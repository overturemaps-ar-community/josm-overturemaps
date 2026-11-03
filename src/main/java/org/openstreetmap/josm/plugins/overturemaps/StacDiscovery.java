package org.openstreetmap.josm.plugins.overturemaps;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kraftek.stac.core.model.Asset;
import com.kraftek.stac.core.model.Catalog;
import com.kraftek.stac.core.model.Item;
import com.kraftek.stac.core.model.Link;
import com.kraftek.stac.core.parser.STACParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** STAC navigation and bounded discovery; network calls must never run on the Swing EDT. */
public final class StacDiscovery {
 public static final URI ROOT = URI.create("https://stac.overturemaps.org/catalog.json");
 private static final int MAX_METADATA_BYTES = 5 * 1024 * 1024;
 private static final int MAX_ATTEMPTS = 3;
 private final STACParser parser = new STACParser();
 private final ObjectMapper mapper = new ObjectMapper();
 private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
 private final DocumentFetcher fetcher;
 private final Map<URI, String> documentCache = new ConcurrentHashMap<>();
 @FunctionalInterface interface DocumentFetcher { String fetch(URI uri) throws Exception; }
 public StacDiscovery() { this.fetcher = this::fetchHttp; }
 StacDiscovery(DocumentFetcher fetcher) { this.fetcher = fetcher; }
 public record AssetMatch(String itemId, String provider, URI url, double[] itemBbox) {}
 public record CatalogEntry(String name, URI url) {
  @Override public String toString() { return name; }
 }
 public enum ProviderPreference {
  AUTO("Auto (prefer AWS)"), AWS("AWS"), AZURE("Azure");
  private final String label;
  ProviderPreference(String label) { this.label = label; }
  @Override public String toString() { return label; }
 }
 public record Result(List<AssetMatch> matches, int documentsVisited, int itemsVisited, int matchedItems, int pendingDocuments) {
  public boolean complete() { return pendingDocuments == 0; }
 }
 public record Selection(String release, String theme, String type, double[] bbox, int maxDocuments) {
  public Selection {
   if (bbox == null || bbox.length != 4 || bbox[0] > bbox[2] || bbox[1] > bbox[3] || bbox[0] < -180 || bbox[2] > 180 || bbox[1] < -90 || bbox[3] > 90) throw new IllegalArgumentException("Invalid WGS84 bbox");
   if (maxDocuments < 1) throw new IllegalArgumentException("maxDocuments must be positive");
  }
 }
 private static URI validateMetadataUri(URI uri) throws IOException {
  if (uri == null || !uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
   throw new IOException("STAC metadata URL must be absolute HTTPS: " + uri);
  return uri.normalize();
 }
 private String fetchHttp(URI input) throws IOException, InterruptedException {
  URI url = validateMetadataUri(input);
  IOException lastFailure = null;
  for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
   HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(40))
     .header("Accept", "application/json").GET().build();
   HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
   int status = response.statusCode();
   try (InputStream body = response.body()) {
    if (status == 200) {
     byte[] bytes = body.readNBytes(MAX_METADATA_BYTES + 1);
     if (bytes.length > MAX_METADATA_BYTES) throw new IOException("STAC document exceeds " + MAX_METADATA_BYTES + " bytes at " + url);
     return new String(bytes, StandardCharsets.UTF_8);
    }
   }
   lastFailure = new IOException("HTTP " + status + " at " + url);
   if (status != 429 && status < 500) break;
  }
  throw lastFailure;
 }
 private String document(URI input) throws Exception {
  URI url = validateMetadataUri(input);
  String cached = documentCache.get(url);
  if (cached != null) return cached;
  String value = fetcher.fetch(url);
  documentCache.put(url, value);
  return value;
 }
 private static Link findChild(List<Link> links, String name) {
  return links.stream().filter(l -> "child".equals(l.getRel()) && (name.equals(l.getTitle()) || URI.create(l.getHref()).getPath().contains("/" + name + "/"))).findFirst()
   .orElseThrow(() -> new IllegalArgumentException("Missing STAC child: " + name));
 }
 private Catalog catalog(URI url) throws Exception { return parser.parseCatalogResponse(document(url)); }
 public List<CatalogEntry> children(URI url) throws Exception {
  Catalog source = catalog(url);
  List<CatalogEntry> result = new ArrayList<>();
  for (Link link : source.getLinks()) if ("child".equals(link.getRel())) {
   URI child = validateMetadataUri(url.resolve(link.getHref()));
   String title = link.getTitle();
   if (title == null || title.isBlank()) {
    String path = child.getPath();
    int end = path.endsWith("/") ? path.length() - 1 : path.length();
    int slash = path.lastIndexOf('/', end - 1);
    title = path.substring(slash + 1, end);
   }
   result.add(new CatalogEntry(title, child));
  }
  return List.copyOf(result);
 }
 private static boolean intersects(double[] a, double[] b) { return a != null && a.length >= 4 && a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1]; }
 /** Remove nested unknown extension values only in the copy passed to kraftek's ItemDeserializer. */
 private JsonNode compatibleItem(JsonNode original) {
  ObjectNode copy = ((ObjectNode)original).deepCopy();
  if (copy.path("properties").isObject()) {
   ObjectNode props = (ObjectNode) copy.get("properties");
   List<String> keys = new ArrayList<>(); props.fieldNames().forEachRemaining(keys::add);
   for (String key : keys) if (props.get(key).isContainerNode()) props.remove(key);
  }
  if (copy.path("assets").isObject()) {
   ObjectNode assets = (ObjectNode)copy.get("assets");
   assets.fields().forEachRemaining(entry -> {
    if (entry.getValue().isObject()) {
     ObjectNode asset = (ObjectNode)entry.getValue();
     List<String> keys = new ArrayList<>(); asset.fieldNames().forEachRemaining(keys::add);
     for (String key : keys) if (!Set.of("href", "title", "description", "type", "roles").contains(key) && asset.get(key).isContainerNode()) asset.remove(key);
    }
   });
  }
  return copy;
 }
 public Result discover(Selection selection) throws Exception {
  URI release = ROOT.resolve(findChild(catalog(ROOT).getLinks(), selection.release()).getHref());
  URI theme = release.resolve(findChild(catalog(release).getLinks(), selection.theme()).getHref());
  URI type = theme.resolve(findChild(catalog(theme).getLinks(), selection.type()).getHref());
  Deque<URI> pending = new ArrayDeque<>(); pending.add(type);
  Set<URI> visited = new HashSet<>(); List<AssetMatch> matches = new ArrayList<>();
  int docs = 0, items = 0, matched = 0;
  while (!pending.isEmpty() && docs < selection.maxDocuments()) {
   if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Discovery cancelled");
   URI url = pending.removeFirst(); if (!visited.add(url)) continue;
   docs++;
   JsonNode json = mapper.readTree(document(url)); String kind = json.path("type").asText();
   if ("Feature".equals(kind)) {
    Item item = parser.parseItemResponse(compatibleItem(json).toString()); items++;
    if (intersects(item.getBbox(), selection.bbox())) { matched++; addAssets(matches, item, url); }
   } else if ("Collection".equals(kind)) {
    var collection = parser.parseCollectionResponse(json.toString());
    enqueue(pending, collection.getLinks(), url);
   } else if ("Catalog".equals(kind)) {
    enqueue(pending, parser.parseCatalogResponse(json.toString()).getLinks(), url);
   } else if ("FeatureCollection".equals(kind)) {
    var features = json.path("features");
    if (features.isArray()) for (JsonNode feature : features) {
     Item item = parser.parseItemResponse(compatibleItem(feature).toString()); items++;
     if (intersects(item.getBbox(), selection.bbox())) { matched++; addAssets(matches, item, url); }
    }
    // Use the original collection links for pagination; item metadata compatibility does not affect them.
    var links = json.path("links");
    if (links.isArray()) for (JsonNode link : links) if ("next".equals(link.path("rel").asText())) pending.add(url.resolve(link.path("href").asText()));
   } else throw new IOException("Unsupported STAC type '" + kind + "' at " + url);
  }
  int pendingUnvisited = (int) pending.stream().filter(uri -> !visited.contains(uri)).distinct().count();
  return new Result(List.copyOf(matches), docs, items, matched, pendingUnvisited);
 }
 private static void enqueue(Deque<URI> queue, List<Link> links, URI base) {
  if (links == null) return;
  for (Link link : links) if ("child".equals(link.getRel()) || "item".equals(link.getRel()) || "next".equals(link.getRel())) queue.add(base.resolve(link.getHref()));
 }
 private static void addAssets(List<AssetMatch> output, Item item, URI base) {
  if (item.getAssets() == null) return;
  for (Map.Entry<String, Asset> e : item.getAssets().entrySet()) {
   Asset asset = e.getValue(); String href = asset.getHref();
   if (href != null && (href.toLowerCase(Locale.ROOT).contains(".parquet") || String.valueOf(asset.getType()).toLowerCase(Locale.ROOT).contains("parquet"))) {
    URI url = base.resolve(href).normalize();
    if ("https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null)
     output.add(new AssetMatch(item.getId(), e.getKey(), url, item.getBbox()));
   }
  }
 }
 public static List<AssetMatch> preferredAssets(List<AssetMatch> matches, ProviderPreference preference) {
  Map<String, List<AssetMatch>> byItem = new LinkedHashMap<>();
  for (AssetMatch match : matches) byItem.computeIfAbsent(match.itemId(), ignored -> new ArrayList<>()).add(match);
  List<AssetMatch> selected = new ArrayList<>();
  for (List<AssetMatch> assets : byItem.values()) {
   String desired = preference == ProviderPreference.AZURE ? "azure" : "aws";
   AssetMatch choice = assets.stream().filter(asset -> desired.equalsIgnoreCase(asset.provider())).findFirst()
     .orElseGet(() -> assets.get(0));
   selected.add(choice);
  }
  return List.copyOf(selected);
 }
}

package org.openstreetmap.josm.plugins.overturemaps;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kraftek.stac.core.STACClient;
import com.kraftek.stac.core.model.Asset;
import com.kraftek.stac.core.model.Catalog;
import com.kraftek.stac.core.model.Item;
import com.kraftek.stac.core.model.Link;
import com.kraftek.stac.core.parser.STACParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** STAC navigation and bounded discovery; network calls must never run on the Swing EDT. */
public final class StacDiscovery {
 public static final URI ROOT = URI.create("https://stac.overturemaps.org/catalog.json");
 private final STACParser parser = new STACParser();
 private final ObjectMapper mapper = new ObjectMapper();
 private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
 public record AssetMatch(String itemId, String provider, URI url, double[] itemBbox) {}
 public record Result(List<AssetMatch> matches, int documentsVisited, int itemsVisited, int matchedItems, int pendingDocuments) {
  public boolean complete() { return pendingDocuments == 0; }
 }
 public record Selection(String release, String theme, String type, double[] bbox, int maxDocuments) {
  public Selection {
   if (bbox == null || bbox.length != 4 || bbox[0] > bbox[2] || bbox[1] > bbox[3] || bbox[0] < -180 || bbox[2] > 180 || bbox[1] < -90 || bbox[3] > 90) throw new IllegalArgumentException("Invalid WGS84 bbox");
   if (maxDocuments < 1) throw new IllegalArgumentException("maxDocuments must be positive");
  }
 }
 private String fetch(URI url) throws IOException, InterruptedException {
  var response = http.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(40)).GET().build(), HttpResponse.BodyHandlers.ofString());
  if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " at " + url);
  return response.body();
 }
 private static Link findChild(List<Link> links, String name) {
  return links.stream().filter(l -> "child".equals(l.getRel()) && (name.equals(l.getTitle()) || URI.create(l.getHref()).getPath().contains("/" + name + "/"))).findFirst()
   .orElseThrow(() -> new IllegalArgumentException("Missing STAC child: " + name));
 }
 private Catalog catalog(URI url) throws Exception { return new STACClient(url.toString(), null).getCatalog(); }
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
   JsonNode json = mapper.readTree(fetch(url)); String kind = json.path("type").asText();
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
  return new Result(List.copyOf(matches), docs, items, matched, pending.size());
 }
 private static void enqueue(Deque<URI> queue, List<Link> links, URI base) {
  if (links == null) return;
  for (Link link : links) if ("child".equals(link.getRel()) || "item".equals(link.getRel()) || "next".equals(link.getRel())) queue.add(base.resolve(link.getHref()));
 }
 private static void addAssets(List<AssetMatch> output, Item item, URI base) {
  if (item.getAssets() == null) return;
  for (Map.Entry<String, Asset> e : item.getAssets().entrySet()) {
   Asset asset = e.getValue(); String href = asset.getHref();
   if (href != null && (href.toLowerCase(Locale.ROOT).contains(".parquet") || String.valueOf(asset.getType()).toLowerCase(Locale.ROOT).contains("parquet")))
    output.add(new AssetMatch(item.getId(), e.getKey(), base.resolve(href), item.getBbox()));
  }
 }
}

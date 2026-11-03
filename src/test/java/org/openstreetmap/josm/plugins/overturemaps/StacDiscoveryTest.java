package org.openstreetmap.josm.plugins.overturemaps;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
class StacDiscoveryTest {
 @Test void rejectsInvalidBbox() { assertThrows(IllegalArgumentException.class, () -> new StacDiscovery.Selection("x", "y", "z", new double[]{10, 40, 20, 30}, 100)); }
 @Test void rejectsInvalidLimit() { assertThrows(IllegalArgumentException.class, () -> new StacDiscovery.Selection("x", "y", "z", new double[]{-59.3,-37.5,-59.0,-37.2}, 0)); }
 @Test void acceptsTandilBbox() { assertNotNull(new StacDiscovery.Selection("2026-10-21.0", "transportation", "segment", new double[]{-59.3,-37.5,-59.0,-37.2}, 150)); }
 @Test void acceptsAndSplitsAntimeridianBbox() {
  assertNotNull(new StacDiscovery.Selection("x", "y", "z", new double[]{170, -20, -170, 20}, 100));
  var parts = StacDiscovery.splitAntimeridian(new double[]{170, -20, -170, 20});
  assertEquals(2, parts.size());
  assertArrayEquals(new double[]{170, -20, 180, 20}, parts.get(0));
  assertArrayEquals(new double[]{-180, -20, -170, 20}, parts.get(1));
 }
 @Test void intersectsAcrossAntimeridian() {
  assertTrue(StacDiscovery.intersects(
    new double[]{175, -10, -175, 10}, new double[]{178, -5, 179, 5}));
  assertTrue(StacDiscovery.intersects(
    new double[]{175, -10, -175, 10}, new double[]{-179, -5, -178, 5}));
  assertFalse(StacDiscovery.intersects(
    new double[]{175, -10, -175, 10}, new double[]{-100, -5, -90, 5}));
 }
 @Test void buildsViewportBboxFromProjectedCorners() {
  assertArrayEquals(new double[]{-59.3, -37.5, -59.0, -37.2},
    DiscoveryDialog.bboxFromCorners(-59.3, -59.0, -37.2, -37.3, -37.5, -37.4));
  assertArrayEquals(new double[]{170, -20, -170, 20},
    DiscoveryDialog.bboxFromCorners(170, -170, 20, 18, -20, -19));
 }
 @Test void insertsDiscoveryActionBeforeExit() {
  JMenu menu = new JMenu("File");
  JMenuItem open = new JMenuItem("Open");
  JMenuItem exit = new JMenuItem("Exit");
  JMenuItem discovery = new JMenuItem("Discover Overture Maps GeoParquet...");
  menu.add(open);
  menu.add(exit);

  OvertureMapsPlugin.addBeforeExit(menu, discovery);

  assertSame(open, menu.getItem(0));
  assertSame(discovery, menu.getItem(1));
  assertSame(exit, menu.getItem(2));
 }

 @Test void discoversFixtureAssetsAndFiltersInsecureUrls() throws Exception {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 10));

  assertTrue(result.complete());
  assertEquals(2, result.documentsVisited());
  assertEquals(1, result.itemsVisited());
  assertEquals(1, result.matchedItems());
  assertEquals(2, result.matches().size());
  assertTrue(result.matches().stream().allMatch(asset -> "https".equals(asset.url().getScheme())));
 }

 @Test void reportsIncompleteFixtureDiscoveryAtDocumentLimit() throws Exception {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 1));

  assertFalse(result.complete());
  assertEquals(1, result.documentsVisited());
  assertEquals(1, result.pendingDocuments());
  assertTrue(result.matches().isEmpty());
 }

 @Test void ignoresVisitedDuplicatesWhenReportingCompleteness() throws Exception {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 2));

  assertTrue(result.complete());
  assertEquals(2, result.documentsVisited());
  assertEquals(1, result.itemsVisited());
 }

 @Test void selectsOnePreferredProviderPerLogicalItem() throws Exception {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 10));

  var automatic = StacDiscovery.preferredAssets(result.matches(), StacDiscovery.ProviderPreference.AUTO);
  var azure = StacDiscovery.preferredAssets(result.matches(), StacDiscovery.ProviderPreference.AZURE);
  assertEquals(1, automatic.size());
  assertEquals("aws", automatic.get(0).provider());
  assertEquals(1, azure.size());
  assertEquals("azure", azure.get(0).provider());
 }

 @Test void cachesCatalogDocumentsWithinSession() throws Exception {
  AtomicInteger fetches = new AtomicInteger();
  StacDiscovery discovery = fixtureDiscovery(fetches);

  assertEquals(1, discovery.children(StacDiscovery.ROOT).size());
  assertEquals(1, discovery.children(StacDiscovery.ROOT).size());
  assertEquals(1, fetches.get());
 }

 @Test void rejectsNonHttpsMetadataUrls() {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  assertThrows(IOException.class, () -> discovery.children(URI.create("http://example.test/catalog.json")));
 }

 @Test void reportsDetailedProgress() throws Exception {
  StacDiscovery discovery = fixtureDiscovery(new AtomicInteger());
  var updates = new ArrayList<StacDiscovery.Progress>();
  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 10), updates::add);

  assertTrue(result.complete());
  assertEquals(2, updates.size());
  assertEquals(2, updates.get(1).documentsVisited());
  assertEquals(1, updates.get(1).itemsVisited());
  assertEquals(1, updates.get(1).matchedItems());
 }

 @Test void followsFeatureCollectionPagination() throws Exception {
  AtomicInteger fetches = new AtomicInteger();
  Map<URI, String> documents = fixtureDocuments();
  String item = resource("item.json");
  URI collection = URI.create("https://example.test/2026-10-21.0/theme=transportation/type=segment/collection.json");
  documents.put(collection, resource("feature-page-1.json").replace("__ITEM__", item));
  documents.put(URI.create("https://example.test/pages/2.json"), resource("feature-page-2.json").replace("__ITEM__", item));
  StacDiscovery discovery = mappedDiscovery(documents, fetches);

  var result = discovery.discover(new StacDiscovery.Selection(
    "2026-10-21.0", "transportation", "segment",
    new double[]{-59.3, -37.5, -59.0, -37.2}, 10));

  assertTrue(result.complete());
  assertEquals(2, result.documentsVisited());
  assertEquals(2, result.itemsVisited());
  assertEquals(2, result.matchedItems());
 }

 @Test void propagatesMetadataFetchFailures() {
  StacDiscovery discovery = new StacDiscovery(uri -> { throw new IOException("fixture network failure"); });
  IOException error = assertThrows(IOException.class, () -> discovery.children(StacDiscovery.ROOT));
  assertEquals("fixture network failure", error.getMessage());
 }

 private static StacDiscovery fixtureDiscovery(AtomicInteger fetches) {
  return mappedDiscovery(fixtureDocuments(), fetches);
 }

 private static Map<URI, String> fixtureDocuments() {
  Map<URI, String> documents = new HashMap<>();
  documents.put(StacDiscovery.ROOT, resource("root.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/catalog.json"), resource("release.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/theme=transportation/catalog.json"), resource("theme.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/theme=transportation/type=segment/collection.json"), resource("collection.json"));
  documents.put(URI.create("https://example.test/items/00008.json"), resource("item.json"));
  return documents;
 }

 private static StacDiscovery mappedDiscovery(Map<URI, String> documents, AtomicInteger fetches) {
  return new StacDiscovery(uri -> {
   fetches.incrementAndGet();
   String value = documents.get(uri);
   if (value == null) throw new IOException("Missing fixture: " + uri);
   return value;
  });
 }

 private static String resource(String name) {
  try (var input = StacDiscoveryTest.class.getResourceAsStream("/stac/" + name)) {
   if (input == null) throw new IOException("Missing resource " + name);
   return new String(input.readAllBytes(), StandardCharsets.UTF_8);
  } catch (IOException e) {
   throw new IllegalStateException(e);
  }
 }
}

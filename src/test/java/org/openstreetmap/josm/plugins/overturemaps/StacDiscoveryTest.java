package org.openstreetmap.josm.plugins.overturemaps;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
class StacDiscoveryTest {
 @Test void rejectsInvalidBbox() { assertThrows(IllegalArgumentException.class, () -> new StacDiscovery.Selection("x", "y", "z", new double[]{10, 20, 0, 30}, 100)); }
 @Test void rejectsInvalidLimit() { assertThrows(IllegalArgumentException.class, () -> new StacDiscovery.Selection("x", "y", "z", new double[]{-59.3,-37.5,-59.0,-37.2}, 0)); }
 @Test void acceptsTandilBbox() { assertNotNull(new StacDiscovery.Selection("2026-10-21.0", "transportation", "segment", new double[]{-59.3,-37.5,-59.0,-37.2}, 150)); }
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

 private static StacDiscovery fixtureDiscovery(AtomicInteger fetches) {
  Map<URI, String> documents = new HashMap<>();
  documents.put(StacDiscovery.ROOT, resource("root.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/catalog.json"), resource("release.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/theme=transportation/catalog.json"), resource("theme.json"));
  documents.put(URI.create("https://example.test/2026-10-21.0/theme=transportation/type=segment/collection.json"), resource("collection.json"));
  documents.put(URI.create("https://example.test/items/00008.json"), resource("item.json"));
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

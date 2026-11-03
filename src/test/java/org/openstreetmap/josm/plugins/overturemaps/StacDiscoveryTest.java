package org.openstreetmap.josm.plugins.overturemaps;
import static org.junit.jupiter.api.Assertions.*;
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
}

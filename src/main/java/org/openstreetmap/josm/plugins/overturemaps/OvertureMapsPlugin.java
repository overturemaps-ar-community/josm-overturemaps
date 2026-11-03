package org.openstreetmap.josm.plugins.overturemaps;
import java.awt.Component;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
/** Minimal entry point; no GeoParquet implementation is duplicated. */
public final class OvertureMapsPlugin extends Plugin {
 public OvertureMapsPlugin(PluginInformation info) {
  super(info);
  JMenuItem item = new JMenuItem("Discover Overture Maps GeoParquet...");
  item.addActionListener(e -> DiscoveryDialog.show());
  addBeforeExit(MainApplication.getMenu().fileMenu, item);
 }

 static void addBeforeExit(JMenu fileMenu, JMenuItem item) {
  Component[] components = fileMenu.getMenuComponents();
  for (int i = 0; i < components.length; i++) {
   if (components[i] instanceof JMenuItem candidate
     && ((candidate.getAction() != null
       && "ExitAction".equals(candidate.getAction().getClass().getSimpleName()))
      || "Exit".equalsIgnoreCase(candidate.getText())
      || "Quit".equalsIgnoreCase(candidate.getText()))) {
    fileMenu.insert(item, i);
    return;
   }
  }
  fileMenu.add(item);
 }
}

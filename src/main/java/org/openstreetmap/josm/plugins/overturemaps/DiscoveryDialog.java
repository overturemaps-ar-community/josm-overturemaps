package org.openstreetmap.josm.plugins.overturemaps;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import org.openstreetmap.josm.gui.MainApplication;
/** Deliberately small mock UI. Asset handoff to GeoParquet is a documented next milestone. */
public final class DiscoveryDialog {
 private DiscoveryDialog() {}
 public static void show() {
  StacDiscovery service = new StacDiscovery();
  JDialog dialog = new JDialog(MainApplication.getMainFrame(), "Overture Maps — STAC discovery", false);
  JComboBox<StacDiscovery.CatalogEntry> release = new JComboBox<>();
  JComboBox<StacDiscovery.CatalogEntry> theme = new JComboBox<>();
  JComboBox<StacDiscovery.CatalogEntry> type = new JComboBox<>();
  JTextField bbox = new JTextField("-59.3,-37.5,-59.0,-37.2");
  JTextField limit = new JTextField("150");
  JPanel form = new JPanel(new GridLayout(5, 2, 6, 6));
  form.add(new JLabel("Release:")); form.add(release);
  form.add(new JLabel("Theme:")); form.add(theme);
  form.add(new JLabel("Type:")); form.add(type);
  form.add(new JLabel("WGS84 BBOX (minLon,minLat,maxLon,maxLat):")); form.add(bbox);
  form.add(new JLabel("Max STAC documents:")); form.add(limit);
  JTextArea output = new JTextArea(14, 90); output.setEditable(false);
  JButton discover = new JButton("Discover GeoParquet assets");
  discover.setEnabled(false);
  JButton cancel = new JButton("Cancel"); cancel.setEnabled(false);
  JButton copy = new JButton("Copy results"); copy.setEnabled(false);
  JProgressBar progress = new JProgressBar();
  progress.setIndeterminate(false);
  progress.setVisible(false);
  AtomicReference<SwingWorker<?, ?>> activeDiscovery = new AtomicReference<>();
  AtomicLong releaseLoad = new AtomicLong();
  AtomicLong themeLoad = new AtomicLong();
  AtomicLong typeLoad = new AtomicLong();
  release.addItemListener(e -> {
   if (e.getStateChange() == ItemEvent.SELECTED) {
    discover.setEnabled(false);
    var selected = (StacDiscovery.CatalogEntry) e.getItem();
    loadChildren(service, selected.url(), theme, themeLoad, output, "themes", false);
   }
  });
  theme.addItemListener(e -> {
   if (e.getStateChange() == ItemEvent.SELECTED) {
    discover.setEnabled(false);
    var selected = (StacDiscovery.CatalogEntry) e.getItem();
    loadChildren(service, selected.url(), type, typeLoad, output, "types", false);
   }
  });
  type.addItemListener(e -> {
   if (e.getStateChange() == ItemEvent.SELECTED) discover.setEnabled(true);
  });
  copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(output.getText()), null));
  cancel.addActionListener(e -> {
   SwingWorker<?, ?> worker = activeDiscovery.get();
   if (worker != null) worker.cancel(true);
  });
  discover.addActionListener(e -> {
   try {
    double[] bounds = java.util.Arrays.stream(bbox.getText().trim().split(",")).mapToDouble(Double::parseDouble).toArray();
    var selectedRelease = (StacDiscovery.CatalogEntry) release.getSelectedItem();
    var selectedTheme = (StacDiscovery.CatalogEntry) theme.getSelectedItem();
    var selectedType = (StacDiscovery.CatalogEntry) type.getSelectedItem();
    if (selectedRelease == null || selectedTheme == null || selectedType == null) throw new IllegalArgumentException("Wait for release, theme and type catalogs to load");
    var selection = new StacDiscovery.Selection(selectedRelease.name(), selectedTheme.name(), selectedType.name(), bounds, Integer.parseInt(limit.getText().trim()));
    discover.setEnabled(false); cancel.setEnabled(true); copy.setEnabled(false);
    progress.setVisible(true); progress.setIndeterminate(true);
    output.setText("Discovering... (network operation)\n");
    SwingWorker<StacDiscovery.Result, Void> worker = new SwingWorker<>() {
     @Override protected StacDiscovery.Result doInBackground() throws Exception { return service.discover(selection); }
     @Override protected void done() {
      discover.setEnabled(true); cancel.setEnabled(false);
      progress.setIndeterminate(false); progress.setVisible(false);
      activeDiscovery.compareAndSet(this, null);
      if (isCancelled()) { output.setText("Discovery cancelled.\n"); return; }
      try {
       var result = get();
       StringBuilder text = new StringBuilder("Documents=" + result.documentsVisited() + " Items=" + result.itemsVisited() + " Matched=" + result.matchedItems() + " Pending=" + result.pendingDocuments() + "\n");
       if (!result.complete()) text.append("INCOMPLETE: document limit reached.\n");
       for (var asset : result.matches()) text.append(asset.itemId()).append(" [").append(asset.provider()).append("] ").append(asset.url()).append('\n');
       text.append("\nGeoParquet plugin handoff: NOT YET IMPLEMENTED. Copy the HTTPS URL and open it via File > Open remote GeoParquet URL...\n");
       output.setText(text.toString()); copy.setEnabled(true);
      } catch (Exception ex) { output.setText("Discovery failed: " + ex); }
     }
    };
    activeDiscovery.set(worker);
    worker.execute();
   } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Invalid selection", JOptionPane.ERROR_MESSAGE); }
  });
  JPanel buttons = new JPanel(); buttons.add(discover); buttons.add(cancel); buttons.add(copy); buttons.add(progress);
  dialog.add(form, BorderLayout.NORTH); dialog.add(new JScrollPane(output), BorderLayout.CENTER); dialog.add(buttons, BorderLayout.SOUTH);
  dialog.pack(); dialog.setLocationRelativeTo(MainApplication.getMainFrame()); dialog.setVisible(true);
  loadChildren(service, StacDiscovery.ROOT, release, releaseLoad, output, "releases", true);
 }

 private static void loadChildren(StacDiscovery service, URI parent,
   JComboBox<StacDiscovery.CatalogEntry> target, AtomicLong generation,
   JTextArea output, String label, boolean selectLatest) {
  long request = generation.incrementAndGet();
  target.setEnabled(false);
  target.removeAllItems();
  output.setText("Loading " + label + "...\n");
  new SwingWorker<java.util.List<StacDiscovery.CatalogEntry>, Void>() {
   @Override protected java.util.List<StacDiscovery.CatalogEntry> doInBackground() throws Exception {
    return service.children(parent);
   }
   @Override protected void done() {
    if (request != generation.get()) return;
    try {
     var entries = get();
     for (var entry : entries) target.addItem(entry);
     target.setEnabled(!entries.isEmpty());
     if (entries.isEmpty()) output.setText("No " + label + " found in " + parent + "\n");
     else {
      output.setText("");
      if (selectLatest) {
       var latest = entries.stream().max(java.util.Comparator.comparing(StacDiscovery.CatalogEntry::name)).orElseThrow();
       target.setSelectedItem(latest);
      } else target.setSelectedIndex(0);
     }
    } catch (Exception ex) {
     output.setText("Could not load " + label + ": " + ex.getMessage() + "\n");
    }
   }
  }.execute();
 }
}

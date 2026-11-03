package org.openstreetmap.josm.plugins.overturemaps;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.Logging;
/** Viewport/manual STAC discovery UI with read-only GeoParquet layer handoff. */
public final class DiscoveryDialog {
 private enum BboxSource {
  CURRENT_VIEWPORT("Current JOSM viewport"), MANUAL("Manual WGS84 BBOX");
  private final String label;
  BboxSource(String label) { this.label = label; }
  @Override public String toString() { return label; }
 }
 private DiscoveryDialog() {}
 public static void show() {
  StacDiscovery service = new StacDiscovery();
  JDialog dialog = new JDialog(MainApplication.getMainFrame(), "Overture Maps — STAC discovery", false);
  JComboBox<StacDiscovery.CatalogEntry> release = new JComboBox<>();
  JComboBox<StacDiscovery.CatalogEntry> theme = new JComboBox<>();
  JComboBox<StacDiscovery.CatalogEntry> type = new JComboBox<>();
  JComboBox<StacDiscovery.ProviderPreference> provider = new JComboBox<>(StacDiscovery.ProviderPreference.values());
  JComboBox<BboxSource> bboxSource = new JComboBox<>(BboxSource.values());
  JTextField bbox = new JTextField("-59.3,-37.5,-59.0,-37.2");
  JTextField limit = new JTextField("150");
  JPanel form = new JPanel(new GridLayout(7, 2, 6, 6));
  form.add(new JLabel("Release:")); form.add(release);
  form.add(new JLabel("Theme:")); form.add(theme);
  form.add(new JLabel("Type:")); form.add(type);
  form.add(new JLabel("Preferred provider:")); form.add(provider);
  form.add(new JLabel("BBOX source:")); form.add(bboxSource);
  form.add(new JLabel("WGS84 BBOX (minLon,minLat,maxLon,maxLat):")); form.add(bbox);
  form.add(new JLabel("Max STAC documents:")); form.add(limit);
  JTextArea output = new JTextArea(14, 90); output.setEditable(false);
  AssetTableModel results = new AssetTableModel();
  JTable resultTable = new JTable(results);
  resultTable.setAutoCreateRowSorter(true);
  resultTable.setFillsViewportHeight(true);
  boolean mapAvailable = MainApplication.getMap() != null && MainApplication.getMap().mapView != null;
  bboxSource.setSelectedItem(mapAvailable ? BboxSource.CURRENT_VIEWPORT : BboxSource.MANUAL);
  bbox.setEditable(!mapAvailable);
  if (mapAvailable) bbox.setText(formatBbox(viewportBbox(MainApplication.getMap().mapView)));
  JButton discover = new JButton("Discover GeoParquet assets");
  discover.setEnabled(false);
  JButton cancel = new JButton("Cancel"); cancel.setEnabled(false);
  JButton copy = new JButton("Copy results"); copy.setEnabled(false);
  JButton load = new JButton("Load selected as reference layer"); load.setEnabled(false);
  boolean bridgeAvailable = GeoParquetBridge.available();
  Logging.info("Overture Maps: GeoParquet import service available: {0}", bridgeAvailable);
  load.setToolTipText(bridgeAvailable ? "Load selected rows through the GeoParquet plugin"
    : "GeoParquet import service unavailable; use Copy results");
  JButton cancelLoad = new JButton("Cancel loads"); cancelLoad.setEnabled(false);
  JProgressBar loadProgress = new JProgressBar();
  loadProgress.setStringPainted(true); loadProgress.setVisible(false);
  JProgressBar progress = new JProgressBar();
  progress.setIndeterminate(false);
  progress.setVisible(false);
  AtomicReference<SwingWorker<?, ?>> activeDiscovery = new AtomicReference<>();
  AtomicReference<double[]> discoveredBbox = new AtomicReference<>();
  AtomicBoolean loadCancelled = new AtomicBoolean();
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
  bboxSource.addItemListener(e -> {
   if (e.getStateChange() != ItemEvent.SELECTED) return;
   boolean manual = e.getItem() == BboxSource.MANUAL;
   bbox.setEditable(manual);
   if (!manual) {
    var map = MainApplication.getMap();
    if (map == null || map.mapView == null) {
     output.setText("Open a JOSM map before using the current viewport.\n");
     bboxSource.setSelectedItem(BboxSource.MANUAL);
    } else bbox.setText(formatBbox(viewportBbox(map.mapView)));
   }
  });
  copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(output.getText()), null));
  load.addActionListener(e -> {
   if (!bridgeAvailable) {
    JOptionPane.showMessageDialog(dialog, "GeoParquet plugin service is unavailable. Copy the URL and open it manually.",
      "GeoParquet plugin unavailable", JOptionPane.ERROR_MESSAGE); return;
   }
   double[] loadBbox = discoveredBbox.get();
   if (loadBbox == null) return;
   var rows = results.selectedOrAll(java.util.Arrays.stream(resultTable.getSelectedRows())
     .map(resultTable::convertRowIndexToModel).toArray());
   loadCancelled.set(false); load.setEnabled(false); cancelLoad.setEnabled(true);
   loadProgress.setMinimum(0); loadProgress.setMaximum(rows.size()); loadProgress.setValue(0);
   loadProgress.setString("Loading 0/" + rows.size() + " files..."); loadProgress.setVisible(true);
   java.util.concurrent.atomic.AtomicInteger pendingLoads = new java.util.concurrent.atomic.AtomicInteger(rows.size());
   java.util.concurrent.atomic.AtomicInteger completedLoads = new java.util.concurrent.atomic.AtomicInteger();
   for (int row : rows) {
    var asset = results.assetAt(row);
    results.status(row, "Loading...");
    try {
     String layerName = "Overture " + asset.itemId() + " (" + asset.provider() + ")";
     GeoParquetBridge.load(asset.url(), layerName, loadBbox, update -> javax.swing.SwingUtilities.invokeLater(() -> {
        results.status(row, "Loading... " + update.geometriesRead() + " geometries");
        loadProgress.setString("Loading " + completedLoads.get() + "/" + rows.size()
          + " files · " + update.geometriesRead() + " geometries in current file");
       }), loadCancelled::get)
       .whenComplete((loaded, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
        if (failure == null) results.status(row, "Loaded read-only");
        else results.status(row, loadCancelled.get() ? "Cancelled" : "Failed: " + rootMessage(failure));
        int complete = completedLoads.incrementAndGet();
        loadProgress.setValue(complete);
        loadProgress.setString(complete + "/" + rows.size() + " files completed");
        if (pendingLoads.decrementAndGet() == 0) {
         cancelLoad.setEnabled(false); load.setEnabled(bridgeAvailable && !results.isEmpty());
         loadProgress.setVisible(false);
        }
       }));
    } catch (Exception failure) {
     results.status(row, "Failed: " + rootMessage(failure));
     int complete = completedLoads.incrementAndGet(); loadProgress.setValue(complete);
     if (pendingLoads.decrementAndGet() == 0) {
      cancelLoad.setEnabled(false); load.setEnabled(true); loadProgress.setVisible(false);
     }
    }
   }
  });
  cancelLoad.addActionListener(e -> { loadCancelled.set(true); cancelLoad.setEnabled(false); });
  cancel.addActionListener(e -> {
   SwingWorker<?, ?> worker = activeDiscovery.get();
   if (worker != null) worker.cancel(true);
  });
  discover.addActionListener(e -> {
   try {
    double[] bounds;
    if (bboxSource.getSelectedItem() == BboxSource.CURRENT_VIEWPORT) {
     var map = MainApplication.getMap();
     if (map == null || map.mapView == null) throw new IllegalArgumentException("Open a JOSM map before using the current viewport");
     bounds = viewportBbox(map.mapView);
     bbox.setText(formatBbox(bounds));
    } else bounds = java.util.Arrays.stream(bbox.getText().trim().split(",")).mapToDouble(Double::parseDouble).toArray();
    var selectedRelease = (StacDiscovery.CatalogEntry) release.getSelectedItem();
    var selectedTheme = (StacDiscovery.CatalogEntry) theme.getSelectedItem();
    var selectedType = (StacDiscovery.CatalogEntry) type.getSelectedItem();
    if (selectedRelease == null || selectedTheme == null || selectedType == null) throw new IllegalArgumentException("Wait for release, theme and type catalogs to load");
    var selection = new StacDiscovery.Selection(selectedRelease.name(), selectedTheme.name(), selectedType.name(), bounds, Integer.parseInt(limit.getText().trim()));
    discover.setEnabled(false); cancel.setEnabled(true); copy.setEnabled(false);
    load.setEnabled(false); results.setAssets(java.util.List.of()); discoveredBbox.set(bounds.clone());
    progress.setMinimum(0); progress.setMaximum(selection.maxDocuments()); progress.setValue(0);
    progress.setStringPainted(true); progress.setString("Starting discovery...");
    progress.setVisible(true); progress.setIndeterminate(false);
    output.setText("Discovering... (network operation)\n");
    SwingWorker<StacDiscovery.Result, StacDiscovery.Progress> worker = new SwingWorker<>() {
     @Override protected StacDiscovery.Result doInBackground() throws Exception { return service.discover(selection, this::publish); }
     @Override protected void process(java.util.List<StacDiscovery.Progress> updates) {
      if (updates.isEmpty()) return;
      var latest = updates.get(updates.size() - 1);
      progress.setValue(latest.documentsVisited());
      progress.setString("Docs " + latest.documentsVisited() + "/" + latest.maxDocuments()
        + " · Items " + latest.itemsVisited() + " · Matches " + latest.matchedItems());
     }
     @Override protected void done() {
      discover.setEnabled(true); cancel.setEnabled(false);
      progress.setIndeterminate(false); progress.setVisible(false);
      activeDiscovery.compareAndSet(this, null);
      if (isCancelled()) { output.setText("Discovery cancelled.\n"); return; }
      try {
       var result = get();
       StringBuilder text = new StringBuilder("Documents=" + result.documentsVisited() + " Items=" + result.itemsVisited() + " Matched=" + result.matchedItems() + " Pending=" + result.pendingDocuments() + "\n");
       if (!result.complete()) text.append("INCOMPLETE: document limit reached.\n");
       var preference = (StacDiscovery.ProviderPreference) provider.getSelectedItem();
       var selectedAssets = StacDiscovery.preferredAssets(result.matches(), preference);
       results.setAssets(selectedAssets);
       text.append("SelectedAssets=").append(selectedAssets.size()).append(" ProviderPreference=").append(preference).append('\n');
       for (var asset : selectedAssets) text.append(asset.itemId()).append(" [").append(asset.provider()).append("] ").append(asset.url()).append('\n');
       if (!bridgeAvailable) text.append("\nGeoParquet plugin service unavailable; Copy results remains available.\n");
       output.setText(text.toString()); copy.setEnabled(true); load.setEnabled(bridgeAvailable && !selectedAssets.isEmpty());
      } catch (Exception ex) { output.setText("Discovery failed: " + ex); }
     }
    };
    activeDiscovery.set(worker);
    worker.execute();
   } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Invalid selection", JOptionPane.ERROR_MESSAGE); }
  });
  JPanel buttons = new JPanel(); buttons.add(discover); buttons.add(cancel); buttons.add(load); buttons.add(cancelLoad); buttons.add(copy); buttons.add(progress);
  JPanel footer = new JPanel(new BorderLayout()); footer.add(buttons, BorderLayout.CENTER); footer.add(loadProgress, BorderLayout.SOUTH);
  JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(resultTable), new JScrollPane(output));
  content.setResizeWeight(0.65);
  dialog.add(form, BorderLayout.NORTH); dialog.add(content, BorderLayout.CENTER); dialog.add(footer, BorderLayout.SOUTH);
  dialog.pack(); dialog.setSize(1100, 700); dialog.setLocationRelativeTo(MainApplication.getMainFrame()); dialog.setVisible(true);
  loadChildren(service, StacDiscovery.ROOT, release, releaseLoad, output, "releases", true);
 }

 private static String rootMessage(Throwable failure) {
  Throwable cause = failure;
  while (cause.getCause() != null) cause = cause.getCause();
  return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
 }

 static double[] viewportBbox(MapView view) {
  int width = Math.max(1, view.getWidth());
  int height = Math.max(1, view.getHeight());
  var topLeft = view.getLatLon(0, 0);
  var topRight = view.getLatLon(width, 0);
  var bottomLeft = view.getLatLon(0, height);
  var bottomRight = view.getLatLon(width, height);
  double west = view.getLatLon(0, height / 2).lon();
  double east = view.getLatLon(width, height / 2).lon();
  return bboxFromCorners(west, east, topLeft.lat(), topRight.lat(), bottomLeft.lat(), bottomRight.lat());
 }

 static double[] bboxFromCorners(double west, double east, double... latitudes) {
  if (latitudes == null || latitudes.length == 0) throw new IllegalArgumentException("Viewport latitudes are required");
  double minLat = 90;
  double maxLat = -90;
  for (double latitude : latitudes) {
   minLat = Math.min(minLat, latitude);
   maxLat = Math.max(maxLat, latitude);
  }
  return new double[]{west, Math.max(-90, minLat), east, Math.min(90, maxLat)};
 }

 private static String formatBbox(double[] bounds) {
  return String.format(Locale.ROOT, "%.7f,%.7f,%.7f,%.7f", bounds[0], bounds[1], bounds[2], bounds[3]);
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

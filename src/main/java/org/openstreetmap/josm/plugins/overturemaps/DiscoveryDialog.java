package org.openstreetmap.josm.plugins.overturemaps;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import org.openstreetmap.josm.gui.MainApplication;
/** Deliberately small mock UI. Asset handoff to GeoParquet is a documented next milestone. */
public final class DiscoveryDialog {
 private DiscoveryDialog() {}
 public static void show() {
  JDialog dialog = new JDialog(MainApplication.getMainFrame(), "Overture Maps — STAC discovery", false);
  JComboBox<String> theme = new JComboBox<>(new String[]{"transportation", "buildings", "places", "base", "addresses", "divisions"});
  JTextField release = new JTextField("2026-10-21.0");
  JTextField type = new JTextField("segment");
  JTextField bbox = new JTextField("-59.3,-37.5,-59.0,-37.2");
  JTextField limit = new JTextField("150");
  JPanel form = new JPanel(new GridLayout(5, 2, 6, 6));
  form.add(new JLabel("Release (editable):")); form.add(release);
  form.add(new JLabel("Theme:")); form.add(theme);
  form.add(new JLabel("Type (editable):")); form.add(type);
  form.add(new JLabel("WGS84 BBOX (minLon,minLat,maxLon,maxLat):")); form.add(bbox);
  form.add(new JLabel("Max STAC documents:")); form.add(limit);
  JTextArea output = new JTextArea(14, 90); output.setEditable(false);
  JButton discover = new JButton("Discover GeoParquet assets");
  JButton copy = new JButton("Copy results"); copy.setEnabled(false);
  copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(output.getText()), null));
  discover.addActionListener(e -> {
   try {
    double[] bounds = java.util.Arrays.stream(bbox.getText().trim().split(",")).mapToDouble(Double::parseDouble).toArray();
    var selection = new StacDiscovery.Selection(release.getText().trim(), String.valueOf(theme.getSelectedItem()), type.getText().trim(), bounds, Integer.parseInt(limit.getText().trim()));
    discover.setEnabled(false); copy.setEnabled(false); output.setText("Discovering... (network operation)\n");
    new SwingWorker<StacDiscovery.Result, Void>() {
     @Override protected StacDiscovery.Result doInBackground() throws Exception { return new StacDiscovery().discover(selection); }
     @Override protected void done() {
      discover.setEnabled(true);
      try {
       var result = get();
       StringBuilder text = new StringBuilder("Documents=" + result.documentsVisited() + " Items=" + result.itemsVisited() + " Matched=" + result.matchedItems() + " Pending=" + result.pendingDocuments() + "\n");
       if (!result.complete()) text.append("INCOMPLETE: document limit reached.\n");
       for (var asset : result.matches()) text.append(asset.itemId()).append(" [").append(asset.provider()).append("] ").append(asset.url()).append('\n');
       text.append("\nGeoParquet plugin handoff: NOT YET IMPLEMENTED. Copy the HTTPS URL and open it via File > Open remote GeoParquet URL...\n");
       output.setText(text.toString()); copy.setEnabled(true);
      } catch (Exception ex) { output.setText("Discovery failed: " + ex); }
     }
    }.execute();
   } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Invalid selection", JOptionPane.ERROR_MESSAGE); }
  });
  JPanel buttons = new JPanel(); buttons.add(discover); buttons.add(copy);
  dialog.add(form, BorderLayout.NORTH); dialog.add(new JScrollPane(output), BorderLayout.CENTER); dialog.add(buttons, BorderLayout.SOUTH);
  dialog.pack(); dialog.setLocationRelativeTo(MainApplication.getMainFrame()); dialog.setVisible(true);
 }
}

package org.openstreetmap.josm.plugins.overturemaps;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

final class AssetTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Item", "Provider", "BBOX", "Asset URL", "Status"};
    private final List<Row> rows = new ArrayList<>();
    record Row(StacDiscovery.AssetMatch asset, String status) {}

    void setAssets(List<StacDiscovery.AssetMatch> assets) {
        rows.clear();
        for (var asset : assets) rows.add(new Row(asset, "Ready"));
        fireTableDataChanged();
    }

    List<Integer> selectedOrAll(int[] selected) {
        if (selected != null && selected.length > 0)
            return java.util.Arrays.stream(selected).boxed().toList();
        return java.util.stream.IntStream.range(0, rows.size()).boxed().toList();
    }

    StacDiscovery.AssetMatch assetAt(int row) { return rows.get(row).asset(); }
    void status(int row, String value) {
        rows.set(row, new Row(rows.get(row).asset(), value));
        fireTableCellUpdated(row, 4);
    }
    boolean isEmpty() { return rows.isEmpty(); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Object getValueAt(int row, int column) {
        Row value = rows.get(row);
        return switch (column) {
            case 0 -> value.asset().itemId();
            case 1 -> value.asset().provider();
            case 2 -> java.util.Arrays.toString(value.asset().itemBbox());
            case 3 -> value.asset().url();
            case 4 -> value.status();
            default -> "";
        };
    }
}

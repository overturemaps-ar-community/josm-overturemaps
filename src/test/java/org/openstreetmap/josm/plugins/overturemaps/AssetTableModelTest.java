package org.openstreetmap.josm.plugins.overturemaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetTableModelTest {
    @Test void supportsMultipleFilesAndSelectedOrAllLoading() {
        AssetTableModel model = new AssetTableModel();
        model.setAssets(List.of(
                asset("one", "aws", "https://example.test/one.parquet"),
                asset("two", "azure", "https://example.test/two.parquet")));

        assertEquals(2, model.getRowCount());
        assertEquals(List.of(0, 1), model.selectedOrAll(new int[0]));
        assertEquals(List.of(1), model.selectedOrAll(new int[]{1}));
        model.status(1, "Loaded read-only");
        assertEquals("Loaded read-only", model.getValueAt(1, 4));
    }

    private static StacDiscovery.AssetMatch asset(String id, String provider, String url) {
        return new StacDiscovery.AssetMatch(id, provider, URI.create(url), new double[]{-60, -38, -58, -37});
    }
}

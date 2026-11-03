package org.openstreetmap.josm.plugins.overturemaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class GeoParquetBridgeTest {
    @Test void convertsWgs84BboxToJosmBounds() {
        var bounds = GeoParquetBridge.toBounds(new double[]{-59.3, -37.5, -59.0, -37.2});
        assertEquals(-59.3, bounds.getMinLon());
        assertEquals(-37.5, bounds.getMinLat());
        assertEquals(-59.0, bounds.getMaxLon());
        assertEquals(-37.2, bounds.getMaxLat());
    }

    @Test void rejectsWrappedBboxForSingleLayerLoad() {
        assertThrows(IllegalArgumentException.class,
                () -> GeoParquetBridge.toBounds(new double[]{170, -20, -170, 20}));
    }
}

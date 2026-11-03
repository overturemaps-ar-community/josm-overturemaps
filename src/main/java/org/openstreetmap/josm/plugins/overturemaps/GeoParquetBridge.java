package org.openstreetmap.josm.plugins.overturemaps;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.openstreetmap.josm.data.Bounds;

/** Calls the public GeoParquet plugin service without bundling its implementation. */
final class GeoParquetBridge {
    private static final String SERVICE = "org.openstreetmap.josm.plugins.geoparquet.GeoParquetImportService";
    private GeoParquetBridge() {}
    record Progress(String stage, int geometriesRead) {}

    static boolean available() {
        try { serviceClass(); return true; }
        catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    static CompletableFuture<?> load(URI uri, String layerName, double[] bbox,
            Consumer<Progress> progress, BooleanSupplier cancelled) throws ReflectiveOperationException {
        Method method = serviceClass().getMethod("loadRemote", URI.class, String.class, Bounds.class,
                Consumer.class, BooleanSupplier.class);
        try {
            Consumer<Object> adapter = value -> progress.accept(progressValue(value));
            return (CompletableFuture<?>) method.invoke(null, uri, layerName, toBounds(bbox), adapter, cancelled);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw e;
        }
    }

    private static Progress progressValue(Object value) {
        if (value == null) return new Progress("loading", 0);
        try {
            String stage = String.valueOf(value.getClass().getMethod("stage").invoke(value));
            int count = ((Number) value.getClass().getMethod("geometriesRead").invoke(value)).intValue();
            return new Progress(stage, count);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return new Progress("loading", 0);
        }
    }

    static Bounds toBounds(double[] bbox) {
        if (bbox == null || bbox.length != 4 || bbox[0] > bbox[2])
            throw new IllegalArgumentException("GeoParquet loading requires a non-wrapped WGS84 BBOX");
        return new Bounds(bbox[1], bbox[0], bbox[3], bbox[2]);
    }

    private static Class<?> serviceClass() throws ClassNotFoundException {
        return Class.forName(SERVICE, true, GeoParquetBridge.class.getClassLoader());
    }
}

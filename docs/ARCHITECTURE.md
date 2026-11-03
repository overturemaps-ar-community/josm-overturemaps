# Architecture and plugin boundary

```text
JOSM (Swing / map viewport)
  └── josm-overturemaps
      ├── OvertureMapsPlugin — menu registration
      ├── DiscoveryDialog — initial mock UI / background worker
      └── StacDiscovery — static STAC navigation, bbox filtering, HTTPS asset discovery
          └── com.kraftek:stac-client:1.0.0 + Jackson

  └── josm-geoparquet (separate installed plugin)
      ├── [future] stable public import service
      ├── Parquetry / HTTP Range
      └── read-only JOSM reference layers
```

## Dependencies and distribution

Manifest: `Plugin-Class=...OvertureMapsPlugin`, `Plugin-Requires=geoparquet`. Build uses Java 25 and shades STAC dependencies into `overturemaps.jar`; **do not** shade GeoParquet into this plugin. Plugin dependency resolution and classloader visibility must be tested with JOSM itself.

## STAC discovery

1. Fetch root catalog, select release child.
2. Fetch release catalog, select theme child.
3. Fetch theme catalog, select type Collection child.
4. Traverse Item/child/next links, bounded by maxDocuments, avoid cycles.
5. Parse Item via kraftek STACParser after compatibility-normalizing unknown nested extension fields; use core `bbox` and `assets`.
6. Intersect bbox in EPSG:4326, resolve asset URLs and emit results with completeness status.

The smoke test established an intersecting `transportation/segment` Item `00008`, but only searched 99 Items, not the full collection. The current scaffold follows the same algorithm and is not yet performance-optimized.

## Integration contract (proposed; **not implemented**)

In `josm-geoparquet`, add a stable public class such as:

```java
public final class GeoParquetImportService {
    public static void openRemote(java.net.URI url, String layerName);
}
```

The final API must specify threading, exceptions, progress/cancellation, BBOX, provider selection, and the creation of JOSM layers. The existing `GeoParquetRemoteAction.open()` is an interactive dialog and **not** an appropriate programmatic API. Prefer compile-time API via a small shared interface or a verified JOSM plugin service; confirm plugin classloader constraints. Avoid reflection unless absolutely necessary and documented.

## Security and correctness

Validate remote URL schemes/hosts and redirect behavior; guard against SSRF if user-configurable catalogs are later supported. Bound document counts, timeouts and total metadata bytes. Surface partial results. Do not fetch full Parquet files in this plugin. No OSM uploads.

## Known kraftek compatibility limitation

`ItemDeserializer` tries to parse unknown nested JSON as date text (notably `storage:schemes`, `aws.storage:refs`, `aws.alternate`, `azure.storage:refs`). The bootstrap makes a temporary parse-only copy that drops nested extension values. Future milestone: preserve structured extension fields via a minimal fork/patch or separate sidecar metadata, without duplicating the entire STAC parser.

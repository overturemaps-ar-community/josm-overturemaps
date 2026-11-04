# Architecture and plugin boundary

```text
JOSM (Swing / map viewport)
  └── josm-overturemaps
      ├── OvertureMapsPlugin — menu registration
      ├── DiscoveryDialog — discovery/results/load UI and background workers
      ├── GeoParquetBridge — verified boundary to the required plugin service
      ├── AssetTableModel — sortable multi-file results and load status
      └── StacDiscovery — static STAC navigation, bbox filtering, HTTPS asset discovery
          └── com.kraftek:stac-client:1.0.0 + Jackson

  └── josm-geoparquet (separate installed plugin)
      ├── GeoParquetImportService — stable async import service
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

Live validation established an intersecting `transportation/segment` Item `00008`.
With `maxDocs=150`, traversal completed after 129 documents and 128 Items. The
implementation reports explicitly when a lower document limit produces incomplete
results; catalog traversal remains bounded and is not intended as a bulk indexer.

## Integration contract

`josm-geoparquet` provides:

```java
CompletableFuture<ImportResult> loadRemote(
    URI url, String layerName, Bounds bbox,
    Consumer<Progress> progress, BooleanSupplier cancelled);
```

The service specifies threading, exceptions, progress/cancellation, BBOX and creation
of read-only JOSM layers. Provider selection remains in Overture before handoff.
`GeoParquetRemoteAction.open()` remains only an interactive entry point.

The bridge resolves this service reflectively because the two plugins are distributed
as independent JARs and GeoParquet is deliberately not a build or shaded dependency.
JOSM's `Plugin-Requires: geoparquet` establishes runtime ordering and classloader
visibility. The reflected method signature uses only JDK and JOSM types; absence or
version mismatch is surfaced in the UI and URL copy remains available.

## Security and correctness

Validate remote URL schemes/hosts and redirect behavior; guard against SSRF if user-configurable catalogs are later supported. Bound document counts, timeouts and total metadata bytes. Surface partial results. Do not fetch full Parquet files in this plugin. No OSM uploads.

## Known kraftek compatibility limitation

`ItemDeserializer` tries to parse unknown nested JSON as date text (notably
`storage:schemes`, `aws.storage:refs`, `aws.alternate`, `azure.storage:refs`). The
compatibility layer makes a temporary parse-only copy that drops those nested
extension values while preserving the original response, asset URLs and core STAC
fields. A future parser update can preserve the structured extension fields without
duplicating the entire STAC parser.

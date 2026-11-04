# Acceptance tests and evidence

## Initial live-catalog validation (2026-11-03)

- PASS: root `https://stac.overturemaps.org/catalog.json`, release `2026-10-21.0`, theme `transportation`, type `segment`.
- PASS: STAC Collection `segment`, 131 links.
- PASS: Item `00008` bbox `[-86.0010757446289,-79.81500244140625,-52.76660919189453,-33.010746002197266]` intersects Tandil `[-59.3,-37.5,-59.0,-37.2]`.
- PASS: AWS/Azure HTTPS Parquet URLs found.
- INCOMPLETE: `maxDocs=100`, 99 Items, 1 matching Item, 2 provider assets, 29 pending documents.

## Build and JOSM validation (M0 completed 2026-11-03)

1. PASS: `make bootstrap` fetched JOSM and installed kraftek STAC client from source.
2. PASS: `make test` passed all unit tests on OpenJDK `25.0.4.1` and Maven `3.6.3`.
3. PASS: `make build` created an `8.8 MB` `target/overturemaps.jar`; archive integrity and manifest entries `Plugin-Class`, `Plugin-Requires: geoparquet`, and Java 25 were verified.
4. PASS: `make install` installed the plugin; JOSM tested version `19613` loaded `geoparquet 0.2.0` followed by `overturemaps 0.1.0` in an isolated profile.
5. PASS: the File menu action and discovery UI opened correctly in JOSM. Network
   discovery and clipboard behavior were validated in later milestones.
6. `maxDocs=150` should allow completion for this known release/type if all links remain stable; verify rather than assume.
7. UI explicitly reports incomplete discovery when maxDocs is too small.
8. Historical M0 scope ended before cross-plugin loading; the completed M3/M4
   validation below supersedes that limitation.

### M0 observations

- The menu action initially appeared after Exit; it was changed to insert immediately before Exit and covered by a unit test.
- Maven warns that the project-local JOSM `systemPath` is not suitable for downstream consumers.
- The shaded STAC dependency graph produces an `8.8 MB` plugin and reports duplicate metadata resources across GeoTools, EMF, Jackson and related libraries. This should be reviewed before distribution but did not break M0.
- JOSM logs an unrelated default-imagery parsing error for `tileMatrixSet=grid_25833` in the isolated profile; both plugins still load successfully.

## Additional regression coverage

- Fixture tests for Item unknown nested fields and correct core bbox/assets.
- Network failure, 404, redirects, timeout, invalid bbox, empty catalog, pagination, duplicate items, mirrored assets.
- Antimeridian, no map view, and incompatible/missing GeoParquet plugin behavior.
- Multi-Item matching for wide BBOX and correct filtering by current viewport.
- No write/upload action to OSM.

## M1 progress (2026-11-03)

- PASS: release, theme and type `child` links are loaded dynamically off the EDT and cached for the dialog session.
- PASS: the live catalog returned 3 releases; the latest discovered release was `2026-10-21.0 Overture Release`, with themes `addresses`, `base`, `buildings`, `divisions`, `places`, `transportation`; transportation exposed `connector` and `segment`.
- PASS: fixture discovery reports complete and incomplete traversal correctly, accepts nested extension objects through the compatibility copy, and filters non-HTTPS Parquet assets.
- PASS: metadata URLs require HTTPS; HTTP requests have connect/request timeouts, three bounded attempts and a 5 MiB document limit.
- PASS: the dialog exposes cancellation and counted document/Item/match progress while discovery runs.
- PASS: Auto/AWS/Azure preference returns one provider asset per logical Item; Auto prefers AWS and falls back to the first available mirror.
- PASS: live Tandil discovery with `maxDocs=150` completed after 129 documents and 128 Items, with Item `00008` as the single match, AWS/Azure mirrors and 0 pending documents.
- PASS: FeatureCollection `next` pagination, duplicate-link completeness and injected network failures have deterministic coverage.
- PASS: 16 deterministic tests and the shaded build complete successfully.

## M2 completion (2026-11-03)

- PASS: the dialog defaults to the active JOSM viewport when a map is open and refreshes its WGS84 BBOX at each discovery.
- PASS: manual BBOX remains available and is the fallback when no map view exists.
- PASS: the exact BBOX used is visible in the dialog.
- PASS: wrapped BBOXes such as `170,-20,-170,20` are accepted and split at ±180° for intersection checks.
- PASS: provider preference and logical-Item deduplication were validated with the live AWS/Azure mirrors for Tandil Item `00008`.
- PASS: moving the map and running discovery again captures the new viewport; no hardcoded Tandil BBOX is required in viewport mode.

## M3 and M4 completion (2026-11-03)

- PASS: GeoParquet provides a public HTTPS/BBOX/layer-name/progress/cancellation API.
- PASS: remote reads remain in GeoParquet, run off EDT and register only a read-only
  `GeoParquetReferenceLayer` on EDT.
- PASS: Overture does not embed Parquetry or duplicate HTTP Range, filtering,
  rendering or styling code.
- PASS: sortable results table supports multiple logical Items, provider and URL
  visibility, row selection, per-file status and copy fallback.
- PASS: load errors remain isolated per row and loads can be cancelled.
- PASS: GeoParquet service contract has 3 deterministic tests; Overture has 20.
- PASS: JOSM tested 19613 loaded GeoParquet 0.3.0 before Overture 0.1.0 and
  confirmed the required import service was visible across plugin classloaders.
- PASS: reference loading displays aggregate file progress and the geometry count
  reported by GeoParquet for the currently active file.

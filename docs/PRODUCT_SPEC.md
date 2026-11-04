# Functional specification — JOSM Overture Maps STAC plugin

## 1. Purpose

Enable JOSM users to discover and visually compare Overture Maps reference data with OSM in the current map viewport, using Overture's static STAC catalog and the separate GeoParquet plugin. This is **not** a conflation or upload tool.

## 2. Scope

- Browse releases from `https://stac.overturemaps.org/catalog.json` (STAC `child` links).
- Browse themes and types from each release's catalogs and collections.
- Default to latest available release **only after** dynamically reading releases; do not hardcode a permanent latest release.
- Use current JOSM viewport in WGS84 (`minLon,minLat,maxLon,maxLat`), with manual override and visible BBOX preview.
- Traverse Collection → Item links; intersect Item bbox with viewport bbox; show provider, Item ID, asset URL and discovery completeness.
- Select preferred HTTPS asset/provider (AWS/Azure) and hand off to `josm-geoparquet`.
- Create a distinct read-only reference layer via GeoParquet plugin; no edit, upload or conflation features.
- Offer cancellation, progress, request limits, errors, and refresh.

## 3. Screens / user flow

1. File > Discover Overture Maps GeoParquet...
2. Discovery dialog: release selector; theme selector; type selector; BBOX source (current viewport/manual); max documents; preferred provider.
3. Results table: Item ID, bbox, asset/provider, URI, status; deduplicate mirrored providers; show incomplete results warning.
4. Action: **Load as reference layer** delegates to GeoParquet plugin. **Copy URL** fallback.
5. Layer appears separately from editable OSM data with GeoParquet plugin styling and viewport filtering.

## 4. Acceptance constraints

- Correctly distinguish STAC Catalog / Collection / Feature (Item) / FeatureCollection.
- Do not require a STAC API server; the public Overture catalog is static.
- Do not download complete Parquet objects in the discovery plugin.
- BBOX intersection is a coarse file filter; final feature filtering belongs to GeoParquet reader.
- Multiple files may overlap one viewport. Do not assume first match is exhaustive.
- An Item's AWS/Azure assets may be mirrors of the same physical data; deduplicate at logical Item level, not by URL alone.
- A user should be able to see a complete vs incomplete discovery status.

## 5. Explicit exclusions

No OSM data edits, automatic upload, conflation, routing validation, Overture-to-OSM conversion, credential management, general-purpose STAC server, or new GeoParquet reader.

## 6. Current implementation status

The dialog dynamically loads release/theme/type options from catalog `child` links, defaults to the latest discovered release, and uses the current JOSM viewport with a visible manual override. Cancellation, counted progress, AWS/Azure preference, logical-Item provider deduplication and antimeridian-aware intersection are implemented. The results table loads one or more assets through the public GeoParquet service, with per-file status, cancellation and URL-copy fallback. Loaded content is a separate read-only reference layer; no OSM editing or upload path is introduced.

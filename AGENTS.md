# Instructions for coding agents (Codex)

Read `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION_PLAN.md`, and `docs/ACCEPTANCE_TESTS.md` before changing code.

1. Preserve **two plugins**: `josm-overturemaps` (STAC discovery/UI) and `josm-geoparquet` (GeoParquet reader/layer). Do not duplicate Parquetry, HTTP Range, layer rendering, or GeoParquet parsing.
2. Use `com.kraftek:stac-client:1.0.0` as the STAC model/parser. The Overture catalog is **static**: do not assume STAC API `/search` exists.
3. Preserve release/theme/type selection and WGS84 BBOX semantics. Use current JOSM viewport in a later milestone, converting coordinates to EPSG:4326.
4. Keep all HTTP requests and file processing off Swing EDT; mutate Swing and JOSM layers on EDT.
5. Do not silently claim complete discovery when the max-documents limit was reached.
6. Avoid implicit uploads to OpenStreetMap. Overture layers are read-only reference data, never OSM edits.
7. Treat external STAC URLs as untrusted: validate schemes, cap requests, handle errors, and do not leak credentials.
8. The current GeoParquet plugin does not expose a proven public import API. Define/implement a minimal stable API **in that repository**, then call it from this plugin without duplicating code. Do not invent a working bridge.
9. Do not commit vendored code, downloaded JOSM JARs, or generated artifacts.
10. Every milestone should include tests, documented manual validation and a small reviewable diff.

Initial task: run `make bootstrap && make test && make build`; fix compile/API issues, then open JOSM and validate plugin menu. Do not assume compilation already succeeded.

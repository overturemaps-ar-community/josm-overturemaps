# Implementation roadmap (Codex-ready)

## M0 — Bootstrap and reproducible build

- Run `make bootstrap`, `make test`, `make build`, `make inspect`, `make install`, `make run`.
- Confirm plugin is recognized and File menu action opens the dialog; check `Plugin-Requires` behavior with/without GeoParquet installed.
- Fix any JOSM API/version mismatch and record exact Java/JOSM versions. Keep `josm-geoparquet` untouched.
- Definition of done: reproducible plugin JAR, manifest, and menu dialog in running JOSM.

## M1 — Production-ready static STAC browser

**Status: completed locally on 2026-11-03.** Dynamic catalog models, session caching, bounded HTTPS metadata reads, cancellation, counted progress, pagination/duplicate handling and deterministic fixtures are implemented. The known kraftek nested-extension compatibility normalization remains documented and preserves the original response outside its temporary parse copy.

- Replace hardcoded release/theme/type inputs with dynamic catalog child link models.
- Cache catalog JSON within session, avoid redundant requests; add configurable timeouts, retries, bounded metadata response size.
- Improve Item parser compatibility without silently discarding needed extension fields.
- Support complete discovery, cancellation, progress, and pagination; handle all STAC link types and relative URLs.
- Unit tests with local JSON fixtures; integration tests against live Overture catalog separately opt-in.
- Definition of done: deterministic list of intersecting logical Items, accurate completeness reporting.

## M2 — Viewport-driven discovery

**Status: completed locally on 2026-11-03.** The dialog captures the active JOSM viewport for every discovery, exposes the exact WGS84 BBOX with manual fallback, supports antimeridian-wrapped queries, and provides AWS/Azure preference with logical-Item deduplication.

- Get active JOSM map viewport bounds, transform to EPSG:4326, handle antimeridian cases and map-not-open state.
- Manual BBOX override remains available for debugging.
- Verify that all intersecting files are returned; implement provider selection/deduplication.
- Definition of done: switching map viewport updates discoverable assets without hardcoded Tandil bbox.

## M3 — Cross-plugin integration (requires changes in `josm-geoparquet`)

- Define and implement stable `GeoParquetImportService` public API in GeoParquet repo.
- Specify method parameters: URI, layer name, bbox/viewport, progress, cancellation and read-only behavior.
- Verify JOSM plugin classloader behavior; avoid embedding the GeoParquet library or its code in this project.
- Invoke service from Overture results dialog; keep URL copy fallback.
- Definition of done: one click loads a read-only Overture reference layer, with HTTP Range and viewport feature filtering handled by GeoParquet.

## M4 — UX and hardening

- Results table, status, error recovery, provider preferences, styling integration, caching, tests with multiple files and mirror URLs.
- Documentation and JOSM plugin packaging/distribution validation.
- Definition of done: repeatable real-world comparison workflow, no OSM editing/upload.

## First Codex prompt

> Read AGENTS.md and docs/*.md. Start only with M0. Run the build, correct any compilation or JOSM plugin manifest issues, validate the menu entry, and report exact commands/results. Do not implement GeoParquet reading or claim cross-plugin integration works yet.

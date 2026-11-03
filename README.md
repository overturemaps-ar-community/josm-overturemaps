# josm-overturemaps — kickoff scaffold

A standalone JOSM plugin for discovering Overture Maps GeoParquet files using its
static STAC catalog and loading them as read-only reference layers through the
separate GeoParquet plugin.

## Validated baseline (2026-11-03)

The earlier independent smoke test successfully traversed the Overture release catalog and discovered `transportation/segment` Item `00008`, with AWS and Azure HTTPS `.zstd.parquet` assets intersecting Tandil bbox `-59.3,-37.5,-59.0,-37.2`. The run stopped at `maxDocs=100` with 29 pending documents. That discovery run predated the JOSM scaffold validation described below.

Milestone M0 was subsequently completed with OpenJDK `25.0.4.1`, Maven `3.6.3`, and JOSM tested version `19613`. The unit tests and shaded JAR build passed, the manifest was inspected, and an isolated JOSM profile loaded both `geoparquet 0.2.0` and `overturemaps 0.1.0`. The File menu action and discovery dialog were verified in a virtual display. M1 through M4 are now implemented locally.

## Dependencies

- Java 25 (matches the existing GeoParquet plugin)
- Maven, Git, curl, JOSM tested JAR
- `com.kraftek:stac-client:1.0.0` installed locally from https://github.com/kraftek/stac
- **JOSM GeoParquet plugin installed separately**: `geoparquet.jar` from the user's existing project. Plugin manifest declares `Plugin-Requires: geoparquet`.

## First steps

```bash
make bootstrap
make test
make build
make install
make run
```

The File menu contains **Discover Overture Maps GeoParquet...**. The UI dynamically loads releases, themes and types from the static STAC catalog, defaults to the newest catalog release, and uses the current JOSM viewport in WGS84 by default with a visible manual override. It also supports a document limit, counted progress, cancellation, AWS/Azure preference and a sortable results table. Mirrored provider assets are collapsed to one selected URL per logical Item. Wrapped antimeridian BBOXes are split for correct intersection. Use `transportation` / `segment` for the validated Tandil case.

Select one or more result rows and choose **Load selected as reference layer**. Each
file has its own load status and cancellation/error handling. The required GeoParquet
plugin owns HTTP Range access, viewport filtering, rendering and styling; URL copy
remains available as a fallback.

## Docs

- [Product and functional specification](docs/PRODUCT_SPEC.md)
- [Architecture and integration contract](docs/ARCHITECTURE.md)
- [Implementation plan for Codex](docs/IMPLEMENTATION_PLAN.md)
- [Acceptance tests](docs/ACCEPTANCE_TESTS.md)
- [Agent instructions](AGENTS.md)

## Releases

Pushing a semantic-version tag such as `v0.1.0` runs GitHub Actions with Java 25,
tests and packages the plugin, verifies that the manifest version matches the tag,
and creates a GitHub Release containing `overturemaps-0.1.0.jar` plus its SHA-256 file.

## Notes

The original smoke test uses a compatibility normalization before kraftek Item parsing: unsupported nested STAC extension objects are stripped **only from a temporary parse copy**. This is a temporary limitation, tracked for a proper preservation strategy. Asset URLs and core STAC fields are preserved. Metadata requests require HTTPS, use bounded retries, reject documents larger than 5 MiB and are cached for the dialog session.

`make run` uses the same JVM flags as the validated `josm-geoparquet` starter. Installation paths can vary by JOSM packaging and operating system; override `JOSM_PLUGINS_DIR` if needed.

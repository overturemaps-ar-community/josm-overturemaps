# JOSM Overture Maps

[Versión en español](README.es.md)

A standalone JOSM plugin for discovering Overture Maps GeoParquet files using its
static STAC catalog and loading them as read-only reference layers through the
separate GeoParquet plugin.

## Validated release

The plugin has been validated against the live Overture release catalog using
`transportation/segment` data intersecting Tandil bbox
`-59.3,-37.5,-59.0,-37.2`. Complete discovery with a sufficient document limit,
AWS/Azure mirror selection and GeoParquet reference-layer loading were exercised.

Version `0.1.0` was built and exercised with OpenJDK `25.0.4.1` and JOSM tested
`19613`. An isolated JOSM profile loaded `geoparquet 0.3.0` and
`overturemaps 0.1.0`; catalog discovery, the results UI and cross-plugin loading are
implemented and covered by deterministic tests.

## Dependencies

- Java 25 (matches the existing GeoParquet plugin)
- Maven, Git, curl, JOSM tested JAR
- `com.kraftek:stac-client:1.0.0` installed locally from https://github.com/kraftek/stac
- **JOSM GeoParquet plugin installed separately**: a compatible `geoparquet.jar`.
  The plugin manifest declares `Plugin-Requires: geoparquet`.

## Install published releases

Both plugins are available without authentication from their public release pages:

1. Download and install
   [GeoParquet](https://github.com/geoverse-dev/josm-geoparquet/releases) as
   `geoparquet.jar`.
2. Download
   [Overture Maps](https://github.com/overturemaps-ar-community/josm-overturemaps/releases)
   as `overturemaps.jar` into the same JOSM plugins directory.
3. Enable both plugins and start JOSM with Java 25 and preview features enabled.

Install GeoParquet first because Overture Maps declares it as a required plugin.
For the recommended self-contained setup, run the installer included in this
repository. It downloads JOSM and both plugins, verifies their release checksums,
and uses an isolated profile:

```bash
JAVA_HOME=/path/to/jdk-25 ./scripts/install-and-run.sh
```

See [Installation](docs/INSTALLATION.md) or
[Instalación en español](docs/INSTALLATION.es.md).

## Build and run

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
- [Installation guide](docs/INSTALLATION.md)
- [Architecture and integration contract](docs/ARCHITECTURE.md)
- [Implementation roadmap and completed milestones](docs/IMPLEMENTATION_PLAN.md)
- [Acceptance tests](docs/ACCEPTANCE_TESTS.md)

## Releases

Pushing a semantic-version tag such as `v0.1.0` runs GitHub Actions with Java 25,
tests and packages the plugin, verifies that the manifest version matches the tag,
and creates a GitHub Release containing `overturemaps-0.1.0.jar` plus its SHA-256 file.

## Notes

Compatibility normalization before kraftek Item parsing strips unsupported nested
STAC extension objects **only from a temporary parse copy**. Asset URLs, core STAC
fields and the original response are preserved. Metadata requests require HTTPS,
use bounded retries, reject documents larger than 5 MiB and are cached for the
dialog session.

`make run` uses the same JVM flags as the validated `josm-geoparquet` starter. Installation paths can vary by JOSM packaging and operating system; override `JOSM_PLUGINS_DIR` if needed.

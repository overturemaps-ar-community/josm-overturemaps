# Installing JOSM Overture Maps

The repository includes a self-contained installer for end users. It downloads
JOSM tested and the public GeoParquet and Overture Maps releases, verifies both
checksums, and starts JOSM with an isolated profile. It does not require a GitHub
account, Git, Maven, or local compilation.

## Requirements

- Linux or macOS with Bash.
- JDK 25.
- `curl`, `unzip`, and `sha256sum`.
- Internet access for the first run.

## Install and run

Clone or download this repository and run from its root:

```bash
JAVA_HOME=/path/to/jdk-25 ./scripts/install-and-run.sh
```

The script creates `./josm-overture-bundle/` containing JOSM,
`geoparquet.jar`, `overturemaps.jar`, preferences, user data, and cache. Later
runs reuse the downloads. GeoParquet is installed automatically because it is a
required dependency. The profile is isolated from any regular JOSM installation.

## Options

```bash
# Install and verify without opening JOSM
./scripts/install-and-run.sh --prepare-only

# Choose another directory
./scripts/install-and-run.sh --dir /path/to/my-josm

# Download JOSM and both plugins again
./scripts/install-and-run.sh --refresh
```

Select other releases with:

```bash
GEOPARQUET_VERSION=0.3.0 OVERTUREMAPS_VERSION=0.1.0 \
  ./scripts/install-and-run.sh
```

Run `./scripts/install-and-run.sh --help` for every option.

## Verify in JOSM

1. Confirm that GeoParquet and Overture Maps are enabled under
   **Preferences → Plugins**.
2. Open **File → Discover Overture Maps GeoParquet...**.
3. Select a release, theme, and type; use the current viewport or a manual BBOX.
4. Select results and choose **Load selected as reference layer**.
5. Confirm that loaded layers are read-only references.

## Manual installation

Download the versioned JAR first from the
[GeoParquet releases](https://github.com/geoverse-dev/josm-geoparquet/releases),
rename it to `geoparquet.jar`, then download the versioned JAR from the
[Overture Maps releases](https://github.com/overturemaps-ar-community/josm-overturemaps/releases).
Rename it to `overturemaps.jar`, copy both into the JOSM plugins directory, and
enable them. JOSM must run with Java 25, `--enable-preview`, and the JVM exports
used by the script.

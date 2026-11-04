# Instalación de JOSM Overture Maps

El repositorio incluye un instalador autocontenido para usuarios finales. Descarga
JOSM tested y las releases públicas de GeoParquet y Overture Maps, valida ambos
checksums y ejecuta JOSM con un perfil aislado. No requiere una cuenta de GitHub,
Git, Maven ni compilar.

## Requisitos

- Linux o macOS con Bash.
- JDK 25.
- `curl`, `unzip` y `sha256sum`.
- Conexión a Internet durante la primera ejecución.

## Instalar y ejecutar

Clonar o descargar este repositorio y, desde su raíz, ejecutar:

```bash
JAVA_HOME=/ruta/al/jdk-25 ./scripts/install-and-run.sh
```

Se crea `./josm-overture-bundle/` con JOSM, `geoparquet.jar`,
`overturemaps.jar`, preferencias, datos y caché. Las ejecuciones siguientes
reutilizan las descargas.

GeoParquet se instala automáticamente porque es una dependencia requerida. El
perfil es independiente de cualquier instalación previa de JOSM.

## Opciones

```bash
# Instalar y verificar sin abrir JOSM
./scripts/install-and-run.sh --prepare-only

# Elegir otra carpeta
./scripts/install-and-run.sh --dir /ruta/a/mi-josm

# Volver a descargar JOSM y ambos plugins
./scripts/install-and-run.sh --refresh
```

Para seleccionar otras releases:

```bash
GEOPARQUET_VERSION=0.3.0 OVERTUREMAPS_VERSION=0.1.0 \
  ./scripts/install-and-run.sh
```

Consultar `./scripts/install-and-run.sh --help` para ver todas las opciones.

## Verificación en JOSM

1. Confirmar que GeoParquet y Overture Maps aparecen habilitados en
   **Preferencias → Plugins**.
2. Abrir **Archivo → Discover Overture Maps GeoParquet...**.
3. Elegir release, tema y tipo; usar el viewport actual o una BBOX manual.
4. Seleccionar resultados y usar **Load selected as reference layer**.
5. Verificar que las capas cargadas sean referencias de solo lectura.

## Instalación manual

Descargar primero el JAR versionado desde las
[releases de GeoParquet](https://github.com/geoverse-dev/josm-geoparquet/releases)
y renombrarlo como `geoparquet.jar`. Luego descargar el JAR versionado desde las
[releases de Overture Maps](https://github.com/overturemaps-ar-community/josm-overturemaps/releases).
Renombrarlo como `overturemaps.jar`, copiar ambos en la carpeta de plugins de JOSM
y habilitarlos. JOSM debe iniciarse con Java 25, `--enable-preview` y los exports
de JVM usados por el script.

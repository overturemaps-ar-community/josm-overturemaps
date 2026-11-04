# JOSM Overture Maps

[English version](README.md)

Plugin de JOSM para descubrir archivos GeoParquet de Overture Maps mediante su
catálogo STAC estático y cargarlos como capas de referencia de solo lectura usando
el plugin GeoParquet independiente.

## Versión validada

El plugin fue validado contra el catálogo público de releases de Overture usando
datos `transportation/segment` que intersectan Tandil
(`-59.3,-37.5,-59.0,-37.2`). Se probaron el recorrido completo del catálogo, la
selección de mirrors AWS/Azure y la carga de capas mediante GeoParquet.

La versión `0.1.0` fue compilada y ejecutada con OpenJDK `25.0.4.1` y JOSM tested
`19613`. Un perfil aislado cargó `geoparquet 0.3.0` y `overturemaps 0.1.0`; el
descubrimiento, la tabla de resultados y la integración entre plugins están
implementados y cubiertos por pruebas determinísticas.

## Funcionalidades

- Lee dinámicamente releases, temas y tipos desde el catálogo STAC de Overture.
- Selecciona de forma predeterminada la release más nueva descubierta.
- Usa el viewport actual de JOSM en WGS84 o una BBOX manual visible.
- Soporta BBOX que cruzan el antimeridiano.
- Informa documentos procesados, Items encontrados y si el resultado es completo.
- Permite cancelar el descubrimiento y limitar la cantidad de documentos.
- Selecciona AWS, Azure o modo automático y colapsa mirrors del mismo Item lógico.
- Presenta resultados ordenables y carga uno o varios archivos con estado individual.
- Delega HTTP Range, filtrado espacial, renderización y estilos a GeoParquet.
- Mantiene las capas de Overture separadas y en modo de solo lectura.

## Requisitos

- Java 25.
- JOSM tested.
- Plugin **JOSM GeoParquet 0.3.0 o compatible**, instalado por separado.
- Para compilar: Maven, Git, curl y make.
- `com.kraftek:stac-client:1.0.0`, instalado por `make bootstrap`.

El manifiesto declara `Plugin-Requires: geoparquet`. Si el plugin requerido falta o
su API no es compatible, JOSM informa la dependencia y Overture Maps no intenta
duplicar el lector; copiar la URL permanece disponible como alternativa.

## Compilar y ejecutar

```bash
make bootstrap
make test
make build
make install
make run
```

La acción **Discover Overture Maps GeoParquet...** aparece en el menú Archivo. Para
el caso validado de Tandil se puede elegir `transportation` / `segment`, aunque el
contenido disponible siempre se obtiene dinámicamente del catálogo.

Seleccionar una o más filas y pulsar **Load selected as reference layer**. Cada
archivo conserva su propio estado y manejo de cancelación/error. El plugin
GeoParquet realiza la lectura remota y agrega la capa de referencia.

## Releases

Al pushear un tag semántico como `v0.1.0`, GitHub Actions ejecuta las pruebas con
Java 25, empaqueta el plugin, valida la versión del manifiesto y publica
`overturemaps-0.1.0.jar` junto con su SHA-256.

## Seguridad y alcance

Este plugin sirve para comparación visual. No realiza conflación, no convierte
Overture a OSM y no agrega datos de referencia a uploads de OpenStreetMap.

Las solicitudes de metadatos requieren HTTPS, tienen reintentos y límites, rechazan
documentos mayores a 5 MiB y se cachean durante la sesión del diálogo. La
normalización de compatibilidad del cliente STAC elimina objetos de extensiones no
soportados solamente de una copia temporal para parseo; conserva la respuesta
original, las URLs de assets y los campos STAC principales.

## Documentación

- [Especificación funcional](docs/PRODUCT_SPEC.md)
- [Arquitectura e integración](docs/ARCHITECTURE.md)
- [Roadmap y milestones completados](docs/IMPLEMENTATION_PLAN.md)
- [Pruebas de aceptación](docs/ACCEPTANCE_TESTS.md)

La documentación técnica se mantiene en inglés; este README proporciona la guía de
usuario equivalente en español.

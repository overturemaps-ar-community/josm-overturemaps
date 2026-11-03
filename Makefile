SHELL := /bin/bash
JOSM_JAR ?= .cache/josm-tested.jar
JOSM_URL ?= https://josm.openstreetmap.de/download/josm-tested.jar
STAC_DIR ?= .cache/stac
STAC_REPO ?= https://github.com/kraftek/stac.git
STAC_REF ?= f12957bd9ebe1f5972d23be502e8cce0de00b832
JOSM_PLUGINS_DIR ?= $(HOME)/.local/share/JOSM/plugins
MVN ?= mvn
.PHONY: bootstrap bootstrap-josm bootstrap-stac build test install run inspect clean
bootstrap: bootstrap-josm bootstrap-stac
bootstrap-josm:
	@mkdir -p .cache
	@if [ ! -s "$(JOSM_JAR)" ]; then curl -fL --retry 3 "$(JOSM_URL)" -o "$(JOSM_JAR)"; fi
bootstrap-stac:
	@if [ ! -d "$(STAC_DIR)/.git" ]; then git clone --no-checkout "$(STAC_REPO)" "$(STAC_DIR)"; fi
	@cd "$(STAC_DIR)" && git fetch --depth 1 origin "$(STAC_REF)" && git checkout --detach FETCH_HEAD
	$(MVN) -q -f "$(STAC_DIR)/pom.xml" -DskipTests install
build:
	@test -s "$(JOSM_JAR)" || { echo 'Run make bootstrap'; exit 1; }
	$(MVN) -DskipTests package
test:
	$(MVN) test
install: build
	@mkdir -p "$(JOSM_PLUGINS_DIR)"
	cp target/overturemaps.jar "$(JOSM_PLUGINS_DIR)/overturemaps.jar"
	@echo 'Ensure geoparquet.jar is installed; restart JOSM.'
run: bootstrap-josm
	java --enable-preview --add-exports=java.base/sun.security.action=ALL-UNNAMED --add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED --add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED -jar "$(JOSM_JAR)"
inspect:
	unzip -p target/overturemaps.jar META-INF/MANIFEST.MF
clean:
	$(MVN) clean

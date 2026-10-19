#!/bin/sh
# Linux / macOS build. Put the DFC jars in ./lib (or set DOCUMENTUM_SHARED).
cd "$(dirname "$0")" || exit 1
if [ -f lib/dfc.jar ]; then CP="lib/*:lib/lib/*"; elif [ -n "$DOCUMENTUM_SHARED" ]; then CP="$DOCUMENTUM_SHARED/*:$DOCUMENTUM_SHARED/lib/*"; else
  echo "DFC not found: copy dfc.jar and the other DFC jars into ./lib"; exit 1; fi
rm -rf build && mkdir -p build/classes
javac --release 17 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src -name '*.java') || exit 1
jar --create --file docuquery.jar --main-class com.sedin.docuquery.App -C build/classes . || exit 1
echo "Build OK: docuquery.jar - start with ./run.sh"

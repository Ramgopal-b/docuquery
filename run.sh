#!/bin/sh
cd "$(dirname "$0")" || exit 1
if [ -f lib/dfc.jar ]; then CP="lib/*:lib/lib/*"; else CP="$DOCUMENTUM_SHARED/*:$DOCUMENTUM_SHARED/lib/*"; fi
CONF="${DFC_CONFIG:-config}"
exec java -Xmx1g -Ddfc.properties.file="$CONF/dfc.properties" \
  --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/sun.security.provider=ALL-UNNAMED \
  -cp "docuquery.jar:$CONF:$CP" com.sedin.docuquery.App

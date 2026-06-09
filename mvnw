#!/bin/sh
# Maven Wrapper script
# Baixa e usa a versão exata do Maven definida em .mvn/wrapper/maven-wrapper.properties

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep "wrapperUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2)
    echo "Baixando Maven Wrapper de: $WRAPPER_URL"
    if command -v curl > /dev/null; then
        curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget > /dev/null; then
        wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "ERRO: curl ou wget necessário para baixar o Maven Wrapper." >&2
        exit 1
    fi
fi

exec java -jar "$MAVEN_WRAPPER_JAR" "$@"

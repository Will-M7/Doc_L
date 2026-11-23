#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:-1.1.0}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Versión inválida' >&2; exit 1; }
command -v jpackage >/dev/null
mkdir -p target dist
work="$(mktemp -d "$PWD/target/linux-package.XXXXXXXX")"
artifact="$PWD/dist/EstudioDocumental-${version}-linux-x64.tar.gz"
[[ ! -e "$artifact" ]] || { echo "Se conserva el paquete existente: $artifact" >&2; exit 1; }
./mvnw -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
  -DincludeScope=runtime "-DoutputDirectory=$work/input"
cp target/estudio-documental-1.1.0.jar.original "$work/input/studio.jar"
jpackage --type app-image --name EstudioDocumental --app-version "$version" \
  --input "$work/input" --dest "$work/app" --main-jar studio.jar \
  --main-class com.ejemplo.crudpersona.MCLauncher
tar -czf "$artifact" -C "$work/app" EstudioDocumental
(cd dist && sha256sum "$(basename "$artifact")" > "$(basename "$artifact").sha256")
echo "Paquete creado: $artifact"

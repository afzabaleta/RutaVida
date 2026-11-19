#!/bin/sh
# Starts RutaVida with the jar of "mvn package". Needs backend/db.properties (never in Git).
cd "$(dirname "$0")/backend" || exit 1
if [ ! -f target/rutavida.jar ]; then
  echo "[ERROR] No existe backend/target/rutavida.jar. Primero: mvn -f backend/pom.xml package"
  exit 1
fi
if [ ! -f db.properties ]; then
  echo "[ERROR] Falta backend/db.properties. Copie db.properties.example y escriba la contraseña."
  exit 1
fi
exec java -jar target/rutavida.jar "$@"

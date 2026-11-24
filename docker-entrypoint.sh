#!/bin/sh
set -eu

# Render secret files only accept plaintext. Oracle Wallets contain binary files,
# so the whole wallet ZIP is supplied as base64 and unpacked only at container start.
if [ -n "${RUTAVIDA_ORACLE_WALLET_B64:-}" ]; then
    wallet_archive="$(mktemp)"
    trap 'rm -f "$wallet_archive"' EXIT
    printf '%s' "$RUTAVIDA_ORACLE_WALLET_B64" | base64 --decode > "$wallet_archive"
    unzip -oq "$wallet_archive" -d /etc/secrets
fi

exec java -jar /app/rutavida.jar

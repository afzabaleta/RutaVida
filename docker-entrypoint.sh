#!/bin/sh
set -eu

# Render secret files only accept plaintext. Oracle Wallets contain binary files,
# so the whole wallet ZIP is supplied as base64 and unpacked only at container start.
if [ -n "${RUTAVIDA_ORACLE_WALLET_B64:-}" ]; then
    wallet_archive="$(mktemp)"
    wallet_dir="$(mktemp -d)"
    trap 'rm -f "$wallet_archive"; rm -rf "$wallet_dir"' EXIT
    printf '%s' "$RUTAVIDA_ORACLE_WALLET_B64" | base64 --decode > "$wallet_archive"
    unzip -oq "$wallet_archive" -d "$wallet_dir"

    # Render reserves /etc/secrets as a read-only mount. Point the JDBC driver
    # at the writable, runtime-only directory containing the extracted wallet.
    export RUTAVIDA_DB_URL="$(printf '%s' "$RUTAVIDA_DB_URL" | sed "s|TNS_ADMIN=/etc/secrets|TNS_ADMIN=$wallet_dir|")"
fi

exec java -jar /app/rutavida.jar

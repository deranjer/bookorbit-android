#!/usr/bin/env bash
# Starts the test server and, on first run, creates the admin and a library of public-domain books.
# Credentials are generated into .env; sign in from the emulator at http://10.0.2.2:3000.
set -euo pipefail
cd "$(dirname "$0")"

SERVER_VERSION=3.6.0
URL=http://localhost:3000/api/v1

if [[ ! -f .env ]]; then
  rand() { openssl rand -hex "$1"; }
  cat > .env <<ENV
APP_IMAGE=ghcr.io/bookorbit/bookorbit:${SERVER_VERSION}
POSTGRES_PASSWORD=$(rand 16)
JWT_SECRET=$(rand 32)
SETUP_BOOTSTRAP_TOKEN=$(rand 16)
ADMIN_USERNAME=reader
ADMIN_PASSWORD=Test$(rand 6)
ENV
fi
set -a; source .env; set +a

# Project Gutenberg EPUBs with images: varied lengths, covers and structure.
mkdir -p books
for id in 1342 84 2701 11 1661 345 98 174 1952 2554; do
  [[ -f books/pg$id.epub ]] || curl -fsSL -o "books/pg$id.epub" "https://www.gutenberg.org/ebooks/$id.epub3.images"
done

docker compose up -d
echo -n "waiting for the server"
until curl -fs "$URL/auth/setup-status" >/dev/null 2>&1; do echo -n .; sleep 2; done; echo

if curl -fs "$URL/auth/setup-status" | grep -q '"needsSetup":true'; then
  curl -fsS -X POST "$URL/auth/setup" -H 'content-type: application/json' -H "x-setup-token: $SETUP_BOOTSTRAP_TOKEN" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"name\":\"Test Reader\",\"email\":\"reader@example.test\",\"password\":\"$ADMIN_PASSWORD\"}" >/dev/null
  token=$(curl -fsS -X POST "$URL/auth/login" -H 'content-type: application/json' \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
  curl -fsS -X POST "$URL/libraries" -H 'content-type: application/json' -H "authorization: Bearer $token" \
    -d '{"name":"Classics","icon":"book","folders":["/books"]}' >/dev/null
  echo "created the admin and the Classics library"
fi
echo "server: http://10.0.2.2:3000 from the emulator, http://localhost:3000 here. Credentials are in tools/testserver/.env"

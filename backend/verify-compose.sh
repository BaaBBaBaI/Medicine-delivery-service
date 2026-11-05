#!/usr/bin/env bash
set -euo pipefail
BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_PATH="$BACKEND_DIR/docker-compose.yml"
echo "Compose path: $COMPOSE_PATH"
docker compose -f "$COMPOSE_PATH" config


#!/usr/bin/env bash
set -euo pipefail

WAIT_TIMEOUT="${1:-420}"
BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$BACKEND_DIR")"
COMPOSE_PATH="$BACKEND_DIR/docker-compose.yml"
ROOT_ENV_PATH="$REPO_ROOT/.env"
LOCAL_ENV_PATH="$BACKEND_DIR/.env"

cd "$BACKEND_DIR"

if [ ! -f "$COMPOSE_PATH" ]; then
    echo "ERROR: docker-compose.yml not found: $COMPOSE_PATH" >&2
    exit 1
fi

ENV_PATH=""
if [ -f "$ROOT_ENV_PATH" ]; then
    ENV_PATH="$ROOT_ENV_PATH"
elif [ -f "$LOCAL_ENV_PATH" ]; then
    ENV_PATH="$LOCAL_ENV_PATH"
fi

if [ -z "$ENV_PATH" ]; then
    echo ".env not found. Copying from .env.example..."
    if [ -f "$BACKEND_DIR/.env.example" ]; then
        cp "$BACKEND_DIR/.env.example" "$LOCAL_ENV_PATH"
        cp "$BACKEND_DIR/.env.example" "$ROOT_ENV_PATH"
        ENV_PATH="$LOCAL_ENV_PATH"
    else
        echo "ERROR: .env.example not found" >&2
        exit 1
    fi
fi

echo "Stopping stale containers (database volume is preserved)..."
docker compose --env-file "$ENV_PATH" -f "$COMPOSE_PATH" down --remove-orphans || true

echo "Starting PostgreSQL 17 and Medicine Delivery Spring Boot Backend..."
docker compose --env-file "$ENV_PATH" -f "$COMPOSE_PATH" up -d --force-recreate --wait --wait-timeout "$WAIT_TIMEOUT"

echo ""
echo "============================================================"
echo " MEDICINE DELIVERY SERVICE IS READY"
echo "============================================================"
echo " Web UI:           http://localhost:8080/"
echo " Swagger UI:       http://localhost:8080/swagger-ui.html"
echo " REST API:         http://localhost:8080/api"
echo " OpenAPI JSON:     http://localhost:8080/v3/api-docs"
echo " Health:           http://localhost:8080/actuator/health"
echo " PostgreSQL JDBC:  jdbc:postgresql://localhost:55432/medicine_delivery"
echo "============================================================"
docker compose --env-file "$ENV_PATH" -f "$COMPOSE_PATH" ps


#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/docker/.uac.env"
LEGACY_ENV_FILE="$SCRIPT_DIR/docker/.openproject.env"
BOOTSTRAP_SCRIPT="$SCRIPT_DIR/docker/openproject_bootstrap.sh"
DEV_USER_SCRIPT="$SCRIPT_DIR/docker/openproject_create_dev_user.sh"
DOCKER_COMPOSE_FILE="$SCRIPT_DIR/docker/docker-compose.yml"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${BLUE}ℹ${NC} $1"; }
success() { echo -e "${GREEN}✓${NC} $1"; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
error() { echo -e "${RED}✗${NC} $1"; }

load_existing_env() {
  local candidate
  for candidate in "$ENV_FILE" "$LEGACY_ENV_FILE"; do
    if [[ -f "$candidate" ]]; then
      # shellcheck disable=SC1090
      set +e
      set -a
      source "$candidate"
      local rc=$?
      set +a
      set -e
      if [[ $rc -eq 0 ]]; then
        success "Loaded existing values from $candidate"
        return 0
      fi
      warn "Ignoring malformed env file: $candidate"
    fi
  done
  return 1
}

prompt_value() {
  local var_name="$1"
  local prompt_label="$2"
  local default_value="${3:-}"
  local secret="${4:-false}"
  local current_value="${!var_name:-$default_value}"
  local input=""

  if [[ "$secret" == "true" ]]; then
    if [[ -n "$current_value" ]]; then
      read -r -s -p "$prompt_label [hidden, press Enter to keep current]: " input
    else
      read -r -s -p "$prompt_label: " input
    fi
    echo
  else
    read -r -p "$prompt_label [$current_value]: " input
  fi

  if [[ -n "$input" ]]; then
    printf -v "$var_name" '%s' "$input"
  elif [[ -n "$current_value" ]]; then
    printf -v "$var_name" '%s' "$current_value"
  else
    printf -v "$var_name" '%s' "$default_value"
  fi
}

check_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    warn "Docker is not installed or not on PATH. Skipping runtime checks."
    return 1
  fi
  if ! docker info >/dev/null 2>&1; then
    warn "Docker daemon is not reachable. Start Docker Desktop and rerun if you want readiness checks."
    return 1
  fi
  success "Docker daemon is available"
  return 0
}

check_openproject_http() {
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$OPENPROJECT_BASE_URL" || true)
  if [[ "$code" == "302" || "$code" == "200" ]]; then
    success "OpenProject HTTP endpoint is reachable at $OPENPROJECT_BASE_URL (HTTP $code)"
    return 0
  fi
  warn "OpenProject HTTP endpoint is not ready at $OPENPROJECT_BASE_URL (HTTP ${code:-000})"
  return 1
}

maybe_start_openproject() {
  if ! check_docker; then
    return 0
  fi

  local answer=""
  read -r -p "Start/restart OpenProject containers now? [y/N]: " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    return 0
  fi

  docker compose -f "$DOCKER_COMPOSE_FILE" up -d openproject-db openproject
  success "Requested OpenProject startup via Docker Compose"
}

maybe_create_openproject_dev_user() {
  if ! check_docker; then
    return 0
  fi

  local answer=""
  read -r -p "Create/update local OpenProject dev user '$OPENPROJECT_DEV_LOGIN' and generate API token now? [Y/n]: " answer
  if [[ -n "$answer" && ! "$answer" =~ ^[Yy]$ ]]; then
    info "Skipping OpenProject dev user/token generation."
    return 0
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx 'uac-openproject'; then
    warn "OpenProject container is not running; cannot generate dev user/token yet."
    return 0
  fi

  local output token
  if ! output=$(OPENPROJECT_DEV_LOGIN="$OPENPROJECT_DEV_LOGIN" \
      OPENPROJECT_DEV_PASSWORD="$OPENPROJECT_DEV_PASSWORD" \
      OPENPROJECT_DEV_FIRSTNAME="$OPENPROJECT_DEV_LOGIN" \
      OPENPROJECT_DEV_LASTNAME="Local" \
      OPENPROJECT_DEV_EMAIL="uac@local.test" \
      "$DEV_USER_SCRIPT"); then
    warn "Failed to create/update OpenProject dev user."
    return 0
  fi

  echo "$output"
  token=$(printf '%s\n' "$output" | awk -F= '/^TOKEN=/{print $2}' | tail -n 1)
  if [[ -n "$token" ]]; then
    OPENPROJECT_API_TOKEN="$token"
    success "Captured OpenProject API token for $OPENPROJECT_DEV_LOGIN"
  else
    warn "OpenProject dev user command ran, but no token was captured."
  fi
}

write_env_file() {
  mkdir -p "$SCRIPT_DIR/docker"
  {
    printf 'OPENPROJECT_BASE_URL=%q\n' "$OPENPROJECT_BASE_URL"
    printf 'OPENPROJECT_PROJECT_IDENTIFIER=%q\n' "$OPENPROJECT_PROJECT_IDENTIFIER"
    printf 'OPENPROJECT_PROJECT_NAME=%q\n' "$OPENPROJECT_PROJECT_NAME"
    printf 'OPENPROJECT_DEV_LOGIN=%q\n' "$OPENPROJECT_DEV_LOGIN"
    printf 'OPENPROJECT_DEV_PASSWORD=%q\n' "$OPENPROJECT_DEV_PASSWORD"
    printf 'OPENPROJECT_API_TOKEN=%q\n' "$OPENPROJECT_API_TOKEN"
    printf 'GITHUB_TOKEN=%q\n' "$GITHUB_TOKEN"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  success "Wrote integration env to $ENV_FILE"
}

maybe_bootstrap_openproject() {
  if [[ -z "$OPENPROJECT_API_TOKEN" ]]; then
    warn "OPENPROJECT_API_TOKEN is empty; skipping OpenProject API/bootstrap validation."
    return 0
  fi

  if ! check_openproject_http; then
    warn "OpenProject UI/API is not reachable yet. You can rerun $BOOTSTRAP_SCRIPT later."
    return 0
  fi

  local answer=""
  read -r -p "Run OpenProject project bootstrap now? [Y/n]: " answer
  if [[ -n "$answer" && ! "$answer" =~ ^[Yy]$ ]]; then
    info "Skipping bootstrap for now."
    return 0
  fi

  if "$BOOTSTRAP_SCRIPT"; then
    success "OpenProject bootstrap completed"
  else
    warn "OpenProject bootstrap failed. This usually means the token is invalid or OpenProject setup is not completed yet."
  fi
}

main() {
  echo "UAC integration setup"
  echo "- prepares OpenProject + optional GitHub token env"
  echo "- writes docker/.uac.env"
  echo "- can start OpenProject and bootstrap the uac project"
  echo

  load_existing_env || true

  OPENPROJECT_BASE_URL="${OPENPROJECT_BASE_URL:-http://localhost:8084}"
  OPENPROJECT_PROJECT_IDENTIFIER="${OPENPROJECT_PROJECT_IDENTIFIER:-uac}"
  OPENPROJECT_PROJECT_NAME="${OPENPROJECT_PROJECT_NAME:-UAC Monitoring}"
  OPENPROJECT_DEV_LOGIN="${OPENPROJECT_DEV_LOGIN:-UAC}"
  OPENPROJECT_DEV_PASSWORD="${OPENPROJECT_DEV_PASSWORD:-uac123}"
  OPENPROJECT_API_TOKEN="${OPENPROJECT_API_TOKEN:-}"
  GITHUB_TOKEN="${GITHUB_TOKEN:-}"

  prompt_value OPENPROJECT_BASE_URL "OpenProject base URL" "$OPENPROJECT_BASE_URL"
  prompt_value OPENPROJECT_PROJECT_IDENTIFIER "OpenProject project identifier" "$OPENPROJECT_PROJECT_IDENTIFIER"
  prompt_value OPENPROJECT_PROJECT_NAME "OpenProject project name" "$OPENPROJECT_PROJECT_NAME"
  prompt_value OPENPROJECT_DEV_LOGIN "OpenProject local dev username" "$OPENPROJECT_DEV_LOGIN"
  prompt_value OPENPROJECT_DEV_PASSWORD "OpenProject local dev password" "$OPENPROJECT_DEV_PASSWORD" true
  prompt_value OPENPROJECT_API_TOKEN "OpenProject API token" "$OPENPROJECT_API_TOKEN" true
  prompt_value GITHUB_TOKEN "GitHub token (optional, for REST fallback)" "$GITHUB_TOKEN" true

  maybe_start_openproject
  maybe_create_openproject_dev_user
  write_env_file
  maybe_bootstrap_openproject

  echo
  success "Setup complete"
  echo "Next steps:"
  echo "  1. ./run_demo.sh --local-systems"
  echo "  2. Open http://localhost:8888 and check the Tickets tab"
  echo "  3. Open $OPENPROJECT_BASE_URL/projects/$OPENPROJECT_PROJECT_IDENTIFIER"
  echo "  4. Login to OpenProject with $OPENPROJECT_DEV_LOGIN / $OPENPROJECT_DEV_PASSWORD"
}

main "$@"


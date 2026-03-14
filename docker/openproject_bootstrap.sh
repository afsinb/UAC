#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTEGRATION_ENV="$SCRIPT_DIR/.uac.env"
LEGACY_ENV="$SCRIPT_DIR/.openproject.env"

if [[ -f "$INTEGRATION_ENV" ]]; then
  # shellcheck disable=SC1090
  set +e
  set -a
  source "$INTEGRATION_ENV"
  rc=$?
  set +a
  set -e
  if [[ $rc -ne 0 ]]; then
    echo "Ignoring malformed env file: $INTEGRATION_ENV"
  fi
elif [[ -f "$LEGACY_ENV" ]]; then
  # shellcheck disable=SC1090
  set +e
  set -a
  source "$LEGACY_ENV"
  rc=$?
  set +a
  set -e
  if [[ $rc -ne 0 ]]; then
    echo "Ignoring malformed env file: $LEGACY_ENV"
  fi
fi

OPENPROJECT_BASE_URL="${OPENPROJECT_BASE_URL:-http://localhost:8084}"
OPENPROJECT_API_TOKEN="${OPENPROJECT_API_TOKEN:-}"
OPENPROJECT_PROJECT_IDENTIFIER="${OPENPROJECT_PROJECT_IDENTIFIER:-uac}"
OPENPROJECT_PROJECT_NAME="${OPENPROJECT_PROJECT_NAME:-UAC Monitoring}"

if [[ -z "$OPENPROJECT_API_TOKEN" ]]; then
  echo "OPENPROJECT_API_TOKEN is not set."
  echo "Create an API token in OpenProject (My account -> Access tokens), then re-run."
  exit 1
fi

auth_header="Authorization: Basic $(printf 'apikey:%s' "$OPENPROJECT_API_TOKEN" | base64)"

# Check API availability
code=$(curl -s -o /tmp/openproject_api_check.json -w "%{http_code}" \
  -H "$auth_header" \
  -H "Accept: application/json" \
  "$OPENPROJECT_BASE_URL/api/v3")

if [[ "$code" != "200" ]]; then
  echo "OpenProject API is not ready or token is invalid (HTTP $code)."
  cat /tmp/openproject_api_check.json
  exit 1
fi

# Create project if not exists
project_check_code=$(curl -s -o /tmp/openproject_project_get.json -w "%{http_code}" \
  -H "$auth_header" \
  -H "Accept: application/json" \
  "$OPENPROJECT_BASE_URL/api/v3/projects/$OPENPROJECT_PROJECT_IDENTIFIER")

if [[ "$project_check_code" == "200" ]]; then
  echo "Project '$OPENPROJECT_PROJECT_IDENTIFIER' already exists."
else
  payload=$(cat <<JSON
{
  "identifier": "$OPENPROJECT_PROJECT_IDENTIFIER",
  "name": "$OPENPROJECT_PROJECT_NAME"
}
JSON
)

  create_code=$(curl -s -o /tmp/openproject_project_create.json -w "%{http_code}" \
    -X POST \
    -H "$auth_header" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$OPENPROJECT_BASE_URL/api/v3/projects")

  if [[ "$create_code" != "201" ]]; then
    echo "Failed to create project '$OPENPROJECT_PROJECT_IDENTIFIER' (HTTP $create_code)."
    cat /tmp/openproject_project_create.json
    exit 1
  fi

  echo "Project '$OPENPROJECT_PROJECT_IDENTIFIER' created."
fi

echo "OpenProject bootstrap finished."


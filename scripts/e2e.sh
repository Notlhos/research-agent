#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8000}"
API_KEY="${API_KEY:-${RESEARCH_API_KEY:-local-api-key}}"
TOPIC="${1:-retrieval augmented generation evaluation}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-300}"

api() {
  curl -sf -H "X-API-Key: ${API_KEY}" "$@"
}

command -v jq > /dev/null || { echo "jq is required"; exit 1; }

echo "Waiting for API at ${BASE_URL}"
for _ in $(seq 1 60); do
  if api "${BASE_URL}/api/research" > /dev/null; then
    break
  fi
  sleep 3
done

run_id=$(api -X POST "${BASE_URL}/api/research" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg topic "${TOPIC}" '{topic: $topic}')" | jq -r .id)
echo "Run ${run_id} created for topic: ${TOPIC}"

deadline=$((SECONDS + TIMEOUT_SECONDS))
status=""
while [ "${SECONDS}" -lt "${deadline}" ]; do
  status=$(api "${BASE_URL}/api/research/${run_id}" | jq -r .status)
  echo "  status: ${status}"
  case "${status}" in
    DONE | FAILED | CANCELLED) break ;;
  esac
  sleep 3
done

run=$(api "${BASE_URL}/api/research/${run_id}")
if [ "${status}" != "DONE" ]; then
  echo "Run did not finish successfully: ${run}"
  exit 1
fi

report=$(api "${BASE_URL}/api/research/${run_id}/report")
echo "${report}"
echo "${report}" | grep -q "## References" || { echo "Report has no references"; exit 1; }
echo "E2E passed: run ${run_id} is DONE with a cited report"

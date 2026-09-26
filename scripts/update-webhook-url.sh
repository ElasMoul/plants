#!/usr/bin/env bash
# Re-points the GitHub workflow_job webhook at the current cloudflared quick-tunnel URL.
# Run after (re)starting the webhook-tunnel compose service: its *.trycloudflare.com
# hostname is random per container start, so the GitHub hook goes stale on restart.
#
# Usage: scripts/update-webhook-url.sh [owner/repo]   (default: elmoul/conventions)
set -euo pipefail

REPO="${1:-elmoul/conventions}"
ENV_FILE="$(dirname "$0")/../.env"
TOKEN=$(grep -E '^GITHUB_TOKEN=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r\n')
SECRET=$(grep -E '^GITHUB_WEBHOOK_SECRET=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r\n')

TUNNEL_URL=$(docker logs ci-runner-webhook-tunnel-1 2>&1 \
  | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1)
if [ -z "$TUNNEL_URL" ]; then
  echo "ERROR: no trycloudflare URL in ci-runner-webhook-tunnel-1 logs — is the tunnel up?" >&2
  exit 1
fi

api() { curl -sf -m 15 -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "$@"; }

HOOK_ID=$(api "https://api.github.com/repos/$REPO/hooks" \
  | python -c "import sys,json; hs=[h for h in json.load(sys.stdin) if h['config'].get('url','').endswith('/webhook')]; print(hs[0]['id'] if hs else '')")

BODY=$(python -c "import json,sys; print(json.dumps({'active': True, 'events': ['workflow_job'], 'config': {'url': sys.argv[1] + '/webhook', 'content_type': 'json', 'secret': sys.argv[2], 'insecure_ssl': '0'}}))" "$TUNNEL_URL" "$SECRET")

if [ -n "$HOOK_ID" ]; then
  api -X PATCH -d "$BODY" "https://api.github.com/repos/$REPO/hooks/$HOOK_ID" > /dev/null
  echo "updated hook $HOOK_ID on $REPO -> $TUNNEL_URL/webhook"
else
  api -X POST -d "$BODY" "https://api.github.com/repos/$REPO/hooks" > /dev/null
  echo "created hook on $REPO -> $TUNNEL_URL/webhook"
fi

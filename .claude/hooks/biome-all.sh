#!/usr/bin/env bash
# Stop hook: whole-app Biome check as a safety net before the turn ends.
# Catches files touched outside Edit/Write (scripts, git ops, moves).
set -uo pipefail

input=$(cat)
# Never block twice — avoids a stop/re-stop loop if Biome stays unhappy.
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ADMIN="$ROOT/apps/admin"
BIOME="$ADMIN/node_modules/.bin/biome"
[ -x "$BIOME" ] || exit 0

out=$(cd "$ADMIN" && "$BIOME" check --no-errors-on-unmatched . 2>&1)
[ $? -eq 0 ] && exit 0

printf 'Biome check failed in apps/admin — fix these before finishing:\n%s\n' "$out" >&2
exit 2

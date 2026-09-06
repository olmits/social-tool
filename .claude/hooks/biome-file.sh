#!/usr/bin/env bash
# PostToolUse hook: format + lint a single edited file in apps/admin.
# Autofixes what Biome can fix; exits 2 (feeding stderr back to Claude) for what it can't.
set -uo pipefail

# Resolve from the script's own location, so this works no matter which
# directory Claude Code was launched from.
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ADMIN="$ROOT/apps/admin"
BIOME="$ADMIN/node_modules/.bin/biome"
[ -x "$BIOME" ] || exit 0

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] || exit 0
case "$file" in
  "$ADMIN"/*) ;;
  *) exit 0 ;;
esac
case "$file" in
  *.ts|*.tsx|*.js|*.jsx|*.mjs|*.cjs|*.json|*.jsonc|*.css) ;;
  *) exit 0 ;;
esac

out=$(cd "$ADMIN" && "$BIOME" check --write --no-errors-on-unmatched "$file" 2>&1)
[ $? -eq 0 ] && exit 0

printf 'Biome found issues it could not auto-fix in %s:\n%s\n' "$file" "$out" >&2
exit 2

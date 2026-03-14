#!/usr/bin/env bash
set -euo pipefail

if ! command -v npx >/dev/null 2>&1; then
  echo 'npx is required to run Supabase CLI.' >&2
  exit 1
fi

exec npx --yes supabase "$@"

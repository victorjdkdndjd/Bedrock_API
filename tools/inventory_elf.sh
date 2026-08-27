#!/usr/bin/env bash
set -euo pipefail
dir="${1:-.}"
printf '%-30s %-11s %-9s %-8s %-7s %s\n' FILE SIZE ARCH STRIPPED EXPORTS BUILD_ID
for f in "$dir"/*.so; do
  [[ -e "$f" ]] || continue
  name=$(basename "$f"); size=$(stat -c%s "$f"); arch=$(readelf -h "$f" | awk -F: '/Machine:/{gsub(/^ +/,"",$2);print $2}')
  if readelf -S "$f" | grep -q '\.symtab'; then stripped=no; else stripped=yes; fi
  exports=$(nm -D --defined-only "$f" 2>/dev/null | wc -l || true)
  bid=$(readelf -n "$f" 2>/dev/null | awk '/Build ID:/{print $3; found=1; exit} END{if(!found)print "-"}')
  printf '%-30s %-11s %-9s %-8s %-7s %s\n' "$name" "$size" "$arch" "$stripped" "$exports" "$bid"
done

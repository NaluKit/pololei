#!/usr/bin/env bash
#
# Copyright © 2026 Frank Hossfeld
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

# Tests central-state.sh and publish-github-release.sh with stubs for curl, gh and the Central
# repository; nothing is published and no network is used.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
VERSION=1.0.0
failed=0
fail() { echo "FAIL: $*" >&2; failed=1; }

# Stub curl: serves $CENTRAL_DIR/<last two path parts of the URL>; 404 if missing.
mkdir -p "$WORK/bin" "$WORK/central" "$WORK/release"
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
out=; url=
while [[ $# -gt 0 ]]; do
  case $1 in
    -o) out=$2; shift 2 ;;
    -w|-sS) [[ $1 == -w ]] && shift; shift ;;
    *) url=$1; shift ;;
  esac
done
file="$CENTRAL_DIR/$(basename "$url")"
if [[ -f $file ]]; then cp "$file" "$out"; printf 200; else : > "$out"; printf 404; fi
STUB
# Stub gh: the state of the "release" is a directory GH_DIR (exists = release exists).
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "gh $*" >> "$GH_LOG"
case "$1 $2" in
  "release view")
    [[ -d $GH_DIR ]] || exit 1
    if [[ $* == *--json* ]]; then ls "$GH_DIR"; fi ;;
  "release download") cat "$GH_DIR/SHA256SUMS" ;;
  "release upload") cp "$4" "$GH_DIR/" ;;
  "release create")
    mkdir -p "$GH_DIR"
    shift 2
    while [[ $# -gt 0 ]]; do
      case $1 in
        --target|--title|--notes-file) shift 2 ;;
        *) cp "$1" "$GH_DIR/"; shift ;;
      esac
    done ;;
esac
STUB
chmod +x "$WORK/bin/curl" "$WORK/bin/gh"
export PATH="$WORK/bin:$PATH" CENTRAL_DIR="$WORK/central" GH_DIR="$WORK/gh" GH_LOG="$WORK/gh.log"

for module in pololei pololei-epc; do
  echo "jar of $module" > "$WORK/release/$module-$VERSION.jar"
  echo '{}' > "$WORK/release/$module-$VERSION-cyclonedx.json"
done
echo "notes" > "$WORK/release/RELEASE-NOTES.md"
(cd "$WORK/release" && shasum -a 256 ./*.jar ./*.json > SHA256SUMS)

export CENTRAL_BASE_URL=unused
state() { "$HERE/central-state.sh" "$VERSION" "$WORK/release" 2> "$WORK/err" || echo "exit"; }

# Central: nothing there, everything identical, a differing JAR, only some modules
[[ $(state) == absent ]] || fail "empty Central should be absent"
cp "$WORK/release"/*-$VERSION.jar "$WORK/central/"
[[ $(state) == identical ]] || fail "identical JARs should skip the deploy"
echo changed > "$WORK/central/pololei-epc-$VERSION.jar"
[[ $(state) == exit ]] || fail "a differing JAR must fail"
rm "$WORK/central/pololei-epc-$VERSION.jar"
[[ $(state) == exit ]] || fail "a partial deploy must fail"

# GitHub release in a scratch repository
cd "$WORK"
git init -q repo && cd repo
git -c user.name=t -c user.email=t@example.org commit -q --allow-empty -m one
COMMIT=$(git rev-parse HEAD)
export EXPECTED_COMMIT=$COMMIT
publish() { "$HERE/publish-github-release.sh" "$VERSION" "$WORK/release" > "$WORK/out" 2> "$WORK/err" || echo "exit"; }

[[ $(publish) != exit ]] || fail "creating the release failed"
grep -q "Created" "$WORK/out" || fail "release not created"
# interrupted run: an asset is missing; re-run adds it only
rm "$GH_DIR/pololei-epc-$VERSION-cyclonedx.json"
: > "$GH_LOG"
[[ $(publish) != exit ]] || fail "completing the release failed"
grep -q "release upload.*cyclonedx" "$GH_LOG" || fail "missing asset not uploaded"
grep -q "release create" "$GH_LOG" && fail "a second release was created"
[[ $(grep -c "release upload" "$GH_LOG") -eq 1 ]] || fail "existing assets were uploaded again"
# complete release: nothing to do
: > "$GH_LOG"
publish > /dev/null
grep -q "release upload\|release create" "$GH_LOG" && fail "complete release was changed"
# other checksums
echo "other" > "$GH_DIR/SHA256SUMS"
[[ $(publish) == exit ]] || fail "differing SHA256SUMS must fail"
echo "$(cat "$WORK/release/SHA256SUMS")" > "$GH_DIR/SHA256SUMS"
# tag on another commit
git -c user.name=t -c user.email=t@example.org commit -q --allow-empty -m two
git tag "$VERSION"
[[ $(publish) == exit ]] || fail "a tag on another commit must fail"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Release publishing: all checks passed."

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

# Tests collect-release-files.sh with synthetic build outputs: a complete set, and sets without a
# sources or javadoc JAR, an SBOM or the report, plus leftovers of another version.
set -euo pipefail

SCRIPT=$(cd "$(dirname "$0")" && pwd)/collect-release-files.sh
WORK=$(mktemp -d)
trap 'rm -rf "${WORK:?}"' EXIT
VERSION=1.0.0
failed=0
fail() { echo "FAIL: $*" >&2; failed=1; }

# a project tree with the script and one module list, like the real one
ROOT="$WORK/project"
mkdir -p "$ROOT/scripts/release"
cp "$SCRIPT" "$ROOT/scripts/release/"
cat > "$ROOT/pom.xml" <<POM
<modules>
  <module>alpha</module>
  <module>beta</module>
</modules>
POM
printf '## [%s] - 2026-01-01\n\nNotes.\n' "$VERSION" > "$ROOT/CHANGELOG.md"
echo '<html/>' > "$WORK/report.html"

build() {
  for module in alpha beta; do
    mkdir -p "$ROOT/$module/target"
    for suffix in "" -sources -javadoc; do
      echo "$module$suffix" > "$ROOT/$module/target/$module-$VERSION$suffix.jar"
    done
    echo '{}' > "$ROOT/$module/target/bom.json"
  done
}
collect() {
  rm -rf "${WORK:?}/out"
  "$ROOT/scripts/release/collect-release-files.sh" "$VERSION" "$WORK/out" "$@" > "$WORK/log" 2>&1 && echo ok || echo failed
}

build
[[ $(collect "$WORK/report.html") == ok ]] || { cat "$WORK/log" >&2; fail "complete set"; }
grep -q 'Collected 6 JARs' "$WORK/log" || fail "wrong JAR count"
for f in alpha-$VERSION-sources.jar beta-$VERSION-javadoc.jar pololei-$VERSION-dependency-check-report.html; do
  [[ -f $WORK/out/$f ]] || fail "missing $f in the output"
  grep -q "$f" "$WORK/out/SHA256SUMS" || fail "$f is not in SHA256SUMS"
done
(cd "$WORK/out" && shasum -a 256 -c SHA256SUMS > /dev/null) || fail "SHA256SUMS does not match"
[[ $(grep -c . "$WORK/out/SHA256SUMS") -eq 9 ]] || fail "SHA256SUMS should list 6 JARs, 2 SBOMs and the report"

# each missing artifact is named and fails
for victim in alpha/target/alpha-$VERSION-sources.jar beta/target/beta-$VERSION-javadoc.jar beta/target/bom.json alpha/target/alpha-$VERSION.jar; do
  build
  rm "${ROOT:?}/${victim:?}"
  [[ $(collect) == failed ]] || fail "missing $victim was not detected"
  grep -q "$(basename "$victim")" "$WORK/log" || fail "message does not name $victim"
done

# a missing report file
build
[[ $(collect "$WORK/missing.html") == failed ]] || fail "missing report not detected"

# leftovers: a non-empty output directory is refused
build
mkdir -p "$WORK/out" && echo old > "$WORK/out/alpha-0.9.0.jar"
if "$ROOT/scripts/release/collect-release-files.sh" "$VERSION" "$WORK/out" > "$WORK/log" 2>&1; then
  fail "non-empty directory accepted"
fi
# JARs of another version in target are not collected
echo old > "$ROOT/alpha/target/alpha-0.9.0.jar"
[[ $(collect) == ok ]] || fail "complete set with an old JAR"
[[ ! -f $WORK/out/alpha-0.9.0.jar ]] || fail "JAR of another version collected"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Release file collection: all checks passed."

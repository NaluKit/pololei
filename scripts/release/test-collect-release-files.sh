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
# sources or javadoc JAR, an SBOM, a POM or the report, plus leftovers of another version, the
# publication set for Maven Central, and the verification of SECURITY.md.
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
    echo "<project>$module</project>" > "$ROOT/$module/.flattened-pom.xml"
  done
  echo "<project>parent</project>" > "$ROOT/.flattened-pom.xml"
}
collect() {
  rm -rf "${WORK:?}/out"
  "$ROOT/scripts/release/collect-release-files.sh" "$VERSION" "$WORK/out" "$@" > "$WORK/log" 2>&1 && echo ok || echo failed
}

build
[[ $(collect "$WORK/report.html") == ok ]] || { cat "$WORK/log" >&2; fail "complete set"; }
grep -q 'Collected 6 JARs' "$WORK/log" || fail "wrong JAR count"
for f in alpha-$VERSION-sources.jar beta-$VERSION-javadoc.jar beta-$VERSION-cyclonedx.json; do
  [[ -f $WORK/out/$f ]] || fail "missing $f in the output"
  grep -q "$f" "$WORK/out/SHA256SUMS" || fail "$f is not in SHA256SUMS"
done
(cd "$WORK/out" && shasum -a 256 -c SHA256SUMS > /dev/null) || fail "SHA256SUMS does not match"
[[ $(grep -c . "$WORK/out/SHA256SUMS") -eq 8 ]] || fail "SHA256SUMS should list 6 JARs and 2 SBOMs"
grep -q "dependency-check-report" "$WORK/out/SHA256SUMS" && fail "the report must not be in SHA256SUMS"
[[ -f $WORK/out/pololei-$VERSION-dependency-check-report.html ]] || fail "missing report in the output"
(cd "$WORK/out" && shasum -a 256 -c SCAN-SHA256SUMS > /dev/null) || fail "SCAN-SHA256SUMS does not match"
[[ $(grep -c . "$WORK/out/SCAN-SHA256SUMS") -eq 1 ]] || fail "SCAN-SHA256SUMS should list the report only"

# the publication set for Maven Central: JARs, POMs and SBOMs of every module and the parent POM
(cd "$WORK/out/central" && shasum -a 256 -c CENTRAL-SHA256SUMS > /dev/null) || fail "CENTRAL-SHA256SUMS does not match"
[[ $(grep -c . "$WORK/out/central/CENTRAL-SHA256SUMS") -eq 11 ]] || fail "CENTRAL-SHA256SUMS should list 11 files"
for f in alpha/alpha-$VERSION.jar alpha/alpha-$VERSION-sources.jar beta/beta-$VERSION-javadoc.jar alpha/alpha-$VERSION.pom \
         beta/beta-$VERSION-cyclonedx.json pololei-parent/pololei-parent-$VERSION.pom; do
  grep -q " $f\$" "$WORK/out/central/CENTRAL-SHA256SUMS" || fail "$f is not in CENTRAL-SHA256SUMS"
done
cmp -s "$WORK/out/central/alpha/alpha-$VERSION.pom" "$ROOT/alpha/.flattened-pom.xml" || fail "the POM is not the flattened one"

# without a report there is no scan manifest
build
[[ $(collect) == ok ]] || fail "set without a report"
[[ ! -f $WORK/out/SCAN-SHA256SUMS ]] || fail "SCAN-SHA256SUMS without a report"

# each missing artifact is named and fails
for victim in alpha/target/alpha-$VERSION-sources.jar beta/target/beta-$VERSION-javadoc.jar beta/target/bom.json alpha/target/alpha-$VERSION.jar \
              beta/.flattened-pom.xml .flattened-pom.xml; do
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

# SECURITY.md, "Verifying a release": a rebuild without the report compares equal with a release
# that carries a report; a changed build artifact or a changed report is found
build
collect "$WORK/report.html" > /dev/null
rm -rf "$WORK/published" && cp -R "$WORK/out" "$WORK/published"
rm -rf "$WORK/rebuilt"
"$ROOT/scripts/release/collect-release-files.sh" "$VERSION" "$WORK/rebuilt" > /dev/null
diff "$WORK/rebuilt/SHA256SUMS" "$WORK/published/SHA256SUMS" > /dev/null || fail "documented comparison fails for identical artifacts"
(cd "$WORK/published" && shasum -a 256 -c SCAN-SHA256SUMS > /dev/null) || fail "documented report check fails for the original report"
echo changed > "$ROOT/beta/target/beta-$VERSION.jar"
rm -rf "$WORK/rebuilt"
"$ROOT/scripts/release/collect-release-files.sh" "$VERSION" "$WORK/rebuilt" > /dev/null
diff "$WORK/rebuilt/SHA256SUMS" "$WORK/published/SHA256SUMS" > /dev/null && fail "documented comparison accepts a changed JAR"
echo '<html>changed</html>' > "$WORK/published/pololei-$VERSION-dependency-check-report.html"
(cd "$WORK/published" && shasum -a 256 -c SCAN-SHA256SUMS > /dev/null 2>&1) && fail "documented report check accepts a changed report"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Release file collection: all checks passed."

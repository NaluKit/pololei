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

# Tests central-state.sh and publish-github-release.sh with stubs for curl, gh, Maven Central and
# the Central Portal; nothing is published and no network is used. The checks look at the content
# of the simulated release and Central, not only at names or call counts.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "${WORK:?}"' EXIT
VERSION=1.0.0
REPORT=pololei-$VERSION-dependency-check-report.html
ID=11111111-2222-3333-4444-555555555555
failed=0
fail() { echo "FAIL: $*" >&2; failed=1; }

# Stub curl: Maven Central serves $CENTRAL_DIR/<artifactId>/<file> (404 if missing), the Portal
# status of a deployment is $PORTAL_DIR/<id> (404 if missing).
mkdir -p "$WORK/bin" "$WORK/central" "$WORK/portal" "$WORK/release" "$WORK/records"
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
out=; url=
while [[ $# -gt 0 ]]; do
  case $1 in
    -o) out=$2; shift 2 ;;
    -w|-X|-H) shift 2 ;;
    -*) shift ;;
    *) url=$1; shift ;;
  esac
done
case $url in
  */api/v1/publisher/status\?id=*) file="$PORTAL_DIR/${url##*id=}" ;;
  *) rest=${url%/*/*}; file="$CENTRAL_DIR/${rest##*/}/${url##*/}" ;;
esac
if [[ -f $file ]]; then cp "$file" "$out"; printf 200; else : > "$out"; printf 404; fi
STUB
# Stub gh: the release is the directory GH_DIR (exists = release exists), GH_DIR.draft marks a draft.
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "gh $*" >> "$GH_LOG"
case "$1 $2" in
  "release view")
    [[ -d $GH_DIR ]] || exit 1
    case $* in
      *isDraft*) if [[ -f $GH_DIR.draft ]]; then echo true; else echo false; fi ;;
      *--json*) ls "$GH_DIR" ;;
    esac ;;
  "release download")
    pattern=$(sed -E 's/.*--pattern ([^ ]+).*/\1/' <<< "$*")
    [[ -f $GH_DIR/$pattern ]] || exit 1
    cat "$GH_DIR/$pattern" ;;
  "release upload")
    if [[ -f $GH_DIR/$(basename "$4") ]]; then echo "asset exists" >&2; exit 1; fi
    cp "$4" "$GH_DIR/" ;;
  "release create")
    [[ $* == *--draft* ]] || { echo "not a draft" >&2; exit 1; }
    mkdir -p "$GH_DIR"; touch "$GH_DIR.draft" ;;
  "release edit")
    if [[ $* == *--draft=false* ]]; then rm -f "${GH_DIR:?}.draft"; fi ;;
esac
STUB
chmod +x "$WORK/bin/curl" "$WORK/bin/gh"
GH_DIR="$WORK/gh"
export PATH="$WORK/bin:$PATH" CENTRAL_DIR="$WORK/central" PORTAL_DIR="$WORK/portal" GH_DIR GH_LOG="$WORK/gh.log"

# the release files as collect-release-files.sh writes them
R="$WORK/release"
mkdir -p "$R/central/pololei-parent"
for module in pololei pololei-epc; do
  mkdir -p "$R/central/$module"
  for suffix in "" -sources -javadoc; do
    echo "jar of $module$suffix" > "$R/$module-$VERSION$suffix.jar"
    cp "$R/$module-$VERSION$suffix.jar" "$R/central/$module/"
  done
  echo "{\"sbom\":\"$module\"}" > "$R/$module-$VERSION-cyclonedx.json"
  cp "$R/$module-$VERSION-cyclonedx.json" "$R/central/$module/"
  echo "<project>$module</project>" > "$R/central/$module/$module-$VERSION.pom"
done
echo "<project>parent</project>" > "$R/central/pololei-parent/pololei-parent-$VERSION.pom"
(cd "$R/central" && find . -mindepth 2 -type f | sed 's:^\./::' | LC_ALL=C sort | xargs shasum -a 256 > CENTRAL-SHA256SUMS)
echo "<html>report of the release</html>" > "$R/$REPORT"
echo "notes" > "$R/RELEASE-NOTES.md"
(cd "$R" && ls ./*.jar ./*.json | sed 's:^\./::' | LC_ALL=C sort | xargs shasum -a 256 > SHA256SUMS && shasum -a 256 "$REPORT" > SCAN-SHA256SUMS)
MANIFEST_HASH=$(shasum -a 256 < "$R/central/CENTRAL-SHA256SUMS" | cut -c1-64)

# --------------------------------------------------------------------------------- Maven Central
cd "$WORK"
git init -q repo && cd repo
git -c user.name=t -c user.email=t@example.org commit -q --allow-empty -m one
COMMIT=$(git rev-parse HEAD)
export EXPECTED_COMMIT=$COMMIT CENTRAL_BASE_URL=https://central.test/maven2 CENTRAL_PORTAL_URL=https://portal.test \
  CENTRAL_USERNAME=user CENTRAL_PASSWORD=secret
state() { "$HERE/central-state.sh" "$VERSION" "$R" "$WORK/records" 2> "$WORK/err" || echo "exit"; }
central_reset() { rm -rf "${WORK:?}/central"; mkdir -p "$WORK/central"; }
central_all() { central_reset; cp -R "$R/central/." "$WORK/central/"; rm -f "$WORK/central/CENTRAL-SHA256SUMS"; }
records_reset() { rm -rf "${WORK:?}/records"; mkdir -p "$WORK/records"; }
record() { # record <name> <deployment id or empty> [commit] [manifest hash]
  {
    echo "version=$VERSION"
    echo "commit=${3:-$COMMIT}"
    echo "central_manifest=${4:-$MANIFEST_HASH}"
    echo "run=1"
    if [[ -n $2 ]]; then echo "deployment_id=$2"; fi
  } > "$WORK/records/$1.record"
}
portal() { echo "{\"deploymentId\":\"$1\",\"deploymentState\":\"$2\"}" > "$WORK/portal/$1"; }

[[ $(state) == absent ]] || fail "empty Central without records should be absent"
central_all
[[ $(state) == identical ]] || { cat "$WORK/err" >&2; fail "the complete identical set should skip the deploy"; }
# N07: a differing sources JAR, javadoc JAR, POM or SBOM next to an identical main JAR
for victim in pololei/pololei-$VERSION-sources.jar pololei-epc/pololei-epc-$VERSION-javadoc.jar pololei/pololei-$VERSION.pom \
              pololei-epc/pololei-epc-$VERSION-cyclonedx.json pololei-parent/pololei-parent-$VERSION.pom; do
  central_all
  echo changed > "$WORK/central/$victim"
  [[ $(state) == exit ]] || fail "a differing $victim must fail"
  grep -q "$(basename "$victim")" "$WORK/err" || fail "the message does not name $victim"
  [[ $(cat "$WORK/central/$victim") == changed ]] || fail "$victim was changed"
done
# a missing file of the set
for victim in pololei/pololei-$VERSION-javadoc.jar pololei/pololei-$VERSION.pom pololei-epc/pololei-epc-$VERSION-cyclonedx.json; do
  central_all
  rm -f "$WORK/central/${victim:?}"
  [[ $(state) == exit ]] || fail "a set without $victim must fail"
done
central_reset

# N08: nothing visible, but an earlier run started a deployment
record started ""
[[ $(state) == exit ]] || fail "a started deployment without id must not be deployed again"
grep -q "deployment id is unknown" "$WORK/err" || fail "unknown id not reported"
record uploaded "$ID"
for pending in PENDING VALIDATING VALIDATED PUBLISHING; do
  portal "$ID" "$pending"
  [[ $(state) == exit ]] || fail "a $pending deployment must not be deployed again"
  grep -q "not published yet" "$WORK/err" || fail "$pending not reported as pending"
done
portal "$ID" PUBLISHED
[[ $(state) == published ]] || { cat "$WORK/err" >&2; fail "a published, not yet visible deployment should skip the deploy"; }
portal "$ID" FAILED
[[ $(state) == exit ]] || fail "a rejected deployment must not be deployed again automatically"
grep -q "rejected" "$WORK/err" || fail "rejection not reported"
portal "$ID" SOMETHING
[[ $(state) == exit ]] || fail "an unknown Portal state must fail"
rm -f "$WORK/portal/$ID"
[[ $(state) == exit ]] || fail "an unreachable deployment status must fail"
records_reset
record other-commit "$ID" 0000000000000000000000000000000000000000
[[ $(state) == exit ]] || fail "a record of another commit must fail"
records_reset
record other-set "$ID" "$COMMIT" 0000
[[ $(state) == exit ]] || fail "a record of another publication set must fail"
records_reset
printf 'version=0.9.0\ncommit=x\n' > "$WORK/records/old.record"
[[ $(state) == absent ]] || fail "records of other versions must not count"
records_reset

# -------------------------------------------------------------------------------- GitHub release
publish() { "$HERE/publish-github-release.sh" "$VERSION" "$R" > "$WORK/out" 2> "$WORK/err" || echo "exit"; }
expected_assets() { (cd "$R" && ls ./*.jar ./*.json SHA256SUMS "$REPORT" SCAN-SHA256SUMS | sed 's:^\./::' | LC_ALL=C sort); }
complete() { # the release has exactly the local assets with identical content and is published
  if [[ $(ls "$GH_DIR" | LC_ALL=C sort) != "$(expected_assets)" ]]; then
    fail "$1: assets differ"
    return
  fi
  for name in $(expected_assets); do
    cmp -s "$GH_DIR/$name" "$R/$name" || fail "$1: content of $name differs"
  done
  [[ ! -f $GH_DIR.draft ]] || fail "$1: release is still a draft"
}
drop() { rm -f "${GH_DIR:?}/${1:?}"; }

[[ $(publish) != exit ]] || { cat "$WORK/err" >&2; fail "creating the release failed"; }
grep -q "Created draft" "$WORK/out" && grep -q "Published release" "$WORK/out" || fail "release not created as draft and published"
complete create
cp -R "$GH_DIR" "$WORK/gh-complete"
restore() { rm -rf "${GH_DIR:?}"; cp -R "$WORK/gh-complete" "$GH_DIR"; rm -f "${GH_DIR:?}.draft"; }

# N05: each missing build artifact or manifest alone is uploaded again, nothing else changes
for victim in pololei-$VERSION.jar pololei-epc-$VERSION-sources.jar pololei-$VERSION-cyclonedx.json SHA256SUMS SCAN-SHA256SUMS; do
  restore
  drop "$victim"
  : > "$GH_LOG"
  [[ $(publish) != exit ]] || { cat "$WORK/err" >&2; fail "missing $victim was not completed"; }
  complete "missing $victim"
  [[ $(grep -c "release upload" "$GH_LOG") -eq 1 ]] || fail "missing $victim: other assets were uploaded again"
done
# a missing report whose manifest is there: the original report is lost, check manually
restore
drop "$REPORT"
[[ $(publish) == exit ]] || fail "a lost report with its manifest present must fail"
# report and its manifest missing (interrupted before the report): both are uploaded
restore
drop "$REPORT"
drop SCAN-SHA256SUMS
[[ $(publish) != exit ]] || { cat "$WORK/err" >&2; fail "missing report and manifest were not completed"; }
complete "missing report and manifest"
# a draft with some build artifacts only (no manifests, no report)
restore
drop SHA256SUMS
drop "$REPORT"
drop SCAN-SHA256SUMS
drop "pololei-epc-$VERSION.jar"
touch "$GH_DIR.draft"
[[ $(publish) != exit ]] || { cat "$WORK/err" >&2; fail "a draft without manifests was not completed"; }
complete "draft without manifests"
# a new run with another report does not block the original release; the original stays
restore
cp "$R/$REPORT" "$WORK/report.orig"
cp "$R/SCAN-SHA256SUMS" "$WORK/scan.orig"
echo "<html>report of a later scan</html>" > "$R/$REPORT"
(cd "$R" && shasum -a 256 "$REPORT" > SCAN-SHA256SUMS)
: > "$GH_LOG"
[[ $(publish) != exit ]] || { cat "$WORK/err" >&2; fail "a new report blocked the completion"; }
cmp -s "$GH_DIR/$REPORT" "$WORK/report.orig" || fail "the original report was replaced"
grep -q "release upload" "$GH_LOG" && fail "a complete release was changed"
# ... also when the release misses a JAR
drop "pololei-$VERSION.jar"
[[ $(publish) != exit ]] || fail "a new report blocked adding a missing JAR"
cmp -s "$GH_DIR/pololei-$VERSION.jar" "$R/pololei-$VERSION.jar" || fail "missing JAR not restored"
cmp -s "$GH_DIR/$REPORT" "$WORK/report.orig" || fail "the original report was replaced"
cp "$WORK/report.orig" "$R/$REPORT"
cp "$WORK/scan.orig" "$R/SCAN-SHA256SUMS"
# a complete release: nothing to do
restore
: > "$GH_LOG"
[[ $(publish) != exit ]] || fail "complete release failed"
grep -q "release upload\|release create\|release edit" "$GH_LOG" && fail "complete release was changed"

# N06: changed content of present assets is found and not replaced
for victim in pololei-$VERSION.jar pololei-epc-$VERSION-cyclonedx.json; do
  restore
  echo tampered > "$GH_DIR/$victim"
  : > "$GH_LOG"
  [[ $(publish) == exit ]] || fail "a changed $victim with an unchanged manifest must fail"
  [[ $(cat "$GH_DIR/$victim") == tampered ]] || fail "the changed $victim was replaced"
  grep -q "release download.*$victim" "$GH_LOG" || fail "the content of $victim was not downloaded"
done
restore
echo tampered > "$GH_DIR/$REPORT"
[[ $(publish) == exit ]] || fail "a report that does not match SCAN-SHA256SUMS must fail"
restore
drop SCAN-SHA256SUMS
echo tampered > "$GH_DIR/$REPORT"
[[ $(publish) == exit ]] || fail "an unverifiable report must fail"
restore
echo "other" > "$GH_DIR/SHA256SUMS"
[[ $(publish) == exit ]] || fail "differing SHA256SUMS must fail"
restore
echo "extra" > "$GH_DIR/unexpected.jar"
[[ $(publish) == exit ]] || fail "an unexpected asset must fail"
# a local file that does not match its manifest
restore
cp "$R/pololei-$VERSION.jar" "$WORK/jar.orig"
echo broken > "$R/pololei-$VERSION.jar"
[[ $(publish) == exit ]] || fail "local files that do not match SHA256SUMS must fail"
cp "$WORK/jar.orig" "$R/pololei-$VERSION.jar"
# a tag on another commit
restore
git -c user.name=t -c user.email=t@example.org commit -q --allow-empty -m two
git tag "$VERSION"
[[ $(publish) == exit ]] || fail "a tag on another commit must fail"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Release publishing: all checks passed."

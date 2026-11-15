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

# Creates the GitHub release of a version, or completes an existing one after an interrupted or
# repeated run. The release is created as a draft and published only when all assets are there and
# verified, so an incomplete release is never public.
#
# The assets are the reproducible build artifacts listed in SHA256SUMS (JARs, SBOMs), SHA256SUMS,
# and the OWASP report with its own manifest SCAN-SHA256SUMS. On an existing release
#   - every present build artifact is downloaded and compared with the local SHA256SUMS, a present
#     SHA256SUMS must equal the local one; a difference fails, nothing is replaced
#   - missing build artifacts and a missing SHA256SUMS are uploaded
#   - a present report is the original one and stays; it must match a present SCAN-SHA256SUMS. A
#     report of this run that differs is not published. A missing report is uploaded with its
#     manifest; if only one of report and manifest is there and they cannot be verified, the run
#     fails for a manual check
#   - assets with other names fail the run
# Fails also if the tag points to another commit than EXPECTED_COMMIT. The upload order is: build
# artifacts, SHA256SUMS, report, SCAN-SHA256SUMS.
#   EXPECTED_COMMIT=<sha> publish-github-release.sh <version> <release-files-directory>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <release-files-directory>" >&2
  exit 2
fi
VERSION=$1
DIR=$2
: "${EXPECTED_COMMIT:?EXPECTED_COMMIT is required}"
REPORT=pololei-$VERSION-dependency-check-report.html

fail() {
  echo "::error::$*" >&2
  exit 1
}

if git rev-parse -q --verify "refs/tags/$VERSION" > /dev/null; then
  tag_commit=$(git rev-list -n 1 "refs/tags/$VERSION")
  if [[ $tag_commit != "$EXPECTED_COMMIT" ]]; then
    fail "Tag $VERSION points to $tag_commit, not to $EXPECTED_COMMIT"
  fi
fi

[[ -f $DIR/SHA256SUMS && -f $DIR/RELEASE-NOTES.md ]] || fail "SHA256SUMS or RELEASE-NOTES.md missing in $DIR"
# the local files must match their manifests
(cd "$DIR" && shasum -a 256 -c --quiet SHA256SUMS) || fail "The release files in $DIR do not match SHA256SUMS"
if [[ -f $DIR/SCAN-SHA256SUMS ]]; then
  (cd "$DIR" && shasum -a 256 -c --quiet SCAN-SHA256SUMS) || fail "The report in $DIR does not match SCAN-SHA256SUMS"
fi
build=()
while read -r _ name; do
  build+=("${name#\*}")
done < "$DIR/SHA256SUMS"
[[ ${#build[@]} -gt 0 ]] || fail "SHA256SUMS in $DIR lists no files"

sha256() { shasum -a 256 | cut -c1-64; }
local_hash() { awk -v name="$2" '$2 == name || $2 == "*" name { print $1 }' "$1"; }
remote_content() { gh release download "$VERSION" --pattern "$1" --output -; }
remote_hash() { remote_content "$1" | sha256; }
upload() {
  gh release upload "$VERSION" "$DIR/$1"
  echo "Uploaded $1"
}

if gh release view "$VERSION" > /dev/null 2>&1; then
  echo "Release $VERSION exists: checking and completing it"
else
  gh release create "$VERSION" --draft --target "$EXPECTED_COMMIT" --title "$VERSION" --notes-file "$DIR/RELEASE-NOTES.md"
  echo "Created draft release $VERSION"
fi
existing=$(gh release view "$VERSION" --json assets --jq '.assets[].name')
has() { grep -qxF "$1" <<< "$existing"; }

# assets that this script does not create
while IFS= read -r name; do
  [[ -z $name ]] && continue
  case $name in
    SHA256SUMS | SCAN-SHA256SUMS | "$REPORT") ;;
    *) printf '%s\n' "${build[@]}" | grep -qxF "$name" || fail "Release $VERSION has an unexpected asset $name; check it manually" ;;
  esac
done <<< "$existing"

# the build artifacts: compare the content of every present one, upload the missing ones
if has SHA256SUMS && [[ $(remote_content SHA256SUMS) != "$(cat "$DIR/SHA256SUMS")" ]]; then
  fail "SHA256SUMS of the existing release $VERSION differ from this build"
fi
missing=()
for name in "${build[@]}"; do
  if has "$name"; then
    [[ $(remote_hash "$name") == "$(local_hash "$DIR/SHA256SUMS" "$name")" ]] \
      || fail "$name of the existing release $VERSION differs from this build; it is not replaced"
  else
    missing+=("$name")
  fi
done
for name in ${missing[@]+"${missing[@]}"}; do
  upload "$name"
done
has SHA256SUMS || upload SHA256SUMS

# the OWASP report: the first published one stays the report of the release
if has "$REPORT" && has SCAN-SHA256SUMS; then
  [[ $(remote_hash "$REPORT") == "$(remote_content SCAN-SHA256SUMS | awk -v name="$REPORT" '$2 == name || $2 == "*" name { print $1 }')" ]] \
    || fail "$REPORT of the existing release $VERSION does not match its SCAN-SHA256SUMS"
  if [[ -f $DIR/$REPORT && $(sha256 < "$DIR/$REPORT") != "$(remote_hash "$REPORT")" ]]; then
    echo "The report of this run differs from the one of the release; the original report stays"
  fi
elif has "$REPORT"; then
  [[ -f $DIR/$REPORT && $(sha256 < "$DIR/$REPORT") == "$(remote_hash "$REPORT")" ]] \
    || fail "$REPORT of the existing release $VERSION has no SCAN-SHA256SUMS and differs from the report of this run; check it manually"
  upload SCAN-SHA256SUMS
elif has SCAN-SHA256SUMS; then
  fail "Release $VERSION has a SCAN-SHA256SUMS but no $REPORT; check it manually"
elif [[ -f $DIR/$REPORT ]]; then
  upload "$REPORT"
  upload SCAN-SHA256SUMS
else
  echo "No OWASP report for release $VERSION"
fi

# publish the draft only when everything is there
existing=$(gh release view "$VERSION" --json assets --jq '.assets[].name')
for name in "${build[@]}" SHA256SUMS; do
  has "$name" || fail "Asset $name of release $VERSION is still missing"
done
if [[ $(gh release view "$VERSION" --json isDraft --jq .isDraft) == true ]]; then
  gh release edit "$VERSION" --draft=false
  echo "Published release $VERSION"
else
  echo "Completed existing release $VERSION"
fi

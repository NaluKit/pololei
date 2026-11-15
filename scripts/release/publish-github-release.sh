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

# Creates the GitHub release of a version, or completes an existing one after an interrupted run:
# missing assets are added, existing ones are never replaced. Fails if the tag points to another
# commit than EXPECTED_COMMIT, or if the SHA256SUMS of the existing release differ from the local
# ones.
#   EXPECTED_COMMIT=<sha> publish-github-release.sh <version> <release-files-directory>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <release-files-directory>" >&2
  exit 2
fi
VERSION=$1
DIR=$2
: "${EXPECTED_COMMIT:?EXPECTED_COMMIT is required}"

if git rev-parse -q --verify "refs/tags/$VERSION" > /dev/null; then
  tag_commit=$(git rev-list -n 1 "refs/tags/$VERSION")
  if [[ $tag_commit != "$EXPECTED_COMMIT" ]]; then
    echo "::error::Tag $VERSION points to $tag_commit, not to $EXPECTED_COMMIT" >&2
    exit 1
  fi
fi

assets=()
for file in "$DIR"/*.jar "$DIR"/*.json "$DIR"/*.html "$DIR"/SHA256SUMS; do
  [[ -f $file ]] && assets+=("$file")
done

if gh release view "$VERSION" > /dev/null 2>&1; then
  if [[ $(gh release download "$VERSION" --pattern SHA256SUMS --output -) != "$(cat "$DIR/SHA256SUMS")" ]]; then
    echo "::error::SHA256SUMS of the existing release $VERSION differ from this build" >&2
    exit 1
  fi
  existing=$(gh release view "$VERSION" --json assets --jq '.assets[].name')
  for file in "${assets[@]}"; do
    if ! grep -qxF "$(basename "$file")" <<< "$existing"; then
      gh release upload "$VERSION" "$file"
    fi
  done
  echo "Completed existing release $VERSION"
else
  gh release create "$VERSION" --target "$EXPECTED_COMMIT" --title "$VERSION" \
    --notes-file "$DIR/RELEASE-NOTES.md" "${assets[@]}"
  echo "Created release $VERSION"
fi

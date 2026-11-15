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

# Tells whether the version is already on Maven Central, so that a re-run of the release does not
# upload it twice. Compares the main JAR of every published module (as collected by
# collect-release-files.sh) with the one on Central:
#   absent      no module is on Central: deploy
#   identical   all modules are on Central with the same SHA-256: skip the deploy
# Exit code 1 if a JAR on Central differs, or only some modules are there; never overwrite, find
# out manually what was published. A deployment that Central has accepted but not yet made
# visible reads as "absent"; deploying it again is then rejected by Central, which fails loudly.
#
# CENTRAL_BASE_URL overrides https://repo1.maven.org/maven2 (for tests).
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <release-files-directory>" >&2
  exit 2
fi
VERSION=$1
DIR=$2
BASE=${CENTRAL_BASE_URL:-https://repo1.maven.org/maven2}
GROUP_PATH=io/github/nalukit

present=0
absent=0
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
shopt -s nullglob
for jar in "$DIR"/*-"$VERSION".jar; do
  name=$(basename "$jar")
  module=${name%-"$VERSION".jar}
  code=$(curl -sS -o "$tmp" -w '%{http_code}' "$BASE/$GROUP_PATH/$module/$VERSION/$name")
  case $code in
    200)
      if [[ $(shasum -a 256 "$tmp" | cut -d' ' -f1) != "$(shasum -a 256 "$jar" | cut -d' ' -f1)" ]]; then
        echo "::error::$name on Maven Central differs from the build of this commit; not deploying" >&2
        exit 1
      fi
      present=$((present + 1))
      ;;
    404) absent=$((absent + 1)) ;;
    *)
      echo "::error::Unexpected HTTP status $code for $name on Maven Central" >&2
      exit 1
      ;;
  esac
done
if [[ $((present + absent)) -eq 0 ]]; then
  echo "No JARs of version $VERSION in $DIR" >&2
  exit 1
fi
if [[ $present -gt 0 && $absent -gt 0 ]]; then
  echo "::error::Version $VERSION is only partly on Maven Central ($present of $((present + absent)) modules); clarify manually" >&2
  exit 1
fi
if [[ $present -gt 0 ]]; then
  echo identical
else
  echo absent
fi

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

#
# Checks that a version is consistent before it is tagged and published:
#   scripts/release/check-release.sh [--allow-snapshot] [--repository owner/name] <version>
#
# A release version (x.y.z) needs a section '## x.y.z' in CHANGELOG.md and, with --repository, links to a
# pololei repository in pom.xml, README.md and SECURITY.md that all point to that GitHub repository. A SNAPSHOT version fails, unless
# --allow-snapshot is given: then it is a development build and the release checks are skipped. The
# release dry run of the CI uses --allow-snapshot, the release job does not.
#
# POLOLEI_ROOT overrides the project directory (used by check-release-test.sh).
#
set -euo pipefail

usage() {
  echo "usage: $0 [--allow-snapshot] [--repository owner/name] <version>" >&2
  exit 2
}

allow_snapshot=false
repository=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --allow-snapshot) allow_snapshot=true; shift ;;
    --repository) [[ $# -ge 2 ]] || usage; repository="$2"; shift 2 ;;
    -*) usage ;;
    *) break ;;
  esac
done
[[ $# -eq 1 && -n "$1" ]] || usage
version="$1"

if [[ "$version" == *-SNAPSHOT ]]; then
  if [[ "$allow_snapshot" == true ]]; then
    echo "Version $version is a development snapshot; release checks skipped."
    exit 0
  fi
  echo "Version $version is a SNAPSHOT and cannot be released." >&2
  exit 1
fi
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Version $version is not a valid release version (x.y.z)." >&2
  exit 1
fi

root="${POLOLEI_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
if ! grep -qE "^## \[?${version//./\\.}\]?([[:space:]]|$)" "$root/CHANGELOG.md"; then
  echo "CHANGELOG.md has no section '## $version'." >&2
  exit 1
fi

if [[ -n "$repository" ]]; then
  # every link to a pololei repository (project, SCM, issues, badge, advisories) must name the
  # repository that publishes the release; GitHub names are case-insensitive
  expected="$(echo "$repository" | tr '[:upper:]' '[:lower:]')"
  if ! grep -qiF "<url>https://github.com/$repository</url>" "$root/pom.xml"; then
    echo "pom.xml has no project url https://github.com/$repository." >&2
    exit 1
  fi
  for file in pom.xml README.md SECURITY.md; do
    [[ -f "$root/$file" ]] || continue
    while read -r link; do
      found="$(echo "${link#github.com?}" | sed 's/\.git$//' | tr '[:upper:]' '[:lower:]')"
      if [[ "$found" != "$expected" ]]; then
        echo "$file links to github.com/$found instead of $repository." >&2
        exit 1
      fi
    done < <(grep -oiE 'github\.com[:/][A-Za-z0-9_.-]+/pololei[A-Za-z0-9_.-]*' "$root/$file" || true)
  done
fi
echo "Release $version is consistent."

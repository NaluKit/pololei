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
# Tests check-release.sh with a temporary CHANGELOG.md and pom.xml:
#   scripts/release/check-release-test.sh
#
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/check-release.sh"
POLOLEI_ROOT="$(mktemp -d)"
export POLOLEI_ROOT
trap 'rm -rf "$POLOLEI_ROOT"' EXIT

printf '# Changelog\n\n## Unreleased\n\n## 1.2.0\n\n- first release\n' > "$POLOLEI_ROOT/CHANGELOG.md"
cat > "$POLOLEI_ROOT/pom.xml" <<'POM'
<project>
  <url>https://github.com/nalukit/pololei</url>
  <scm>
    <connection>scm:git:https://github.com/NaluKit/pololei.git</connection>
    <developerConnection>scm:git:git@github.com:NaluKit/pololei.git</developerConnection>
    <url>https://github.com/NaluKit/pololei</url>
  </scm>
  <issueManagement>
    <url>https://github.com/nalukit/pololei/issues</url>
  </issueManagement>
</project>
POM

printf '[Build](https://github.com/NaluKit/pololei/actions) successor of [iban4g](https://github.com/nalukit/iban4g)\n' > "$POLOLEI_ROOT/README.md"

failures=0
expect() {
  local expected="$1"
  shift
  if "$script" "$@" > /dev/null 2>&1; then actual=pass; else actual=fail; fi
  if [[ "$actual" != "$expected" ]]; then
    echo "FAILED: check-release.sh $* should $expected" >&2
    failures=$((failures + 1))
  fi
}

expect pass 1.2.0                                                # release with changelog section
expect pass --repository NaluKit/pololei 1.2.0                   # links match, case-insensitive
expect fail --repository NaluKit/pololei-incubator 1.2.0         # links point elsewhere
expect fail 1.3.0                                                # no changelog section
expect fail 1.2                                                  # not x.y.z
expect fail 1.2.0-SNAPSHOT                                       # snapshot in the release job
expect pass --allow-snapshot 1.3.0-SNAPSHOT                      # development build
expect pass --allow-snapshot --repository other/repo HEAD-SNAPSHOT
expect fail --allow-snapshot 1.3.0                               # a release candidate is checked
expect fail                                                      # no version
printf '[Build](https://github.com/NaluKit/pololei-incubator/actions)\n' > "$POLOLEI_ROOT/README.md"
expect fail --repository NaluKit/pololei 1.2.0                   # README links elsewhere
expect pass 1.2.0                                                # without --repository links are not checked

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed" >&2
  exit 1
fi
echo "check-release.sh: all checks passed"

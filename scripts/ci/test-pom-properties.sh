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

# Tests check-pom-properties.sh with POMs with and without a duplicate property.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "${WORK:?}"' EXIT
failed=0

printf '<project>\n  <properties>\n    <a.version>1</a.version>\n    <b.version>2</b.version>\n  </properties>\n</project>\n' > "$WORK/ok.xml"
printf '<project>\n  <properties>\n    <a.version>1</a.version>\n    <b.version>2</b.version>\n    <a.version>3</a.version>\n  </properties>\n</project>\n' > "$WORK/duplicate.xml"
# the same name in two different blocks (for example a profile) is not a duplicate
printf '<project>\n  <properties>\n    <a.version>1</a.version>\n  </properties>\n  <profiles><profile>\n    <properties>\n      <a.version>2</a.version>\n    </properties>\n  </profile></profiles>\n</project>\n' > "$WORK/profile.xml"

"$HERE/check-pom-properties.sh" "$WORK/ok.xml" > /dev/null 2>&1 || { echo "FAIL: ok.xml" >&2; failed=1; }
"$HERE/check-pom-properties.sh" "$WORK/profile.xml" > /dev/null 2>&1 || { echo "FAIL: profile.xml" >&2; failed=1; }
if "$HERE/check-pom-properties.sh" "$WORK/duplicate.xml" > /dev/null 2>&1; then
  echo "FAIL: a duplicate property was not detected" >&2
  failed=1
fi

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "POM property check: all checks passed."

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

# Tests check-pinned-refs.sh with workflow files that are pinned and not pinned.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
SHA=0123456789abcdef0123456789abcdef01234567
DIGEST=sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
failed=0

expect() {
  local expected=$1 name=$2
  if "$HERE/check-pinned-refs.sh" "$WORK/$name" > /dev/null 2>&1; then actual=0; else actual=1; fi
  if [[ $actual -ne $expected ]]; then
    echo "FAIL: $name (expected status $expected, got $actual)" >&2
    failed=1
  fi
}

printf '      - uses: actions/checkout@%s # v7\n      - uses: ./local\n    image: x/y@%s # latest\n' "$SHA" "$DIGEST" > "$WORK/ok.yaml"
printf '      - uses: actions/checkout@v7\n' > "$WORK/tag.yaml"
printf '      - uses: actions/checkout@main\n' > "$WORK/branch.yaml"
printf '      - uses: actions/checkout@0123abc\n' > "$WORK/short.yaml"
printf '        image: x/y:latest\n' > "$WORK/latest.yaml"
printf '        image: x/y\n' > "$WORK/nodigest.yaml"
expect 0 ok.yaml
for name in tag branch short latest nodigest; do
  expect 1 "$name.yaml"
done

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Pinned references: all checks passed."

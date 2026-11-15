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

# Fails if a property is defined more than once in the <properties> blocks of a POM. Maven uses the
# last definition, so an earlier one would show a version that is not in effect. Without arguments
# the parent POM is checked.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
pom=${1:-$ROOT/pom.xml}

duplicates=$(awk '
  /<properties>/ { inside = 1; delete seen; next }
  /<\/properties>/ { inside = 0; next }
  inside && /^[[:space:]]*<[A-Za-z0-9._-]+>/ {
    name = $0
    sub(/^[[:space:]]*</, "", name)
    sub(/>.*$/, "", name)
    if (seen[name]++) print name
  }
' "$pom")
if [[ -n $duplicates ]]; then
  echo "$pom defines properties more than once:" >&2
  echo "$duplicates" >&2
  exit 1
fi
echo "No duplicate properties in $pom."

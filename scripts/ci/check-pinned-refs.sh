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

# Fails if a workflow uses an external action without a full 40 character commit SHA, or a
# container image without a digest. Local actions (./...) are fine. Run without arguments for
# .github/workflows, or pass workflow files.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
if [[ $# -gt 0 ]]; then
  files=("$@")
else
  files=("$ROOT"/.github/workflows/*.y*ml)
fi

status=0
for file in "${files[@]}"; do
  while IFS= read -r line; do
    ref=$(sed -E 's/^[[:space:]-]*(uses|image):[[:space:]]*//; s/[[:space:]]+#.*$//; s/["'\'']//g' <<< "$line")
    case $line in
      *uses:*)
        [[ $ref == ./* ]] && continue
        [[ $ref =~ @[0-9a-f]{40}$ ]] || { echo "$file: action not pinned to a commit SHA: $ref" >&2; status=1; }
        ;;
      *image:*)
        [[ $ref =~ @sha256:[0-9a-f]{64}$ ]] || { echo "$file: image not pinned to a digest: $ref" >&2; status=1; }
        ;;
    esac
  done < <(grep -E '^[[:space:]-]*(uses|image):' "$file" || true)
done
if [[ $status -eq 0 ]]; then
  echo "All external actions and images are pinned."
fi
exit $status

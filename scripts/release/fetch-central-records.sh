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

# Downloads the deployment records of a version that earlier workflow runs kept as artifacts
# (central-deployment-<version>-<run>-<attempt>-<stage>) into a directory, for central-state.sh.
# Needs GH_TOKEN with actions:read and GITHUB_REPOSITORY. Fails if the artifacts cannot be listed
# or a record cannot be downloaded, because a missing record could cause a second upload.
#   fetch-central-records.sh <version> <directory>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <directory>" >&2
  exit 2
fi
VERSION=$1
OUT=$2
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
mkdir -p "$OUT"

prefix="central-deployment-$VERSION-"
listing=$(gh api --paginate "repos/$GITHUB_REPOSITORY/actions/artifacts?per_page=100" \
  --jq ".artifacts[] | select(.name | startswith(\"$prefix\")) | \"\(.id) \(.expired) \(.workflow_run.id) \(.name)\"")
count=0
while read -r id expired run name; do
  [[ -z $id ]] && continue
  if [[ $expired == true ]]; then
    echo "::error::The deployment record $name (run $run) has expired; check the Central Portal manually" >&2
    exit 1
  fi
  gh run download "$run" --repo "$GITHUB_REPOSITORY" --name "$name" --dir "$OUT/$name"
  count=$((count + 1))
done <<< "$listing"
echo "Fetched $count deployment record(s) of $VERSION" >&2

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

# Tells whether the version is already on Maven Central, so that a re-run of the release never
# uploads it twice. Prints the state:
#   absent      nothing of the version is on Central and no earlier run started a deployment of
#               it: deploy
#   identical   every file of the publication set is on Central with the same SHA-256: skip the
#               deploy
#   published   nothing is visible yet, but the deployment of an earlier run is published on the
#               Central Portal and becomes visible soon: skip the deploy
# The publication set is central/CENTRAL-SHA256SUMS of the release files (collect-release-files.sh):
# the main, sources and javadoc JARs, the POMs and the SBOMs of every module and the parent POM.
#
# Exit code 1, without a state, whenever a deploy must not run and the release cannot be completed
# yet: a file on Central differs, only part of the set is visible, a deployment of an earlier run is
# still pending (validating, validated and waiting for publishing, publishing) or was rejected, or
# an earlier run started an upload whose deployment id or state is unknown. Nothing is ever
# overwritten; the message says what to check.
#
# The deployment records of earlier runs (written by the workflow before and after the upload,
# key=value lines: version, commit, central_manifest, run, deployment_id) are read from the
# optional third argument, a directory searched recursively for *.record files. A record of the
# version with another commit or another publication set fails.
#
#   EXPECTED_COMMIT=<sha> central-state.sh <version> <release-files-directory> [<records-directory>]
#
# CENTRAL_BASE_URL overrides https://repo1.maven.org/maven2 and CENTRAL_PORTAL_URL
# https://central.sonatype.com (for tests). The Portal status needs CENTRAL_USERNAME and
# CENTRAL_PASSWORD (the user token).
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <version> <release-files-directory> [<records-directory>]" >&2
  exit 2
fi
VERSION=$1
DIR=$2
RECORDS=${3:-}
BASE=${CENTRAL_BASE_URL:-https://repo1.maven.org/maven2}
PORTAL=${CENTRAL_PORTAL_URL:-https://central.sonatype.com}
GROUP_PATH=io/github/nalukit
MANIFEST=$DIR/central/CENTRAL-SHA256SUMS

fail() {
  echo "::error::$*" >&2
  exit 1
}

[[ -s $MANIFEST ]] || fail "No publication set $MANIFEST; run collect-release-files.sh first"
manifest_hash=$(shasum -a 256 < "$MANIFEST" | cut -c1-64)

# 1. what is visible on Maven Central
identical=0
missing=0
differing=()
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
while read -r hash path; do
  path=${path#\*}
  artifact=${path%%/*}
  name=${path#*/}
  code=$(curl -sS -o "$tmp" -w '%{http_code}' "$BASE/$GROUP_PATH/$artifact/$VERSION/$name")
  case $code in
    200)
      if [[ $(shasum -a 256 < "$tmp" | cut -c1-64) == "$hash" ]]; then
        identical=$((identical + 1))
      else
        differing+=("$name")
      fi
      ;;
    404) missing=$((missing + 1)) ;;
    *) fail "Unexpected HTTP status $code for $name on Maven Central" ;;
  esac
done < "$MANIFEST"
total=$((identical + missing + ${#differing[@]}))
if [[ ${#differing[@]} -gt 0 ]]; then
  fail "On Maven Central differs from the build of this commit: ${differing[*]}; not deploying, clarify manually"
fi
if [[ $identical -eq $total ]]; then
  echo identical
  exit 0
fi
if [[ $identical -gt 0 ]]; then
  fail "Version $VERSION is only partly visible on Maven Central ($identical of $total files); if a deployment is being published, wait and re-run, otherwise clarify manually"
fi

# 2. nothing visible: did an earlier run start a deployment?
ids=()
started=0
if [[ -n $RECORDS && -d $RECORDS ]]; then
  while IFS= read -r record; do
    value() { sed -n "s/^$1=//p" "$record" | head -n 1; }
    [[ $(value version) == "$VERSION" ]] || continue
    started=$((started + 1))
    [[ $(value commit) == "${EXPECTED_COMMIT:?EXPECTED_COMMIT is required with deployment records}" ]] \
      || fail "An earlier deployment of $VERSION ($record) was made from commit $(value commit), not $EXPECTED_COMMIT"
    [[ $(value central_manifest) == "$manifest_hash" ]] \
      || fail "An earlier deployment of $VERSION ($record) had another publication set"
    id=$(value deployment_id)
    if [[ -n $id ]] && ! printf '%s\n' ${ids[@]+"${ids[@]}"} | grep -qxF "$id"; then
      ids+=("$id")
    fi
  done < <(find "$RECORDS" -type f -name '*.record' | LC_ALL=C sort)
fi
if [[ $started -eq 0 ]]; then
  echo absent
  exit 0
fi
if [[ ${#ids[@]} -eq 0 ]]; then
  fail "An earlier run started the deployment of $VERSION, but its deployment id is unknown, so Central may have accepted it; check $PORTAL/publishing/deployments. Not deploying again. If nothing was uploaded, delete the deployment records of $VERSION (artifacts central-deployment-$VERSION-*) and re-run"
fi

# 3. the state of those deployments on the Central Portal
: "${CENTRAL_USERNAME:?CENTRAL_USERNAME is required to check deployments}"
: "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is required to check deployments}"
token=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')
published=0
pending=()
rejected=()
for id in "${ids[@]}"; do
  code=$(curl -sS -X POST -o "$tmp" -w '%{http_code}' -H "Authorization: Bearer $token" "$PORTAL/api/v1/publisher/status?id=$id")
  [[ $code == 200 ]] || fail "Unexpected HTTP status $code for deployment $id on the Central Portal; state unknown, not deploying again"
  state=$(sed -nE 's/.*"deploymentState"[[:space:]]*:[[:space:]]*"([A-Z_]+)".*/\1/p' "$tmp" | head -n 1)
  echo "Deployment $id of $VERSION: ${state:-unknown}" >&2
  case $state in
    PUBLISHED) published=$((published + 1)) ;;
    PENDING | VALIDATING | VALIDATED | PUBLISHING) pending+=("$id ($state)") ;;
    FAILED) rejected+=("$id") ;;
    *) fail "Unknown state '${state}' of deployment $id on the Central Portal; not deploying again" ;;
  esac
done
if [[ $published -gt 0 ]]; then
  echo published
  exit 0
fi
if [[ ${#pending[@]} -gt 0 ]]; then
  fail "Deployment of $VERSION is not published yet: ${pending[*]}; publish it on $PORTAL/publishing/deployments or wait, then re-run"
fi
fail "Deployment of $VERSION was rejected by Central: ${rejected[*]}; see $PORTAL/publishing/deployments. Not deploying again automatically; after dropping it there, delete the deployment records of $VERSION (artifacts central-deployment-$VERSION-*) and re-run"

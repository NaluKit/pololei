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
# Regenerates pololei/src/main/java/io/github/nalukit/pololei/registry/IbanRegistryData.java
# from the SWIFT IBAN registry.
#
# The registry is published by SWIFT as "IBAN Registry (TXT)" on
# https://www.swift.com/standards/data-standards/iban-international-bank-account-number
# It cannot be downloaded by script, so download it in a browser first.
#
# Release and SHA-256 of the expected file are pinned in scripts/registry/registry-source.properties;
# a file with another SHA-256 is rejected. For a new registry release, update that file first.
#
# usage: scripts/registry/generate-registry.sh <registry.txt>
#
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <registry.txt>" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUTPUT="$ROOT/pololei/src/main/java/io/github/nalukit/pololei/registry/IbanRegistryData.java"
PINS="$ROOT/scripts/registry/registry-source.properties"
RELEASE="$(sed -n 's/^release=//p' "$PINS")"
SHA256="$(sed -n 's/^sha256=//p' "$PINS")"

java "$ROOT/scripts/registry/IbanRegistryGenerator.java" "$1" "$RELEASE" "$SHA256" "$OUTPUT"
echo "now run: mvn -f \"$ROOT/pom.xml\" verify"

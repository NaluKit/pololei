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

# Collects the evidence of a release into one directory, for the GitHub release and for anyone who
# rebuilds the release to compare it:
#   <module>-<version>.jar, -sources.jar, -javadoc.jar   the JARs of every published module; all
#                                                        three are required
#   <module>-<version>-cyclonedx.json                    the SBOM (CycloneDX) of every module
#   SHA256SUMS                                           SHA-256 of the JARs and SBOMs: the
#                                                        reproducible build artifacts
#   pololei-<version>-dependency-check-report.html       the OWASP report, if one is passed
#   SCAN-SHA256SUMS                                      SHA-256 of the report; a report is not
#                                                        reproducible, so it has its own manifest
#   RELEASE-NOTES.md                                     the section of the version in CHANGELOG.md
#   central/<artifactId>/<file>, central/CENTRAL-SHA256SUMS
#                                                        the files that the deploy publishes on Maven
#                                                        Central (JARs, flattened POMs, SBOMs) and
#                                                        their SHA-256, for central-state.sh
#
# The directory must not exist or be empty, so files of another version or an earlier run cannot
# be released by accident.
#
# Run it after a build with the release profile of the same version:
#   mvn package -DskipTests -Dgpg.skip -P release -Drevision=<version> \
#       -Dproject.build.outputTimestamp=<commit time, UTC>
#   scripts/release/collect-release-files.sh <version> <directory> [<dependency-check-report.html>]
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <version> <directory> [<dependency-check-report.html>]" >&2
  exit 2
fi
VERSION=$1
OUT=$2
REPORT=${3:-}
if [[ -n $REPORT && ! -f $REPORT ]]; then
  echo "Missing OWASP report $REPORT" >&2
  exit 1
fi
ROOT=$(cd "$(dirname "$0")/../.." && pwd)

# BEGIN relink_to_tag
# Points relative repository links, with or without anchor, at the files of the release tag. Links
# with a scheme (https:, mailto:), protocol-relative and root-relative links, and anchors stay as they are.
relink_to_tag() {
  sed -E "s#\]\(([^):#/][^):]*)\)#](https://github.com/NaluKit/pololei/blob/$1/\1)#g"
}
# END relink_to_tag

if [[ -d $OUT && -n $(ls -A "$OUT") ]]; then
  echo "Directory $OUT is not empty; use an empty directory" >&2
  exit 1
fi
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

# The modules of the reactor; the corpus tests are never published.
modules=$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' "$ROOT/pom.xml" | grep -v -e '^test-pololei-corpus$')
CENTRAL="$OUT/central"
# central_file <source> <artifactId> <file name>: a file that the deploy publishes on Central
central_file() {
  if [[ ! -f $1 ]]; then
    echo "Missing $1; build version $VERSION with the release profile first" >&2
    exit 1
  fi
  mkdir -p "$CENTRAL/$2"
  cp "$1" "$CENTRAL/$2/$3"
}
count=0
for module in $modules; do
  target="$ROOT/$module/target"
  for suffix in "" -sources -javadoc; do
    jar="$target/$module-$VERSION$suffix.jar"
    if [[ ! -f $jar ]]; then
      echo "Missing $jar; build version $VERSION with the release profile first" >&2
      exit 1
    fi
    cp "$jar" "$OUT/"
    central_file "$jar" "$module" "$module-$VERSION$suffix.jar"
    count=$((count + 1))
  done
  if [[ ! -f $target/bom.json ]]; then
    echo "Missing SBOM $target/bom.json" >&2
    exit 1
  fi
  cp "$target/bom.json" "$OUT/$module-$VERSION-cyclonedx.json"
  central_file "$target/bom.json" "$module" "$module-$VERSION-cyclonedx.json"
  # the flatten plugin writes the POM that is deployed
  central_file "$ROOT/$module/.flattened-pom.xml" "$module" "$module-$VERSION.pom"
done
central_file "$ROOT/.flattened-pom.xml" pololei-parent "pololei-parent-$VERSION.pom"
if [[ -f $ROOT/target/bom.json ]]; then
  cp "$ROOT/target/bom.json" "$OUT/pololei-parent-$VERSION-cyclonedx.json"
  central_file "$ROOT/target/bom.json" pololei-parent "pololei-parent-$VERSION-cyclonedx.json"
fi

(cd "$OUT" && find . -maxdepth 1 -type f \( -name '*.jar' -o -name '*-cyclonedx.json' \) | sed 's:^\./::' | LC_ALL=C sort |
  xargs shasum -a 256 > SHA256SUMS)
(cd "$CENTRAL" && find . -mindepth 2 -type f | sed 's:^\./::' | LC_ALL=C sort | xargs shasum -a 256 > CENTRAL-SHA256SUMS)

if [[ -n $REPORT ]]; then
  cp "$REPORT" "$OUT/pololei-$VERSION-dependency-check-report.html"
  (cd "$OUT" && shasum -a 256 "pololei-$VERSION-dependency-check-report.html" > SCAN-SHA256SUMS)
fi

# The release notes: the section '## [<version>] - <date>' of CHANGELOG.md up to the next section.
# Link definitions at the end of the file are left out, and relative links point to the files of
# the release tag, because the release page has no base path.
notes=$(awk -v version="$VERSION" '
  index($0, "## [" version "] - ") == 1 { inside = 1; next }
  inside && /^## \[/ { exit }
  inside && /^\[[^]]+\]: / { next }
  inside { print }
' "$ROOT/CHANGELOG.md" | relink_to_tag "$VERSION")
if [[ -z ${notes//[[:space:]]/} ]]; then
  echo "CHANGELOG.md has no section '## [$VERSION] - <date>'" >&2
  exit 1
fi
{
  printf '%s\n' "$notes"
  echo
  echo "## Verification"
  echo
  echo "The JARs are published to Maven Central with GPG signatures. This release carries for every"
  echo "module the SBOM (\`*-cyclonedx.json\`), the SHA-256 checksums of the JARs and the SBOMs"
  echo "(\`SHA256SUMS\`), the OWASP Dependency-Check report of the release build with its own checksum"
  echo "(\`SCAN-SHA256SUMS\`), and a build provenance attestation of GitHub. The build is reproducible;"
  echo "see [Verifying a release](https://github.com/NaluKit/pololei/blob/main/SECURITY.md#verifying-a-release)."
} > "$OUT/RELEASE-NOTES.md"

echo "Collected $count JARs of version $VERSION in $OUT"

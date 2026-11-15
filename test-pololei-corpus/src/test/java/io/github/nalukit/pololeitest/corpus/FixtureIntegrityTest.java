/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nalukit.pololeitest.corpus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The retained upstream files and license texts are unchanged: every file has the SHA-256 of
 * {@code manifest.tsv} or {@code licenses.json}, comes from a commit-pinned URL, and no other file is
 * in the corpus.
 */
class FixtureIntegrityTest {

  private static final Set<String> METADATA = Set.of("manifest.tsv", "licenses.json", "sections.tsv", "expectations.tsv", "epc-cases.tsv");

  private static final Pattern PINNED_URL = Pattern.compile("https://raw\\.githubusercontent\\.com/[\\w.-]+/[\\w.-]+/[0-9a-f]{40}/.+");

  private static final Pattern LICENSE_ENTRY = Pattern.compile("\"file\": \"([^\"]+)\",\\s*\"source\": \"([^\"]+)\",\\s*\"sha256\": \"([0-9a-f]{64})\"");

  /** file to {source, sha256} of all retained files and license texts */
  private static Map<String, String[]> pinned() throws IOException {
    Map<String, String[]> files = new LinkedHashMap<>();
    for (String[] row : Corpus.table("manifest.tsv")) {
      assertTrue(Set.of("Apache-2.0", "MIT", "ISC", "BSD-3-Clause", "LGPL-2.1-or-later")
                    .contains(row[3]), "unexpected license " + row[3] + " of " + row[0]);
      files.put(row[0], new String[] { row[1], row[2] });
    }
    Matcher matcher = LICENSE_ENTRY.matcher(Files.readString(Corpus.ROOT.resolve("licenses.json")));
    while (matcher.find()) {
      files.put(matcher.group(1), new String[] { matcher.group(2), matcher.group(3) });
    }
    return files;
  }

  private static String sha256(Path file) throws IOException {
    try {
      return HexFormat.of()
                      .formatHex(MessageDigest.getInstance("SHA-256")
                                              .digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void everyFileHasItsPinnedHash() throws IOException {
    for (Map.Entry<String, String[]> file : pinned().entrySet()) {
      assertEquals(file.getValue()[1], sha256(Corpus.ROOT.resolve(file.getKey())), file.getKey());
    }
  }

  @Test
  void everyFileComesFromAPinnedCommit() throws IOException {
    for (Map.Entry<String, String[]> file : pinned().entrySet()) {
      String source = file.getValue()[0];
      assertTrue(PINNED_URL.matcher(source)
                           .matches(), file.getKey() + " has no commit-pinned source: " + source);
      if (!file.getKey()
               .startsWith("licenses/")) {
        assertTrue(source.endsWith("/" + file.getKey()
                                             .substring(file.getKey()
                                                            .indexOf('/') + 1)),
                   file.getKey() + " is not stored under its upstream path: " + source);
      }
    }
  }

  @Test
  void corpusContainsExactlyTheListedFiles() throws IOException {
    Set<String> present;
    try (Stream<Path> paths = Files.walk(Corpus.ROOT)) {
      present = paths.filter(Files::isRegularFile)
                     .map(path -> Corpus.ROOT.relativize(path)
                                             .toString()
                                             .replace('\\', '/'))
                     .filter(path -> !METADATA.contains(path))
                     .collect(Collectors.toCollection(TreeSet::new));
    }
    assertEquals(new TreeSet<>(pinned().keySet()), present);
  }

  @Test
  void everySectionLiesInARetainedFile() throws IOException {
    Map<String, String[]> files = pinned();
    for (Corpus.Section section : Corpus.sections()) {
      assertTrue(files.containsKey(section.file()), section.file() + " is not retained");
      assertTrue(Corpus.KINDS.contains(section.kind()), section.kind());
      assertTrue(Corpus.UPSTREAM.contains(section.upstream()), section.upstream());
      assertTrue(section.firstLine() <= section.lastLine() && section.lastLine() <= Corpus.lines(section.file())
                                                                                           .size(),
                 section.file() + ":" + section.firstLine() + "-" + section.lastLine());
    }
  }
}

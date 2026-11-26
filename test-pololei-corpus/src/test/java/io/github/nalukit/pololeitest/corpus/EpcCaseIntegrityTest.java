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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every EPC QR code case is anchored in a retained file: its line range exists, every evidence fragment
 * is in that range (or at the named other line), and a stated payload can be read from the file. A note
 * is present exactly where pololei differs from the upstream test.
 */
class EpcCaseIntegrityTest {

  /** {@code @<file>:<line>:<fragment>} or {@code @<line>:<fragment>} for a fragment outside the case range */
  private static final Pattern ELSEWHERE = Pattern.compile("@(?:([^:]+):)?(\\d+):(.*)", Pattern.DOTALL);

  private static final Set<String> UPSTREAM = Set.of("payload", "asserts", "rejected");

  @Test
  void casesAreUniqueAndInRetainedFiles() {
    Set<String> retained = new HashSet<>();
    Corpus.table("manifest.tsv")
          .forEach(row -> retained.add(row[0]));
    Set<String> ids = new HashSet<>();
    for (EpcCases.Case c : EpcCases.cases()) {
      assertTrue(ids.add(c.id()), "duplicate case " + c.id());
      assertTrue(retained.contains(c.file()), c.location() + ": file is not retained");
      assertTrue(UPSTREAM.contains(c.upstream()), c.location() + ": " + c.upstream());
      assertTrue(c.firstLine() <= c.lastLine() && c.lastLine() <= Corpus.lines(c.file())
                                                                        .size(), c.location());
      assertFalse(c.evidence()
                   .isEmpty(), c.location() + ": no evidence");
    }
  }

  @Test
  void everyEvidenceFragmentIsInTheFile() {
    for (EpcCases.Case c : EpcCases.cases()) {
      List<String> lines = Corpus.lines(c.file());
      String range = String.join("\n", lines.subList(c.firstLine() - 1, c.lastLine()));
      for (String fragment : c.evidence()) {
        Matcher elsewhere = ELSEWHERE.matcher(fragment);
        if (elsewhere.matches()) {
          String file = elsewhere.group(1) == null ? c.file() : elsewhere.group(1);
          String line = Corpus.lines(file)
                              .get(Integer.parseInt(elsewhere.group(2)) - 1);
          assertTrue(line.contains(elsewhere.group(3)), c.location() + ": [" + elsewhere.group(3) + "] not in " + file + ":" + elsewhere.group(2));
        } else {
          assertTrue(range.contains(fragment), c.location() + ": [" + fragment + "] not in the case range");
        }
      }
    }
  }

  @Test
  void statedPayloadsAreReadFromTheFile() {
    for (EpcCases.Case c : EpcCases.cases()) {
      if (c.upstream()
           .equals("payload")) {
        String payload = EpcCases.upstreamPayload(c);
        assertTrue(payload.startsWith("BCD\n"), c.location() + ": " + payload);
      }
    }
  }

  @Test
  void notesExplainExactlyTheDeviations() {
    for (EpcCases.Case c : EpcCases.cases()) {
      boolean agrees = EpcCases.agrees(c, EpcCases.build(c));
      if (agrees) {
        assertEquals("", c.note(), c.location() + " agrees with the upstream test; remove the note");
      } else {
        assertFalse(c.note()
                     .isBlank(), c.location() + " differs from the upstream test and needs a note");
      }
    }
  }
}

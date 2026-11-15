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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every value taken from the retained files has exactly one reviewed expectation, at the same place
 * and in the same order, and no expectation exists for a value that is not in the files.
 */
class ExpectationCoverageTest {

  @Test
  void expectationsCoverExactlyTheExtractedValues() {
    List<Corpus.Value> extracted = Corpus.extract();
    List<Corpus.Value> expected = Corpus.expectations()
                                        .stream()
                                        .map(Corpus.Expectation::value)
                                        .toList();
    assertEquals(extracted.size(), expected.size(), "number of values");
    for (int i = 0; i < extracted.size(); i++) {
      assertEquals(extracted.get(i), expected.get(i), "value " + (i + 1));
    }
  }

  @Test
  void everySectionContributesValues() {
    List<Corpus.Value> extracted = Corpus.extract();
    for (Corpus.Section section : Corpus.sections()) {
      assertTrue(extracted.stream()
                          .anyMatch(value -> value.file()
                                                  .equals(section.file()) && value.line() >= section.firstLine() && value.line() <= section.lastLine() && value.kind()
                                                                                                                                                                 .equals(section.kind())),
                 "no value in " + section.file() + ":" + section.firstLine() + "-" + section.lastLine());
    }
  }

  @Test
  void notesExplainExactlyTheDeviations() {
    for (Corpus.Expectation expectation : Corpus.expectations()) {
      String where = expectation.value()
                                .location() + " [" + expectation.value()
                                                                .value() + "]";
      if (Corpus.needsNote(expectation)) {
        assertFalse(expectation.note()
                               .isBlank(), where + " differs from the upstream statement and needs a note");
      } else {
        assertTrue(expectation.note()
                              .isEmpty(), where + " agrees with the upstream statement; remove the note");
      }
    }
  }
}

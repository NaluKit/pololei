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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers in the README of the corpus are the numbers of {@code expectations.tsv} and
 * {@code epc-cases.tsv}, so they cannot drift apart.
 */
class ManifestSummaryTest {

  private static long count(List<Corpus.Expectation> expectations,
                            String kind) {
    return expectations.stream()
                       .filter(expectation -> expectation.value()
                                                         .kind()
                                                         .equals(kind))
                       .count();
  }

  @Test
  void readmeStatesTheNumbersOfTheCorpus() throws IOException {
    String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    List<Corpus.Expectation> expectations = Corpus.expectations();
    long files = expectations.stream()
                             .map(expectation -> expectation.value()
                                                            .file())
                             .distinct()
                             .count();
    long agreeing = expectations.stream()
                                .filter(Corpus::agrees)
                                .count();
    long notes = expectations.stream()
                             .filter(expectation -> !expectation.note()
                                                                .isEmpty())
                             .count();
    String summary = "**Results: " + expectations.size() + " values from " + files + " files (" + count(expectations, "IBAN") + " IBANs, " + count(expectations, "BIC") + " BICs, " + count(expectations,
                                                                                                                                                                                                       "CI") + " Creditor Identifiers). pololei agrees with the upstream statement for " + agreeing + " of them; " + notes + " results are explained one by one.**";
    assertTrue(readme.contains(summary), "README.md must contain:\n" + summary);
  }

  @Test
  void readmeStatesTheNumbersOfTheEpcCases() throws IOException {
    String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    List<EpcCases.Case> cases = EpcCases.cases();
    long files = cases.stream()
                      .map(EpcCases.Case::file)
                      .distinct()
                      .count();
    long payloads = cases.stream()
                         .filter(c -> !c.result()
                                        .startsWith(EpcCases.REJECTED))
                         .count();
    long agreeing = cases.stream()
                         .filter(c -> EpcCases.agrees(c, EpcCases.build(c)))
                         .count();
    String summary = "**EPC QR code: " + cases.size() + " cases from " + files + " files; pololei builds " + payloads + " payloads and rejects " + (cases.size() - payloads) + " inputs. It agrees with the upstream test for " + agreeing + " cases; " + (cases.size() - agreeing) + " results are explained one by one.**";
    assertTrue(readme.contains(summary), "README.md must contain:\n" + summary);
  }
}

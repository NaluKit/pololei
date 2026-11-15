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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates every value of the corpus with pololei and compares the result with its reviewed
 * expectation: the international rules for IBAN, BIC and Creditor Identifier, and for IBANs also the
 * national check digits where pololei has a rule. Fixing or changing a behavior therefore fails this
 * test first and needs a deliberate update of {@code expectations.tsv}.
 * <p>
 * Every run writes {@code target/corpus-report/results.tsv} with the actual results.
 */
class PublicCorpusTest {

  private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());

  @TestFactory
  Stream<DynamicTest> everyValueHasItsExpectedResult() {
    return Corpus.expectations()
                 .stream()
                 .map(expectation -> {
                   Corpus.Value value = expectation.value();
                   return DynamicTest.dynamicTest(value.location() + " " + value.kind() + " [" + value.value() + "]", () -> {
                     String result = Corpus.result(value.kind(), value.value());
                     String national = Corpus.national(value.kind(), value.value());
                     REPORT.add(String.join("\t", value.file(), String.valueOf(value.line()), value.kind(), value.upstream(), value.value(), result, national));
                     assertEquals(expectation.result(), result, "result");
                     assertEquals(expectation.national(), national, "result with national check digits");
                   });
                 });
  }

  @AfterAll
  static void writeReport() throws IOException {
    Path report = Path.of("target/corpus-report/results.tsv");
    Files.createDirectories(report.getParent());
    List<String> lines = new ArrayList<>();
    lines.add("file\tline\tkind\tupstream\tvalue\tresult\tnational");
    synchronized (REPORT) {
      lines.addAll(REPORT);
    }
    Files.write(report, lines, StandardCharsets.UTF_8);
  }
}

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
 * Builds the EPC QR code payload of every case with {@code EpcQrPayload.Builder} and compares it with the
 * reviewed result: the payload byte for byte, or the reason of the rejection. Every run writes
 * {@code target/corpus-report/epc-results.tsv}.
 */
class EpcCorpusTest {

  private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());

  @TestFactory
  Stream<DynamicTest> everyCaseHasItsExpectedResult() {
    return EpcCases.cases()
                   .stream()
                   .map(c -> DynamicTest.dynamicTest(c.location(), () -> {
                     EpcCases.Outcome outcome = EpcCases.build(c);
                     REPORT.add(String.join("\t", c.id(), c.upstream(), outcome.describe(), String.valueOf(outcome.bytes())));
                     assertEquals(c.result(), outcome.describe());
                   }));
  }

  @AfterAll
  static void writeReport() throws IOException {
    Path report = Path.of("target/corpus-report/epc-results.tsv");
    Files.createDirectories(report.getParent());
    List<String> lines = new ArrayList<>();
    lines.add("id\tupstream\tresult\tbytes");
    synchronized (REPORT) {
      lines.addAll(REPORT);
    }
    Files.write(report, lines, StandardCharsets.UTF_8);
  }
}

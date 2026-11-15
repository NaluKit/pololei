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
package io.github.nalukit.pololei;

import io.github.nalukit.pololei.error.IbanError;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The IBANs of the iban4j issues (iban4j-issues-2026-11-15, {@code issue-cases.tsv}): 24 distinct
 * issue/IBAN pairs from the texts and comments, each with its source, and the expected result of
 * the international validation, of the validation with {@link NationalCheckMode#REQUIRED} and, for
 * valid IBANs, of {@link Iban#fields()}. Some values of old issues are wrong or contradict their
 * own assertions; the expectation is the reviewed behaviour of pololei, not the issue statement.
 * The same small cases run under GWT in GwtTestNational. No network access is needed.
 */
class IssueCasesTest {

  private record Case(String issue,
                       String kind,
                       String source,
                       String input,
                       String base,
                       String national,
                       String fields) {
  }

  @Test
  void originalCasesAreComplete() throws IOException {
    Set<String> pairs = new HashSet<>();
    for (Case c : cases()) {
      assertTrue(c.source.startsWith("https://github.com/arturmkrtchyan/iban4j/issues/" + c.issue), c.input);
      if (c.kind.equals("original")) {
        assertTrue(pairs.add(c.issue + " " + c.input), "duplicate " + c.input);
      }
    }
    assertEquals(24, pairs.size());
  }

  @Test
  void resultsAreAsReviewed() throws IOException {
    IbanValidator base     = IbanValidator.standard();
    IbanValidator national = IbanValidator.standard()
                                          .nationalCheck(NationalCheckMode.REQUIRED);
    for (Case c : cases()) {
      assertEquals(c.base, name(base.validate(c.input)), c.source + " " + c.input);
      assertEquals(c.national, name(national.validate(c.input)), c.source + " " + c.input + " (national)");
      if (c.base.equals("VALID")) {
        assertEquals(c.fields, Iban.parse(c.input)
                                   .fields()
                                   .toString(), c.source + " " + c.input + " (fields)");
      }
    }
  }

  @Test
  void decisionsOfTheReview() {
    IbanValidator national = IbanValidator.standard()
                                          .nationalCheck(NationalCheckMode.REQUIRED);
    // #34: control digits 45 fail, 48 pass (the issue code has the assertions the other way round)
    assertEquals(IbanError.INVALID_NATIONAL_CHECK_DIGITS, national.validate("ES7021000418450200051322")
                                                                 .error()
                                                                 .orElseThrow());
    assertTrue(national.isValid("ES7821000418480200051322"));
    // #29: the old 32 character NI example stays invalid, the current registry example is valid
    assertEquals(IbanError.INVALID_LENGTH, IbanValidator.standard()
                                                        .validate("NI92BAMC000000000000000003123123")
                                                        .error()
                                                        .orElseThrow());
    assertTrue(IbanValidator.standard()
                            .isValid("NI45BAPR00000013000003558124"));
    // #17: strict check digit range 02 to 98
    assertEquals(IbanError.INVALID_CHECK_DIGITS, IbanValidator.standard()
                                                              .validate("TR010012300882101517977799")
                                                              .error()
                                                              .orElseThrow());
    assertTrue(IbanValidator.standard()
                            .isValid("TR980012300882101517977799"));
  }

  private static String name(ValidationResult<Iban, IbanError> result) {
    return result.isValid() ? "VALID" : result.error()
                                              .orElseThrow()
                                              .name();
  }

  private static List<Case> cases() throws IOException {
    List<Case> cases = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(IssueCasesTest.class.getResourceAsStream("issue-cases.tsv"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("issue\t")) {
          continue;
        }
        String[] c = line.split("\t", -1);
        cases.add(new Case(c[0], c[1], c[2], c[3], c[4], c[5], c[6]));
      }
    }
    return cases;
  }
}

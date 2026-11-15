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

import io.github.nalukit.pololei.error.BicError;
import io.github.nalukit.pololei.error.InvalidBicException;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Building a BIC from its components, iban4j issue #62. */
class BicComponentsTest {

  private static BicError error(String institution,
                                String country,
                                String location,
                                String branch) {
    return assertThrows(InvalidBicException.class, () -> Bic.of(institution, country, location, branch)).result()
                                                                                                         .error()
                                                                                                         .orElseThrow();
  }

  @Test
  void buildsFromComponents() {
    assertEquals("DEUTDEFF500", Bic.of("DEUT", "DE", "FF", "500").toString());
    assertEquals("DEUTDEFF", Bic.of("DEUT", "DE", "FF").toString());
    assertEquals("DEUTDEFF", Bic.of("DEUT", "DE", "FF", null).toString());
    assertEquals(Bic.parse("DEUTDEFF500"), Bic.of("DEUT", "DE", "FF", "500"));
    assertEquals(Bic.parse("DEUTDEFF500"), Bic.of(" deut", "de ", "ff", "500"));
  }

  @Test
  void alphanumericInstitutionCodeIsValid() {
    assertEquals("A1B2DEFF", Bic.of("A1B2", "DE", "FF").toString());
    assertEquals("A1B2DE2L", Bic.of("A1B2", "DE", "2L").toString());
  }

  @Test
  void invalidComponentsAreRejected() {
    assertEquals(BicError.INVALID_LENGTH, error("DEU", "DE", "FF", null));
    assertEquals(BicError.INVALID_LENGTH, error("DEUTX", "DE", "FF", null));
    assertEquals(BicError.INVALID_LENGTH, error("DEUT", "D", "FF", null));
    assertEquals(BicError.INVALID_LENGTH, error("DEUT", "DE", "F", null));
    assertEquals(BicError.INVALID_LENGTH, error("DEUT", "DE", "FF", ""));
    assertEquals(BicError.INVALID_LENGTH, error("DEUT", "DE", "FF", "50"));
    assertEquals(BicError.INVALID_LENGTH, error("DEUT", "DE", "FF", "5000"));
    assertEquals(BicError.EMPTY, error(null, "DE", "FF", null));
    assertEquals(BicError.EMPTY, error("DEUT", null, "FF", null));
    assertEquals(BicError.EMPTY, error("DEUT", "DE", null, null));
    assertEquals(BicError.INVALID_CHARACTER, error("DE-T", "DE", "FF", null));
    assertEquals(BicError.INVALID_CHARACTER, error("DEUT", "D1", "FF", null));
    assertEquals(BicError.INVALID_CHARACTER, error("DEUT", "DE", "F-", null));
    assertEquals(BicError.INVALID_CHARACTER, error("DEUT", "DE", "FF", "5#0"));
    assertEquals(BicError.UNKNOWN_COUNTRY, error("DEUT", "ZZ", "FF", null));
  }

  @Test
  void errorNamesTheComponentAndPosition() {
    InvalidBicException e = assertThrows(InvalidBicException.class, () -> Bic.of("DEU", "DE", "FF"));
    assertEquals("institution code must have 4 characters, but has 3", e.result().message().orElseThrow());
    e = assertThrows(InvalidBicException.class, () -> Bic.of("DEUT", "DE", "FF", "5#0"));
    assertEquals(10, e.result().position().getAsInt());
  }

  @Test
  void sameRulesAsParseAndRandom() {
    Random random = new Random(11);
    for (int i = 0; i < 200; i++) {
      Bic bic = Bic.random(random, i % 2 == 0 ? 8 : 11);
      assertEquals(bic, Bic.of(bic.institutionCode(), bic.countryCode(), bic.locationCode(), bic.branchCode().orElse(null)));
    }
  }
}

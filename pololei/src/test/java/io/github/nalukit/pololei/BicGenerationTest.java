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

import java.security.SecureRandom;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Random BIC test data, iban4j issue #43. */
class BicGenerationTest {

  @Test
  void sameSeedAndOptionsGiveTheSameBic() {
    assertEquals(Bic.random(new Random(42)), Bic.random(new Random(42)));
    assertEquals(Bic.random(new Random(42), 11), Bic.random(new Random(42), 11));
    assertEquals(Bic.random("DE", new Random(7), 11), Bic.random("DE", new Random(7), 11));
    assertNotEquals(Bic.random(new Random(1)), Bic.random(new Random(2)));
  }

  @Test
  void generatedBicsAreValid() {
    Random random = new Random(1);
    for (int i = 0; i < 1000; i++) {
      Bic eight = Bic.random(random, 8);
      assertTrue(Bic.isValid(eight.toString()), eight.toString());
      assertEquals(8, eight.toString().length());
      assertTrue(eight.branchCode().isEmpty());
      assertFalse(eight.isTestBic());
      Bic eleven = Bic.random(random, 11);
      assertTrue(Bic.isValid(eleven.toString()), eleven.toString());
      assertEquals(11, eleven.toString().length());
      assertTrue(eleven.branchCode().isPresent());
      assertFalse(eleven.isTestBic());
      assertEquals(eleven, Bic.parse(eleven.toString()));
    }
  }

  @Test
  void givenCountryIsKept() {
    Random random = new Random(3);
    for (int i = 0; i < 200; i++) {
      assertEquals("DE", Bic.random("DE", random).countryCode());
      assertEquals("XK", Bic.random(" xk ", random, 11).countryCode());
    }
  }

  @Test
  void randomCountriesAreSpread() {
    Random random = new Random(5);
    java.util.Set<String> countries = new java.util.HashSet<>();
    for (int i = 0; i < 2000; i++) {
      countries.add(Bic.random(random).countryCode());
    }
    assertTrue(countries.size() > 100, "countries: " + countries.size());
  }

  @Test
  void invalidCountriesAndOptionsAreRejected() {
    Random random = new Random(1);
    assertEquals(BicError.UNKNOWN_COUNTRY, assertThrows(InvalidBicException.class, () -> Bic.random("ZZ", random)).result().error().orElseThrow());
    assertEquals(BicError.UNKNOWN_COUNTRY, assertThrows(InvalidBicException.class, () -> Bic.random("DEU", random)).result().error().orElseThrow());
    assertEquals(BicError.UNKNOWN_COUNTRY, assertThrows(InvalidBicException.class, () -> Bic.random("", random)).result().error().orElseThrow());
    assertEquals(BicError.UNKNOWN_COUNTRY, assertThrows(InvalidBicException.class, () -> Bic.random((String) null, random)).result().error().orElseThrow());
    assertThrows(IllegalArgumentException.class, () -> Bic.random(random, 9));
    assertThrows(IllegalArgumentException.class, () -> Bic.random("DE", random, 0));
    assertThrows(NullPointerException.class, () -> Bic.random((Random) null));
    assertThrows(NullPointerException.class, () -> Bic.random("DE", null));
  }

  @Test
  void givenRandomIsUsedDirectly() {
    int[] draws = { 0 };
    Random counting = new Random(9) {
      @Override
      public int nextInt(int bound) {
        draws[0]++;
        return super.nextInt(bound);
      }
    };
    Bic.random("DE", counting, 11);
    // institution 4, location 2, branch 3
    assertTrue(draws[0] >= 9, "draws: " + draws[0]);
    assertTrue(Bic.isValid(Bic.random("FR", new SecureRandom()).toString()));
  }
}

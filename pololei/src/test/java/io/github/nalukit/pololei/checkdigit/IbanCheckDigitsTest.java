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
package io.github.nalukit.pololei.checkdigit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanCheckDigitsTest {

  @Test
  void calculate() {
    assertEquals("89", IbanCheckDigits.calculate("DE", "370400440532013000"));
    assertEquals("29", IbanCheckDigits.calculate("GB", "NWBK60161331926819"));
  }

  @Test
  void checkDigitsAreAlwaysTwoCharacters() {
    assertEquals("02", IbanCheckDigits.calculate("DE", findBbanWithCheckDigits("02")));
  }

  @Test
  void isValid() {
    assertTrue(IbanCheckDigits.isValid("DE89370400440532013000"));
    assertFalse(IbanCheckDigits.isValid("DE88370400440532013000"));
    assertFalse(IbanCheckDigits.isValid("DE89"));
  }

  @Test
  void checkDigits99And01PassMod97ButAreRejected() {
    // 02 and 99 as well as 98 and 01 are congruent modulo 97
    String low  = findBbanWithCheckDigits("02");
    String high = findBbanWithCheckDigits("98");
    assertEquals(1, IbanCheckDigits.mod97(low + "DE99"));
    assertFalse(IbanCheckDigits.isValid("DE99" + low));
    assertEquals(1, IbanCheckDigits.mod97(high + "DE01"));
    assertFalse(IbanCheckDigits.isValid("DE01" + high));
    assertFalse(IbanCheckDigits.isValid("DE00" + low));
  }

  @Test
  void invalidCharacters() {
    assertThrows(IllegalArgumentException.class, () -> IbanCheckDigits.calculate("DE", "3704-0044"));
    assertThrows(IllegalArgumentException.class, () -> IbanCheckDigits.calculate("de", "370400440532013000"));
  }

  private static String findBbanWithCheckDigits(String checkDigits) {
    for (long i = 0; ; i++) {
      String bban = String.valueOf(1_000_000_000_000_000L + i)
                          .substring(1);
      if (IbanCheckDigits.calculate("DE", bban)
                         .equals(checkDigits)) {
        return bban;
      }
    }
  }
}

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BicTest {

  @ParameterizedTest
  @ValueSource(strings = { "DEUTDEFF",
                           "DEUTDEFF500",
                           "DEUTDEFFXXX",
                           "deutdeff",
                           "DEUT DE FF",
                           "NWBKGB2L",
                           "DEU1DEFF",
                           "BNPAXKPR" })
  void validBics(String input) {
    assertTrue(Bic.isValid(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void empty(String input) {
    assertEquals(Optional.of(BicError.EMPTY), Bic.validate(input)
                                                 .error());
  }

  @ParameterizedTest
  @CsvSource({ "DEUTDEF,      INVALID_LENGTH",
               "DEUTDEFF5,    INVALID_LENGTH",
               "DEUTDEFF5000, INVALID_LENGTH",
               "DEUT-EFF,     INVALID_CHARACTER",
               "DEUT1EFF,     INVALID_CHARACTER",
               "DEUTDEF_,     INVALID_CHARACTER",
               "DEUTDEFF50_,  INVALID_CHARACTER",
               "DEUTAAFF,     UNKNOWN_COUNTRY",
               "DEUTDDFF500,  UNKNOWN_COUNTRY" })
  void invalidBics(String input,
                   BicError expected) {
    assertEquals(Optional.of(expected), Bic.validate(input)
                                           .error());
    assertEquals(expected, assertThrows(InvalidBicException.class, () -> Bic.parse(input)).error());
    assertEquals(Optional.empty(), Bic.tryParse(input));
  }

  @Test
  void parts() {
    Bic bic = Bic.parse("deut de ff 500");
    assertEquals("DEUTDEFF500", bic.toString());
    assertEquals("DEUT", bic.institutionCode());
    assertEquals("DE", bic.countryCode());
    assertEquals("FF", bic.locationCode());
    assertEquals(Optional.of("500"), bic.branchCode());
    assertFalse(bic.isPrimaryOffice());
    assertFalse(bic.isTestBic());
    assertEquals(Bic.parse("DEUTDEFF"), bic.primaryOffice());
  }

  @Test
  void primaryOffice() {
    Bic bic = Bic.parse("DEUTDEFF");
    assertEquals(Optional.empty(), bic.branchCode());
    assertTrue(bic.isPrimaryOffice());
    assertSame(bic, bic.primaryOffice());
    assertTrue(Bic.parse("DEUTDEFFXXX")
                  .isPrimaryOffice());
  }

  @Test
  void testBic() {
    assertTrue(Bic.parse("DEUTDEF0")
                  .isTestBic());
  }
}

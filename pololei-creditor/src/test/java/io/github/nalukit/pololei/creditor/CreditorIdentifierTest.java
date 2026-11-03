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
package io.github.nalukit.pololei.creditor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditorIdentifierTest {

  /** Test identifier of the Deutsche Bundesbank. */
  private static final String GERMAN = "DE98ZZZ09999999999";

  /**
   * Published identifiers: the test identifier of the Deutsche Bundesbank, and the Spanish and
   * Maltese examples of python-stdnum (stdnum.eu.at_02; MT is the example of EPC262-08 version 12.0).
   */
  @ParameterizedTest
  @CsvSource({ "DE98ZZZ09999999999, DE, 98, ZZZ, 09999999999",
               "ES23ZZZ47690558N, ES, 23, ZZZ, 47690558N",
               "MT50ZZZ670169305T, MT, 50, ZZZ, 670169305T" })
  void publishedIdentifiers(String input,
                            String country,
                            String checkDigits,
                            String businessCode,
                            String national) {
    CreditorIdentifier ci = CreditorIdentifier.parse(input);
    assertEquals(input, ci.toString());
    assertEquals(country, ci.countryCode());
    assertEquals(checkDigits, ci.checkDigits());
    assertEquals(businessCode, ci.businessCode());
    assertEquals(national, ci.nationalIdentifier());
    assertTrue(CreditorIdentifier.isValid(input));
    assertEquals(Optional.empty(), CreditorIdentifier.validate(input));
    assertEquals(ci, CreditorIdentifier.of(country, null, national));
  }

  @Test
  void whitespaceAndLowerCase() {
    assertEquals(GERMAN, CreditorIdentifier.parse(" de98 zzz 0999\t9999 999 ")
                                           .toString());
    assertEquals(GERMAN, CreditorIdentifier.parse("DE98ZZZ09999999999")
                                           .toString());
  }

  @Test
  void businessCodeIsNotPartOfTheCheckDigits() {
    CreditorIdentifier ci    = CreditorIdentifier.parse(GERMAN);
    CreditorIdentifier other = CreditorIdentifier.parse("DE98A1B09999999999");
    assertEquals("A1B", other.businessCode());
    assertEquals(other, ci.withBusinessCode("a1b"));
    assertEquals(ci, other.withBusinessCode(null));
    assertEquals(ci, other.withBusinessCode(""));
    assertNotEquals(ci, other);
    assertTrue(ci.isSameCreditor(other));
    assertFalse(ci.isSameCreditor(CreditorIdentifier.of("DE", null, "09999999998")));
    assertEquals(CreditorIdentifierError.INVALID_LENGTH, assertThrows(InvalidCreditorIdentifierException.class, () -> ci.withBusinessCode("AB")).error());
    assertEquals(CreditorIdentifierError.INVALID_CHARACTER, assertThrows(InvalidCreditorIdentifierException.class, () -> ci.withBusinessCode("A-B")).error());
  }

  @ParameterizedTest
  @CsvSource({ "DE97ZZZ09999999999, INVALID_CHECK_DIGITS, 3",          // wrong check digits
               "DE98ZZZ09999999998, INVALID_CHECK_DIGITS, 3",          // wrong national identifier
               "DEX8ZZZ09999999999, INVALID_CHECK_DIGITS, 3",          // letter instead of a check digit
               "ES32ZZZ47690558N, INVALID_CHECK_DIGITS, 3",            // swapped check digits
               "MT50ZZZ670169305S, INVALID_CHECK_DIGITS, 3",
               "DE98ZZZ0999999999, INVALID_LENGTH, 0",                 // 17 characters, Germany needs 18
               "DE98ZZZ099999999990, INVALID_LENGTH, 0",
               "ES23ZZZ, INVALID_LENGTH, 0",                           // no national identifier
               "ES23ZZZ12345678901234567890123456789, INVALID_LENGTH, 0", // 36 characters
               "D198ZZZ09999999999, INVALID_COUNTRY_CODE, 2",
               "US98ZZZ09999999999, UNSUPPORTED_COUNTRY, 1",           // not in SEPA
               "BR98ZZZ09999999999, UNSUPPORTED_COUNTRY, 1",           // IBAN country outside SEPA
               "DE98ZZZ0999999999-, INVALID_CHARACTER, 18",
               "DE98ZZZ0999999999Ä, INVALID_CHARACTER, 18" })
  void invalidIdentifiers(String input,
                          CreditorIdentifierError error,
                          int position) {
    assertEquals(Optional.of(error), CreditorIdentifier.validate(input));
    assertFalse(CreditorIdentifier.isValid(input));
    assertEquals(Optional.empty(), CreditorIdentifier.tryParse(input));
    InvalidCreditorIdentifierException exception = assertThrows(InvalidCreditorIdentifierException.class, () -> CreditorIdentifier.parse(input));
    assertEquals(error, exception.error());
    assertEquals(position == 0 ? OptionalInt.empty() : OptionalInt.of(position), exception.position());
    // the message never contains the input
    assertFalse(exception.getMessage()
                         .contains(input.substring(4)), exception.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = { "", " ", "\t\n" })
  void empty(String input) {
    assertEquals(Optional.of(CreditorIdentifierError.EMPTY), CreditorIdentifier.validate(input));
  }

  @Test
  void nullInput() {
    assertEquals(Optional.of(CreditorIdentifierError.EMPTY), CreditorIdentifier.validate(null));
    assertFalse(CreditorIdentifier.isValid(null));
    assertEquals(Optional.empty(), CreditorIdentifier.tryParse(null));
  }

  @Test
  void createWithCalculatedCheckDigits() {
    assertEquals(GERMAN, CreditorIdentifier.of("de", "", "0999 9999 999")
                                           .toString());
    assertEquals("ES23ABC47690558N", CreditorIdentifier.of("ES", "abc", "47690558N")
                                                       .toString());
    // other SEPA countries: the general rules only, up to 35 characters
    CreditorIdentifier longest = CreditorIdentifier.of("AT", null, "1234567890123456789012345678");
    assertEquals(CreditorIdentifier.MAX_LENGTH, longest.toString()
                                                       .length());
    assertTrue(CreditorIdentifier.isValid(longest.toString()));
    assertEquals(CreditorIdentifierError.INVALID_LENGTH, assertThrows(InvalidCreditorIdentifierException.class, () -> CreditorIdentifier.of("DE", null, "123")).error());
    assertEquals(CreditorIdentifierError.UNSUPPORTED_COUNTRY, assertThrows(InvalidCreditorIdentifierException.class, () -> CreditorIdentifier.of("US", null, "09999999999")).error());
    assertEquals(CreditorIdentifierError.INVALID_CHARACTER, assertThrows(InvalidCreditorIdentifierException.class, () -> CreditorIdentifier.of("DE", null, "0999999999/")).error());
    assertThrows(NullPointerException.class, () -> CreditorIdentifier.of(null, null, "1"));
    assertThrows(NullPointerException.class, () -> CreditorIdentifier.of("DE", null, null));
  }

  @Test
  void countryScope() {
    for (String country : new String[] { "DE", "AT", "FR", "NL", "CH", "GB", "MC", "SM", "VA", "AD", "NO", "IS", "LI" }) {
      assertTrue(CreditorIdentifier.isSupportedCountry(country), country);
    }
    for (String country : new String[] { "US", "BR", "TR", "XX", "de", "" }) {
      assertFalse(CreditorIdentifier.isSupportedCountry(country), country);
    }
    assertFalse(CreditorIdentifier.isSupportedCountry(null));
    assertTrue(CreditorIdentifier.hasNationalFormat("DE"));
    assertFalse(CreditorIdentifier.hasNationalFormat("AT"));
    assertFalse(CreditorIdentifier.hasNationalFormat(null));
  }

  @Test
  void equalsAndHashCode() {
    CreditorIdentifier a = CreditorIdentifier.parse(GERMAN);
    CreditorIdentifier b = CreditorIdentifier.parse("de98zzz09999999999");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, GERMAN);
  }
}

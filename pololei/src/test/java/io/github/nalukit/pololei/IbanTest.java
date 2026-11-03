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
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanTest {

  /** IBANs taken over from the iban4g test suite. */
  static Stream<String> validIbans() throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(IbanTest.class.getResourceAsStream("valid-ibans.txt"), StandardCharsets.UTF_8))) {
      return reader.lines()
                   .filter(line -> !line.isBlank())
                   .toList()
                   .stream();
    }
  }

  static Stream<String> registryExamples() {
    return IbanRegistry.countries()
                       .stream()
                       .map(IbanCountry::ibanExample);
  }

  @ParameterizedTest
  @MethodSource({ "validIbans",
                  "registryExamples" })
  void validIbansAreAccepted(String input) {
    Iban iban = Iban.parse(input);
    assertEquals(input, iban.toString());
    assertTrue(Iban.isValid(iban.toPrintFormat()));
  }

  @ParameterizedTest
  @MethodSource("validIbans")
  void wrongCheckDigitsAreRejected(String input) {
    String checkDigits = input.substring(2, 4);
    String other       = checkDigits.equals("50") ? "51" : "50";
    assertEquals(Optional.of(IbanError.INVALID_CHECK_DIGITS), Iban.validate(input.substring(0, 2) + other + input.substring(4))
                                                                  .error());
  }

  @Test
  void printAndLowerCaseInputIsNormalized() {
    Iban iban = Iban.parse("  de89 3704 0044\t0532 0130 00 ");
    assertEquals("DE89370400440532013000", iban.toString());
    assertEquals("DE89 3704 0044 0532 0130 00", iban.toPrintFormat());
    assertEquals("DE89370400440532013000", iban.format(IbanFormat.ELECTRONIC));
  }

  @Test
  void partsAreExtractedFromRegistryPositions() {
    Iban iban = Iban.parse("DE89370400440532013000");
    assertEquals("DE", iban.countryCode());
    assertEquals("89", iban.checkDigits());
    assertEquals("370400440532013000", iban.bban());
    assertEquals(Optional.of("37040044"), iban.bankIdentifier());
    assertEquals(Optional.empty(), iban.branchIdentifier());
    assertEquals("0532013000", iban.accountNumber());
    assertTrue(iban.isSepa());
    assertEquals("Germany", iban.country()
                                .countryName());
  }

  @Test
  void bankBranchAndAccountNumber() {
    Iban iban = Iban.parse("GB29NWBK60161331926819");
    assertEquals(Optional.of("NWBK"), iban.bankIdentifier());
    assertEquals(Optional.of("601613"), iban.branchIdentifier());
    assertEquals("31926819", iban.accountNumber());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "   ",
                           "\t" })
  void emptyInput(String input) {
    assertEquals(Optional.of(IbanError.EMPTY), Iban.validate(input)
                                                   .error());
  }

  @ParameterizedTest
  @CsvSource({ "DE89-3704-0044-0532-0130-00, INVALID_CHARACTER",
               "DE89370400440532013000ä,      INVALID_CHARACTER",
               "8937040044053201300,          INVALID_COUNTRY_CODE",
               "D,                            INVALID_COUNTRY_CODE",
               "US89370400440532013000,       UNSUPPORTED_COUNTRY",
               "DE8937040044053201300,        INVALID_LENGTH",
               "DE893704004405320130000,      INVALID_LENGTH",
               "DEX9370400440532013000,       INVALID_CHECK_DIGITS",
               "DE8937040044053201300A,       INVALID_BBAN",
               "DE00370400440532013000,       INVALID_CHECK_DIGITS",
               "DE88370400440532013000,       INVALID_CHECK_DIGITS" })
  void invalidInput(String input,
                    IbanError expected) {
    ValidationResult<Iban, IbanError> result = Iban.validate(input);
    assertFalse(result.isValid());
    assertEquals(Optional.of(expected), result.error());
    assertTrue(result.message()
                     .isPresent());
    assertTrue(result.value()
                     .isEmpty());
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.parse(input));
    assertEquals(expected, exception.error());
    assertEquals(Optional.empty(), Iban.tryParse(input));
  }

  @Test
  void checkDigits99And01AreRejectedEvenIfMod97Matches() {
    // 02 and 99 as well as 98 and 01 are congruent modulo 97
    Random random = new Random(1);
    String low    = null;
    String high   = null;
    while (low == null || high == null) {
      Iban iban = Iban.random("DE", random);
      if (iban.checkDigits()
              .equals("02")) {
        low = iban.bban();
      } else if (iban.checkDigits()
                     .equals("98")) {
        high = iban.bban();
      }
    }
    // IbanCheckDigitsTest shows that the mod 97 check alone would accept them
    assertEquals(Optional.of(IbanError.INVALID_CHECK_DIGITS), Iban.validate("DE99" + low)
                                                                  .error());
    assertEquals(Optional.of(IbanError.INVALID_CHECK_DIGITS), Iban.validate("DE01" + high)
                                                                  .error());
  }

  @Test
  void bbanErrorNamesPositionAndExpectedType() {
    ValidationResult<Iban, IbanError> result = Iban.validate("DE89370400440532013X00");
    assertEquals("expected a digit at position 20 (BBAN structure of DE: 8!n10!n)", result.message()
                                                                                         .orElseThrow());
  }

  @Test
  void messagesDoNotContainTheInput() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.parse("DE88370400440532013000"));
    assertFalse(exception.getMessage()
                         .contains("370400440532013000"));
  }

  @Test
  void strictElectronicFormat() {
    assertTrue(Iban.validate("DE89370400440532013000", IbanFormat.ELECTRONIC)
                   .isValid());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), Iban.validate("DE89 3704 0044 0532 0130 00", IbanFormat.ELECTRONIC)
                                                            .error());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), Iban.validate("de89370400440532013000", IbanFormat.ELECTRONIC)
                                                            .error());
  }

  @Test
  void strictPrintFormat() {
    assertEquals("DE89370400440532013000", Iban.parse("DE89 3704 0044 0532 0130 00", IbanFormat.PRINT)
                                               .toString());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), Iban.validate("DE89370400440532013000", IbanFormat.PRINT)
                                                            .error());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), Iban.validate("DE89 37040044 0532 0130 00", IbanFormat.PRINT)
                                                            .error());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), Iban.validate("DE89 3704 0044 0532 0130 00 ", IbanFormat.PRINT)
                                                            .error());
  }

  @Test
  void ofCalculatesCheckDigits() {
    assertEquals("DE89370400440532013000", Iban.of("DE", "370400440532013000")
                                               .toString());
    assertEquals("GB29NWBK60161331926819", Iban.of("GB", "NWBK60161331926819")
                                               .toString());
  }

  @Test
  void randomIbansAreValid() {
    Random random = new Random(42);
    for (int i = 0; i < 1_000; i++) {
      Iban iban = Iban.random(random);
      assertTrue(Iban.isValid(iban.toString()), iban.toString());
    }
    assertEquals("AT", Iban.random("AT", random)
                           .countryCode());
  }

  @Test
  void randomIbansAreReproducibleWithSeed() {
    assertEquals(Iban.random(new Random(7)), Iban.random(new Random(7)));
  }

  @Test
  void equalsUsesElectronicFormat() {
    assertEquals(Iban.parse("DE89370400440532013000"), Iban.parse("DE89 3704 0044 0532 0130 00"));
    assertEquals(Iban.parse("DE89370400440532013000")
                     .hashCode(), Iban.parse("de89 3704 0044 0532 0130 00")
                                      .hashCode());
    assertNotEquals(Iban.parse("DE89370400440532013000"), Iban.parse("AT611904300234573201"));
  }

  @Test
  void validationResultToString() {
    assertEquals("valid: DE89370400440532013000", Iban.validate("DE89370400440532013000")
                                                      .toString());
    assertEquals("invalid (EMPTY): IBAN is empty", Iban.validate("")
                                                       .toString());
  }
}

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
import io.github.nalukit.pololei.national.NationalCheck;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random IBANs with national check digits (GAP-07) and with given values (GAP-08).
 */
class IbanGenerationTest {

  private static final IbanValidator NATIONAL = IbanValidator.standard()
                                                             .nationalCheck(NationalCheckMode.REQUIRED);

  static Stream<String> countriesWithRule() {
    return NationalChecks.all()
                         .stream()
                         .map(NationalCheck::countryCode);
  }

  @ParameterizedTest
  @MethodSource("countriesWithRule")
  void nationallyValidRandomIbans(String countryCode) {
    List<Iban> first = random(countryCode, 42);
    for (Iban iban : first) {
      ValidationResult<Iban, IbanError> result = NATIONAL.validate(iban.toString());
      assertTrue(result.isValid(), iban + ": " + result);
      assertEquals(NationalCheckStatus.PASSED, result.nationalCheck());
    }
    // the same seed gives the same IBANs
    assertEquals(first, random(countryCode, 42));
  }

  private static List<Iban> random(String countryCode,
                                   long seed) {
    Random     random = new Random(seed);
    List<Iban> ibans  = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      ibans.add(Iban.builder()
                    .countryCode(countryCode)
                    .nationalCheckDigits(true)
                    .buildRandom(random));
    }
    return ibans;
  }

  @Test
  void defaultRandomModeIsUnchanged() {
    // Iban.random does not calculate national check digits: most French IBANs fail the RIB key
    Random random = new Random(42);
    int    passed = 0;
    for (int i = 0; i < 100; i++) {
      Iban iban = Iban.random("FR", random);
      assertTrue(Iban.isValid(iban.toString()));
      passed += NATIONAL.isValid(iban.toString()) ? 1 : 0;
    }
    assertTrue(passed < 10, "passed: " + passed);
  }

  @Test
  void macedonianRandomIbansUseDigits() {
    Random random = new Random(5);
    for (int i = 0; i < 100; i++) {
      Iban iban = Iban.builder()
                      .countryCode("MK")
                      .nationalCheckDigits(true)
                      .buildRandom(random);
      assertTrue(iban.bban()
                     .matches("[0-9]+"), iban.toString());
    }
  }

  @Test
  void norwegianSpecialCases() {
    // a fixed account whose mod 11 remainder is 1 has no check digit: no retry can help
    String              bban      = norwegianWithoutCheckDigit();
    InvalidIbanException exhausted = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                       .countryCode("NO")
                                                                                       .bankIdentifier(bban.substring(0, 4))
                                                                                       .field(BbanField.ACCOUNT_NUMBER, bban.substring(4, 10))
                                                                                       .nationalCheckDigits(true)
                                                                                       .buildRandom(new Random(1)));
    assertEquals(IbanError.INVALID_NATIONAL_CHECK_DIGITS, exhausted.error());
    assertEquals(Optional.of("NO_MOD11"), exhausted.result()
                                                  .rule());
    assertEquals("no valid national check digits of NO found after 1 random attempt(s) (rule NO_MOD11)", exhausted.getMessage());
    // the same without random values
    assertEquals(IbanError.INVALID_NATIONAL_CHECK_DIGITS, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                                             .countryCode("NO")
                                                                                                             .bankIdentifier(bban.substring(0, 4))
                                                                                                             .field(BbanField.ACCOUNT_NUMBER, bban.substring(4, 10))
                                                                                                             .nationalCheckDigits(true)
                                                                                                             .build()).error());
    // with a free account number the generator draws again; postgiro accounts use Luhn
    Random random = new Random(9);
    for (int i = 0; i < 200; i++) {
      assertTrue(NATIONAL.isValid(Iban.builder()
                                      .countryCode("NO")
                                      .bankIdentifier(bban.substring(0, 4))
                                      .nationalCheckDigits(true)
                                      .buildRandom(random)
                                      .toString()));
      assertTrue(NATIONAL.isValid(Iban.builder()
                                      .countryCode("NO")
                                      .bankIdentifier("0000")
                                      .nationalCheckDigits(true)
                                      .buildRandom(random)
                                      .toString()));
    }
  }

  /** @return a Norwegian BBAN whose mod 11 remainder is 1, so no check digit makes it valid */
  private static String norwegianWithoutCheckDigit() {
    int[] weights = { 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 };
    for (long n = 8601000000L; ; n++) {
      String digits = String.valueOf(n);
      int    sum    = 0;
      for (int i = 0; i < 10; i++) {
        sum += (digits.charAt(i) - '0') * weights[i];
      }
      if (sum % 11 == 1) {
        return digits + "0";
      }
    }
  }

  @Test
  void countryWithoutRule() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("DE")
                                                                                        .nationalCheckDigits(true)
                                                                                        .buildRandom(new Random(1)));
    assertEquals(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, exception.error());
    assertEquals(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                                            .countryCode("NL")
                                                                                                            .bban("ABNA0417164300")
                                                                                                            .nationalCheckDigits(true)
                                                                                                            .build()).error());
  }

  @Test
  void givenBankIsKept() {
    Random random = new Random(42);
    for (int i = 0; i < 100; i++) {
      Iban german = Iban.builder()
                        .countryCode("DE")
                        .bankIdentifier("37040044")
                        .buildRandom(random);
      assertEquals(Optional.of("37040044"), german.bankIdentifier());
      assertTrue(Iban.isValid(german.toString()));
      Iban british = Iban.builder()
                         .countryCode("GB")
                         .bankIdentifier("NWBK")
                         .branchIdentifier("601613")
                         .buildRandom(random);
      assertEquals(Optional.of("NWBK"), british.bankIdentifier());
      assertEquals(Optional.of("601613"), british.branchIdentifier());
      assertTrue(Iban.isValid(british.toString()));
      Iban french = Iban.builder()
                        .countryCode("FR")
                        .bankIdentifier("20041")
                        .nationalCheckDigits(true)
                        .buildRandom(random);
      assertEquals(Optional.of("20041"), french.bankIdentifier());
      assertTrue(NATIONAL.isValid(french.toString()));
    }
  }

  @Test
  void completeInputIsUsedAsIs() {
    assertEquals("GB29NWBK60161331926819", Iban.builder()
                                               .countryCode("GB")
                                               .bankIdentifier("NWBK")
                                               .branchIdentifier("601613")
                                               .accountNumber("31926819")
                                               .buildRandom(new Random(1))
                                               .toString());
    assertEquals("FR1420041010050500013M02606", Iban.builder()
                                                    .countryCode("FR")
                                                    .bankIdentifier("20041")
                                                    .branchIdentifier("01005")
                                                    .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
                                                    .nationalCheckDigits(true)
                                                    .buildRandom(new Random(1))
                                                    .toString());
  }

  @Test
  void invalidInput() {
    // missing country
    assertEquals(IbanError.INVALID_COUNTRY_CODE, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                                    .buildRandom(new Random(1))).error());
    // too long and wrong type
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                              .countryCode("DE")
                                                                                              .bankIdentifier("370400441")
                                                                                              .buildRandom(new Random(1))).error());
    assertEquals(IbanError.INVALID_BBAN, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                            .countryCode("GB")
                                                                                            .bankIdentifier("NWB1")
                                                                                            .buildRandom(new Random(1))).error());
    // contradicting descriptions of the BBAN
    assertThrows(IllegalStateException.class, () -> Iban.builder()
                                                        .countryCode("DE")
                                                        .bban("370400440532013000")
                                                        .buildRandom(new Random(1)));
    assertThrows(NullPointerException.class, () -> Iban.builder()
                                                       .countryCode("DE")
                                                       .buildRandom(null));
  }

  @Test
  void seedsAreReproducible() {
    IbanBuilder builder = Iban.builder()
                              .countryCode("IT")
                              .bankIdentifier("05428")
                              .nationalCheckDigits(true);
    assertEquals(builder.buildRandom(new Random(123)), builder.buildRandom(new Random(123)));
  }
}

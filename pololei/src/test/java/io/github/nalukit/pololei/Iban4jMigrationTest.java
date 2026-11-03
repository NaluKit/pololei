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

import io.github.nalukit.pololei.checkdigit.IbanCheckDigits;
import io.github.nalukit.pololei.error.BicError;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidBicException;
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples of docs/migration-iban4j.md; each test names the iban4j call it replaces.
 */
class Iban4jMigrationTest {

  @Test
  void parseAndFormatIban() {
    // Iban.valueOf(s), Iban.valueOf(s, IbanFormat.Default), toFormattedString()
    Iban iban = Iban.parse("DE89370400440532013000");
    assertEquals(iban, Iban.parse("DE89 3704 0044 0532 0130 00", IbanFormat.PRINT));
    assertEquals(iban, Iban.parse("DE89370400440532013000", IbanFormat.ELECTRONIC));
    assertEquals("DE89 3704 0044 0532 0130 00", iban.toPrintFormat());
    // getCountryCode(), getCheckDigit(), getBban(), getBankCode()
    assertEquals("DE", iban.countryCode());
    assertEquals("89", iban.checkDigits());
    assertEquals("370400440532013000", iban.bban());
    assertEquals(Optional.of("37040044"), iban.bankIdentifier());
    // getAccountNumber()
    assertEquals(Optional.of("0532013000"), iban.field(BbanField.ACCOUNT_NUMBER));
  }

  @Test
  void italianFields() {
    // getNationalCheckDigit(), getAccountNumber() of iban4j
    Iban iban = Iban.parse("IT60X0542811101000000123456");
    assertEquals(Optional.of("X"), iban.field(BbanField.NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.of("05428"), iban.bankIdentifier());
    assertEquals(Optional.of("11101"), iban.branchIdentifier());
    assertEquals(Optional.of("000000123456"), iban.field(BbanField.ACCOUNT_NUMBER));
    // pololei's accountNumber() keeps the registry meaning
    assertEquals("X000000123456", iban.accountNumber());
    // new Iban.Builder().countryCode(IT).nationalCheckDigit("X").bankCode(..).branchCode(..).accountNumber(..).build()
    assertEquals(iban, Iban.builder()
                           .countryCode("IT")
                           .bankIdentifier("05428")
                           .branchIdentifier("11101")
                           .field(BbanField.ACCOUNT_NUMBER, "000000123456")
                           .nationalCheckDigits(true)
                           .build());
    assertEquals(iban, Iban.builder()
                           .countryCode("IT")
                           .field(BbanField.NATIONAL_CHECK_DIGITS, "X")
                           .bankIdentifier("05428")
                           .branchIdentifier("11101")
                           .field(BbanField.ACCOUNT_NUMBER, "000000123456")
                           .build());
  }

  @Test
  void frenchFields() {
    // getBranchCode(), getAccountNumber(), getNationalCheckDigit() of iban4j
    Iban iban = Iban.parse("FR1420041010050500013M02606");
    assertEquals(Optional.of("20041"), iban.bankIdentifier());
    assertEquals(Optional.of("01005"), iban.field(BbanField.BRANCH_IDENTIFIER));
    assertEquals(Optional.empty(), iban.branchIdentifier());
    assertEquals(Optional.of("0500013M026"), iban.field(BbanField.ACCOUNT_NUMBER));
    assertEquals(Optional.of("06"), iban.field(BbanField.NATIONAL_CHECK_DIGITS));
  }

  @Test
  void separateNationalCheckDigits() {
    // getNationalCheckDigit(), getAccountNumber() of iban4j for the countries whose registry
    // structure ends with check digits
    String[][] cases = { { "DJ2100010000000154000100186", "86", "01540001001" },
                         { "EE382200221020145685", "5", "0022102014568" },
                         { "FO6264600001631634", "4", "000163163" },
                         { "HU42117730161111101800000000", "0", "6111110180000000" },
                         { "MR1300020001010000123456753", "53", "00001234567" },
                         { "SE4550000000058398257466", "6", "0000005839825746" },
                         { "TL380080012345678910157", "57", "00123456789101" },
                         { "XK051212012345678906", "06", "0123456789" } };
    for (String[] c : cases) {
      Iban iban = Iban.parse(c[0]);
      assertEquals(Optional.of(c[1]), iban.field(BbanField.NATIONAL_CHECK_DIGITS), c[0]);
      assertEquals(Optional.of(c[2]), iban.field(BbanField.ACCOUNT_NUMBER), c[0]);
      // new Iban.Builder().countryCode(..).bankCode(..).branchCode(..).accountNumber(..).nationalCheckDigit(..).build()
      IbanBuilder builder = Iban.builder()
                                .countryCode(iban.countryCode())
                                .bankIdentifier(iban.bankIdentifier()
                                                    .orElseThrow())
                                .field(BbanField.ACCOUNT_NUMBER, c[2])
                                .field(BbanField.NATIONAL_CHECK_DIGITS, c[1]);
      iban.branchIdentifier()
          .ifPresent(builder::branchIdentifier);
      assertEquals(iban, builder.build(), c[0]);
    }
  }

  @Test
  void checkDigitWithinRegistryIdentifier() {
    // AL and PL: iban4j's nationalCheckDigit is part of the registry branch or bank identifier
    Iban albania = Iban.parse("AL47212110090000000235698741");
    assertEquals(Optional.of("11009"), albania.branchIdentifier());
    assertEquals(Optional.of("9"), albania.field(BbanField.NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.of("0000000235698741"), albania.field(BbanField.ACCOUNT_NUMBER));
    Iban poland = Iban.parse("PL61109010140000071219812874");
    assertEquals(Optional.of("10901014"), poland.bankIdentifier());
    assertEquals(Optional.of("4"), poland.field(BbanField.NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.of("0000071219812874"), poland.field(BbanField.ACCOUNT_NUMBER));
    // the check digit is given with the identifier; a separate value must agree with it
    assertEquals(poland, Iban.builder()
                             .countryCode("PL")
                             .bankIdentifier("10901014")
                             .field(BbanField.NATIONAL_CHECK_DIGITS, "4")
                             .field(BbanField.ACCOUNT_NUMBER, "0000071219812874")
                             .build());
    assertEquals(IbanError.INVALID_BBAN, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                           .countryCode("PL")
                                                                                           .bankIdentifier("10901014")
                                                                                           .field(BbanField.NATIONAL_CHECK_DIGITS, "5")
                                                                                           .field(BbanField.ACCOUNT_NUMBER, "0000071219812874")
                                                                                           .build()).error());
  }

  @Test
  void turkishReservedPosition() {
    // iban4j's getNationalCheckDigit() of TR is the reserved position 6
    Iban iban = Iban.parse("TR330006100519786457841326");
    assertEquals(Optional.of("0"), iban.field(BbanField.RESERVED));
    assertEquals(Optional.empty(), iban.field(BbanField.NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.of("0519786457841326"), iban.field(BbanField.ACCOUNT_NUMBER));
    assertEquals("00519786457841326", iban.accountNumber()); // registry meaning unchanged
  }

  @Test
  void seychellesCurrency() {
    // iban4j's getAccountType() of SC is the currency
    Iban iban = Iban.parse("SC18SSCB11010000000000001497USD");
    assertEquals(Optional.of("USD"), iban.field(BbanField.CURRENCY));
    assertEquals(Optional.empty(), iban.field(BbanField.ACCOUNT_TYPE));
  }

  @Test
  void errorHandling() {
    // try { IbanUtil.validate(s); } catch (InvalidCheckDigitException e) { ... }
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.parse("DE88370400440532013000"));
    assertEquals(IbanError.INVALID_CHECK_DIGITS, exception.error());
    // IbanFormatException BBAN_LENGTH with getExpected()/getActual()
    ValidationResult<Iban, IbanError> length = Iban.validate("DE8937040044053201300");
    assertEquals(Optional.of(IbanError.INVALID_LENGTH), length.error());
    assertEquals(OptionalInt.of(22), length.expectedLength());
    assertEquals(OptionalInt.of(21), length.actualLength());
    // UnsupportedCountryException
    assertEquals(Optional.of(IbanError.UNSUPPORTED_COUNTRY), Iban.validate("GF7620041010050500013M02606")
                                                                 .error());
    // IbanUtil.isValid(s)
    assertFalse(Iban.isValid("DE88370400440532013000"));
    // BicUtil.validate(s) throwing BicFormatException
    assertEquals(BicError.INVALID_LENGTH, assertThrows(InvalidBicException.class, () -> Bic.parse("DEUTDEF")).error());
  }

  @Test
  void countryRules() {
    // IbanUtil.isValid(s, ValidationConfig.builder().enableCountryRules(true).build())
    IbanValidator validator = IbanValidator.standard()
                                           .nationalCheck(NationalCheckMode.IF_SUPPORTED);
    assertTrue(validator.isValid("FR1420041010050500013M02606"));
    assertFalse(validator.isValid("FR8420041010050500013M02607"));
    // BbanStructure.hasNationalCheckDigit(CountryCode.FR)
    assertTrue(IbanRegistry.find("FR")
                           .orElseThrow()
                           .bbanLayout()
                           .contains(BbanField.NATIONAL_CHECK_DIGITS));
    assertTrue(NationalChecks.find("FR")
                             .isPresent());
  }

  @Test
  void builderAndUtilities() {
    // new Iban.Builder().countryCode(GB).bankCode(..).branchCode(..).accountNumber(..).build()
    assertEquals("GB29NWBK60161331926819", Iban.builder()
                                               .countryCode("GB")
                                               .bankIdentifier("NWBK")
                                               .branchIdentifier("601613")
                                               .field(BbanField.ACCOUNT_NUMBER, "31926819")
                                               .build()
                                               .toString());
    // leftPadding(true)
    assertEquals("DE89370400440532013000", Iban.builder()
                                               .countryCode("DE")
                                               .bankIdentifier("37040044")
                                               .field(BbanField.ACCOUNT_NUMBER, "532013000")
                                               .padAccountNumber(true)
                                               .build()
                                               .toString());
    // new Iban.Builder(random).countryCode(..).bankCode(..).buildRandom()
    assertEquals(Optional.of("NWBK"), Iban.builder()
                                          .countryCode("GB")
                                          .bankIdentifier("NWBK")
                                          .buildRandom(new Random(1))
                                          .bankIdentifier());
    // IbanUtil.calculateCheckDigit, isSupportedCountry, getIbanLength
    assertEquals("89", IbanCheckDigits.calculate("DE", "370400440532013000"));
    assertTrue(IbanRegistry.isSupported("DE"));
    assertEquals(22, IbanRegistry.find("DE")
                                 .orElseThrow()
                                 .ibanLength());
    // IbanUtil.validateBban, BbanStructure.validateBbanEntry
    assertTrue(Bban.validate("DE", "370400440532013000")
                   .isValid());
    assertTrue(Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "37040044")
                   .isValid());
  }

  @Test
  void bic() {
    // Bic.valueOf(s), getBankCode(), getCountryCode(), getLocationCode(), getBranchCode(), BicUtil.hasBranchCode(s)
    Bic bic = Bic.parse("DEUTDEFF500");
    assertEquals("DEUT", bic.institutionCode());
    assertEquals("DE", bic.countryCode());
    assertEquals("FF", bic.locationCode());
    assertEquals(Optional.of("500"), bic.branchCode());
    assertTrue(bic.branchCode()
                  .isPresent());
  }
}

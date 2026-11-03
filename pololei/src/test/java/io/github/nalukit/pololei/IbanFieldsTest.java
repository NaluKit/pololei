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
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_NUMBER;
import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_TYPE;
import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER_EXTENSION;
import static io.github.nalukit.pololei.registry.BbanField.BRANCH_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.CURRENCY;
import static io.github.nalukit.pololei.registry.BbanField.NATIONAL_CHECK_DIGITS;
import static io.github.nalukit.pololei.registry.BbanField.OWNER_ACCOUNT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The national BBAN fields (GAP-03) and their relation to {@link Iban#accountNumber()} (GAP-04).
 */
class IbanFieldsTest {

  @Test
  void italianAccountNumber() {
    Iban iban = Iban.parse("IT60X0542811101000000123456");
    // unchanged: all characters outside bank and branch identifier
    assertEquals("X000000123456", iban.accountNumber());
    assertEquals(Optional.of("000000123456"), iban.field(ACCOUNT_NUMBER));
    assertEquals(Optional.of("X"), iban.field(NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.of("05428"), iban.field(BANK_IDENTIFIER));
    assertEquals(Optional.of("11101"), iban.field(BRANCH_IDENTIFIER));
  }

  @Test
  void spanishAccountNumber() {
    Iban iban = Iban.parse("ES9121000418450200051332");
    assertEquals("450200051332", iban.accountNumber());
    assertEquals(Optional.of("0200051332"), iban.field(ACCOUNT_NUMBER));
    assertEquals(Optional.of("45"), iban.field(NATIONAL_CHECK_DIGITS));
  }

  @Test
  void frenchFields() {
    Iban iban = Iban.parse("FR1420041010050500013M02606");
    // unchanged: the registry defines no French branch, so accountNumber() contains it
    assertEquals(Optional.empty(), iban.branchIdentifier());
    assertEquals("01005" + "0500013M026" + "06", iban.accountNumber());
    assertEquals(Map.of(BANK_IDENTIFIER, "20041", BRANCH_IDENTIFIER, "01005", ACCOUNT_NUMBER, "0500013M026", NATIONAL_CHECK_DIGITS, "06"), iban.fields());
    assertEquals(Optional.of("01005"), iban.field(BRANCH_IDENTIFIER));
    assertEquals(Optional.of("0500013M026"), iban.field(ACCOUNT_NUMBER));
    assertEquals(Optional.of("06"), iban.field(NATIONAL_CHECK_DIGITS));
  }

  @Test
  void brazilianFields() {
    Iban iban = Iban.parse("BR1800360305000010009795493C1");
    assertEquals(Optional.of("00360305"), iban.field(BANK_IDENTIFIER));
    assertEquals(Optional.of("00001"), iban.field(BRANCH_IDENTIFIER));
    assertEquals(Optional.of("0009795493"), iban.field(ACCOUNT_NUMBER));
    assertEquals(Optional.of("C"), iban.field(ACCOUNT_TYPE));
    assertEquals(Optional.of("1"), iban.field(OWNER_ACCOUNT_TYPE));
  }

  @Test
  void seychellesFields() {
    Iban iban = Iban.parse("SC18SSCB11010000000000001497USD");
    assertEquals(Optional.of("SSCB11"), iban.field(BANK_IDENTIFIER));
    assertEquals(Optional.of("11"), iban.field(BANK_IDENTIFIER_EXTENSION));
    assertEquals(Optional.of("01"), iban.field(BRANCH_IDENTIFIER));
    assertEquals(Optional.of("0000000000001497"), iban.field(ACCOUNT_NUMBER));
    assertEquals(Optional.of("USD"), iban.field(CURRENCY));
  }

  @Test
  void missingFieldsAreEmpty() {
    Iban iban = Iban.parse("DE89370400440532013000");
    assertEquals(Optional.of("0532013000"), iban.field(ACCOUNT_NUMBER));
    assertEquals(iban.accountNumber(), iban.field(ACCOUNT_NUMBER)
                                           .orElseThrow());
    assertEquals(Optional.empty(), iban.field(NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.empty(), iban.field(BRANCH_IDENTIFIER));
    assertThrows(UnsupportedOperationException.class, () -> iban.fields()
                                                                .clear());
  }

  /** Builder and parser reconstruct the same BBAN from the fields, including leading zeros. */
  @ParameterizedTest
  @ValueSource(strings = { "IT60X0542811101000000123456", "FR1420041010050500013M02606", "ES9121000418450200051332", "BR1800360305000010009795493C1",
                           "SC18SSCB11010000000000001497USD", "IS140159260076545510730339", "BG80BNBG96611020345678", "PT50000201231234567890154", "GB29NWBK60161331926819" })
  void roundTrip(String input) {
    Iban        iban    = Iban.parse(input);
    IbanBuilder builder = Iban.builder()
                              .countryCode(iban.countryCode());
    iban.fields()
        .forEach(builder::field);
    assertEquals(iban, builder.build());
  }

  @Test
  void roundTripOfAllRegistryExamples() {
    for (IbanCountry country : IbanRegistry.countries()) {
      Iban        iban    = Iban.parse(country.ibanExample());
      IbanBuilder builder = Iban.builder()
                                .countryCode(country.countryCode());
      iban.fields()
          .forEach(builder::field);
      assertEquals(iban, builder.build(), country.countryCode());
    }
  }

  @Test
  void frenchBuilderWithBranchAndNationalCheckDigits() {
    Iban iban = Iban.builder()
                    .countryCode("FR")
                    .bankIdentifier("20041")
                    .branchIdentifier("01005")
                    .field(ACCOUNT_NUMBER, "0500013M026")
                    .nationalCheckDigits(true)
                    .build();
    assertEquals("FR1420041010050500013M02606", iban.toString());
  }

  @Test
  void missingFieldIsReported() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("FR")
                                                                                        .bankIdentifier("20041")
                                                                                        .branchIdentifier("01005")
                                                                                        .field(ACCOUNT_NUMBER, "0500013M026")
                                                                                        .build());
    assertEquals(IbanError.INVALID_LENGTH, exception.error());
    assertEquals("national check digits of FR must have 2 characters, but has 0", exception.getMessage());
  }

  @Test
  void fieldErrors() {
    InvalidIbanException undefined = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("DE")
                                                                                        .bankIdentifier("37040044")
                                                                                        .field(ACCOUNT_NUMBER, "0532013000")
                                                                                        .field(NATIONAL_CHECK_DIGITS, "12")
                                                                                        .build());
    assertEquals(IbanError.UNDEFINED_FIELD, undefined.error());
    assertEquals("the BBAN of DE has no national check digits", undefined.getMessage());

    InvalidIbanException type = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                   .countryCode("ES")
                                                                                   .bankIdentifier("2100")
                                                                                   .branchIdentifier("0418")
                                                                                   .field(NATIONAL_CHECK_DIGITS, "4X")
                                                                                   .field(ACCOUNT_NUMBER, "0200051332")
                                                                                   .build());
    assertEquals(IbanError.INVALID_BBAN, type.error());
    // positions are IBAN positions: the check digits start at position 13
    assertEquals(OptionalInt.of(14), type.result()
                                         .position());

    InvalidIbanException length = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                     .countryCode("IT")
                                                                                     .field(NATIONAL_CHECK_DIGITS, "X")
                                                                                     .bankIdentifier("05428")
                                                                                     .branchIdentifier("11101")
                                                                                     .field(ACCOUNT_NUMBER, "0000001234567")
                                                                                     .build());
    assertEquals(IbanError.INVALID_LENGTH, length.error());
    assertEquals("account number of IT must have 12 characters, but has 13", length.getMessage());

    InvalidIbanException contradiction = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                            .countryCode("SC")
                                                                                            .bankIdentifier("SSCB11")
                                                                                            .field(BANK_IDENTIFIER_EXTENSION, "12")
                                                                                            .branchIdentifier("01")
                                                                                            .field(ACCOUNT_NUMBER, "0000000000001497")
                                                                                            .field(CURRENCY, "USD")
                                                                                            .build());
    assertEquals(IbanError.INVALID_BBAN, contradiction.error());
  }

  @Test
  void accountNumberAndFieldsCannotBeMixed() {
    assertThrows(IllegalStateException.class, () -> Iban.builder()
                                                        .countryCode("IT")
                                                        .bankIdentifier("05428")
                                                        .branchIdentifier("11101")
                                                        .accountNumber("X000000123456")
                                                        .field(NATIONAL_CHECK_DIGITS, "X")
                                                        .build());
    assertThrows(IllegalStateException.class, () -> Iban.builder()
                                                        .countryCode("FR")
                                                        .field(NATIONAL_CHECK_DIGITS, "06")
                                                        .nationalCheckDigits(true)
                                                        .build());
  }

  @Test
  void accountNumberOfRegistryPartsIsUnchanged() {
    // accountNumber() in the builder keeps its meaning: everything outside bank and branch
    assertEquals("IT60X0542811101000000123456", Iban.builder()
                                                    .countryCode("IT")
                                                    .bankIdentifier("05428")
                                                    .branchIdentifier("11101")
                                                    .accountNumber("X000000123456")
                                                    .build()
                                                    .toString());
    assertEquals("FR1420041010050500013M02606", Iban.builder()
                                                    .countryCode("FR")
                                                    .bankIdentifier("20041")
                                                    .accountNumber("010050500013M02606")
                                                    .build()
                                                    .toString());
  }
}

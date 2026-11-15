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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IbanBuilderTest {

  @Test
  void buildFromBban() {
    assertEquals("AT611904300234573201", Iban.builder()
                                             .countryCode("AT")
                                             .bban("1904300234573201")
                                             .build()
                                             .toString());
  }

  @Test
  void buildFromBankAndAccount() {
    assertEquals("DE89370400440532013000", Iban.builder()
                                               .countryCode("DE")
                                               .bankIdentifier("37040044")
                                               .accountNumber("0532013000")
                                               .build()
                                               .toString());
  }

  @Test
  void buildFromBankBranchAndAccount() {
    assertEquals("GB29NWBK60161331926819", Iban.builder()
                                               .countryCode("GB")
                                               .bankIdentifier("NWBK")
                                               .branchIdentifier("601613")
                                               .accountNumber("31926819")
                                               .build()
                                               .toString());
  }

  @Test
  void accountNumberFillsPositionsInFrontOfBankIdentifier() {
    // Italy: 1!a (CIN) 5!n (bank) 5!n (branch) 12!c (account)
    Iban iban = Iban.parse("IT60X0542811101000000123456");
    assertEquals("X000000123456", iban.accountNumber());
    assertEquals(iban, Iban.builder()
                           .countryCode("IT")
                           .bankIdentifier("05428")
                           .branchIdentifier("11101")
                           .accountNumber("X000000123456")
                           .build());
  }

  @Test
  void accountNumberIsPaddedOnRequest() {
    assertEquals("DE89370400440532013000", Iban.builder()
                                               .countryCode("de")
                                               .bankIdentifier("37040044")
                                               .accountNumber("532013000")
                                               .padAccountNumber(true)
                                               .build()
                                               .toString());
  }

  @Test
  void shortAccountNumberWithoutPadding() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("DE")
                                                                                        .bankIdentifier("37040044")
                                                                                        .accountNumber("532013000")
                                                                                        .build());
    assertEquals(IbanError.INVALID_LENGTH, exception.error());
    assertEquals("account number of DE must have 10 characters, but has 9", exception.getMessage());
  }

  @Test
  void wrongBankIdentifierLength() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("DE")
                                                                                        .bankIdentifier("3704004")
                                                                                        .accountNumber("0532013000")
                                                                                        .build());
    assertEquals(IbanError.INVALID_LENGTH, exception.error());
  }

  @Test
  void branchIdentifierForCountryWithoutBranch() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                        .countryCode("DE")
                                                                                        .bankIdentifier("37040044")
                                                                                        .branchIdentifier("1")
                                                                                        .accountNumber("0532013000")
                                                                                        .build());
    assertEquals(IbanError.INVALID_BBAN, exception.error());
  }

  @Test
  void bbanNotMatchingStructure() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.of("DE", "37040044053201300A"));
    assertEquals(IbanError.INVALID_BBAN, exception.error());
  }

  @Test
  void invalidCharacterInBban() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.of("DE", "3704004405320130-0"));
    assertEquals(IbanError.INVALID_CHARACTER, exception.error());
  }

  @Test
  void unsupportedCountry() {
    assertEquals(IbanError.UNSUPPORTED_COUNTRY, assertThrows(InvalidIbanException.class, () -> Iban.of("US", "123")).error());
    assertEquals(IbanError.INVALID_COUNTRY_CODE, assertThrows(InvalidIbanException.class, () -> Iban.of(null, "123")).error());
  }

  @Test
  void invalidCountryInputIsNotEchoed() {
    String iban = "DE89370400440532013000\nINJECTED";
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                         .countryCode(iban)
                                                                                         .bban("370400440532013000")
                                                                                         .build());
    assertEquals(IbanError.INVALID_COUNTRY_CODE, exception.error());
    assertFalse(exception.getMessage().contains("DE89"));
    assertFalse(exception.getMessage().contains("INJECTED"));
    assertFalse(exception.getMessage().contains("\n"));
    assertFalse(exception.result().message().orElseThrow().contains("DE89"));
    for (String code : new String[] { "DEU", "12", "D", " ", "D1", "DE89370400440532013000" }) {
      assertEquals(IbanError.INVALID_COUNTRY_CODE, assertThrows(InvalidIbanException.class, () -> Iban.of(code, "123")).error(), code);
      assertEquals(IbanError.INVALID_COUNTRY_CODE, assertThrows(InvalidIbanException.class, () -> Iban.builder().countryCode(code).buildRandom(new java.util.Random(1))).error(), code);
      assertEquals(IbanError.INVALID_COUNTRY_CODE, assertThrows(InvalidIbanException.class, () -> Iban.random(code, new java.util.Random(1))).error(), code);
    }
    assertEquals(IbanError.UNSUPPORTED_COUNTRY, assertThrows(InvalidIbanException.class, () -> Iban.random("us", new java.util.Random(1))).error());
    // documented normalization stays usable
    Iban.builder().countryCode(" d e ").bban("370400440532013000").build();
    Iban.builder().countryCode("de").buildRandom(new java.util.Random(1));
  }

  @Test
  void randomBuilderRejectsInvalidFixedPartsLikeBuild() {
    for (String[] parts : new String[][] { { "05428", "X0000001234!6" }, { "05428", "00000001234!6" }, { "0542A", "0000000123456" }, { "05428", "00000012345" } }) {
      String account = parts[1];
      java.util.function.Supplier<IbanBuilder> builder = () -> Iban.builder()
                                                                   .countryCode("IT")
                                                                   .bankIdentifier(parts[0])
                                                                   .branchIdentifier("11101")
                                                                   .accountNumber(account)
                                                                   .nationalCheckDigits(true);
      InvalidIbanException expected = assertThrows(InvalidIbanException.class, () -> builder.get().build());
      InvalidIbanException actual   = assertThrows(InvalidIbanException.class, () -> builder.get().buildRandom(new java.util.Random(42)));
      assertEquals(expected.error(), actual.error(), account + parts[0]);
      assertEquals(expected.result().position(), actual.result().position(), account + parts[0]);
    }
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                         .countryCode("IT")
                                                                                         .bankIdentifier("05428")
                                                                                         .branchIdentifier("11101")
                                                                                         .accountNumber("X0000001234!6")
                                                                                         .nationalCheckDigits(true)
                                                                                         .buildRandom(new java.util.Random(42)));
    assertEquals(IbanError.INVALID_CHARACTER, exception.error());
    assertEquals(java.util.OptionalInt.of(26), exception.result().position());
    // valid fixed parts still work
    Iban.builder().countryCode("IT").bankIdentifier("05428").branchIdentifier("11101").nationalCheckDigits(true).buildRandom(new java.util.Random(42));
  }

  @Test
  void bbanAndPartsCannotBeCombined() {
    assertThrows(IllegalStateException.class, () -> Iban.builder()
                                                        .countryCode("DE")
                                                        .bban("370400440532013000")
                                                        .bankIdentifier("37040044")
                                                        .build());
  }
}

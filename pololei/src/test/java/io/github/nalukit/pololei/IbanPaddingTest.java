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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Padding of bank identifier, branch identifier and account number (GAP-09).
 */
class IbanPaddingTest {

  @Test
  void germanAccountNumber() {
    // short, with padding
    assertEquals("DE89370400440532013000", german("532013000", true));
    // matching length, with and without padding
    assertEquals("DE89370400440532013000", german("0532013000", true));
    assertEquals("DE89370400440532013000", german("0532013000", false));
    // short without padding: unchanged behaviour
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> german("532013000", false)).error());
    // too long, with and without padding
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> german("10532013000", true)).error());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> german("10532013000", false)).error());
  }

  @Test
  void germanAccountNumberAsField() {
    assertEquals("DE89370400440532013000", Iban.builder()
                                               .countryCode("DE")
                                               .bankIdentifier("37040044")
                                               .field(BbanField.ACCOUNT_NUMBER, "532013000")
                                               .padAccountNumber(true)
                                               .build()
                                               .toString());
  }

  private static String german(String account,
                               boolean padding) {
    return Iban.builder()
               .countryCode("DE")
               .bankIdentifier("37040044")
               .accountNumber(account)
               .padAccountNumber(padding)
               .build()
               .toString();
  }

  @Test
  void austrianBankIdentifier() {
    // AT: 5!n bank identifier, 11!n account number
    assertEquals("AT611904300234573201", austrian("19043", true));
    assertEquals("AT611904300234573201", austrian("19043", false));
    Iban padded = Iban.builder()
                      .countryCode("AT")
                      .bankIdentifier("1904")
                      .padBankIdentifier(true)
                      .accountNumber("00234573201")
                      .build();
    assertEquals("01904", padded.bankIdentifier()
                                .orElseThrow());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> austrian("1904", false)).error());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> austrian("190430", true)).error());
  }

  private static String austrian(String bank,
                                 boolean padding) {
    return Iban.builder()
               .countryCode("AT")
               .bankIdentifier(bank)
               .padBankIdentifier(padding)
               .accountNumber("00234573201")
               .build()
               .toString();
  }

  @Test
  void britishBranchIdentifier() {
    assertEquals("GB29NWBK60161331926819", british("601613", true));
    assertEquals("GB29NWBK60161331926819", british("601613", false));
    assertEquals("001613", Iban.builder()
                               .countryCode("GB")
                               .bankIdentifier("NWBK")
                               .branchIdentifier("1613")
                               .padBranchIdentifier(true)
                               .accountNumber("31926819")
                               .build()
                               .branchIdentifier()
                               .orElseThrow());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> british("1613", false)).error());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> british("6016130", true)).error());
  }

  private static String british(String branch,
                                boolean padding) {
    return Iban.builder()
               .countryCode("GB")
               .bankIdentifier("NWBK")
               .branchIdentifier(branch)
               .padBranchIdentifier(padding)
               .accountNumber("31926819")
               .build()
               .toString();
  }

  @Test
  void paddingCharacter() {
    // GB bank identifier is 4!a: padding with a letter
    assertEquals("XXBK", Iban.builder()
                             .countryCode("GB")
                             .bankIdentifier("BK")
                             .padBankIdentifier(true)
                             .paddingCharacter('x')
                             .branchIdentifier("601613")
                             .accountNumber("31926819")
                             .build()
                             .bankIdentifier()
                             .orElseThrow());
    // a letter cannot pad the numeric German account number
    assertThrows(IllegalArgumentException.class, () -> Iban.builder()
                                                           .countryCode("DE")
                                                           .bankIdentifier("37040044")
                                                           .accountNumber("532013000")
                                                           .padAccountNumber(true)
                                                           .paddingCharacter('X')
                                                           .build());
    // nor a digit the GB bank identifier
    assertThrows(IllegalArgumentException.class, () -> Iban.builder()
                                                           .countryCode("GB")
                                                           .bankIdentifier("BK")
                                                           .padBankIdentifier(true)
                                                           .branchIdentifier("601613")
                                                           .accountNumber("31926819")
                                                           .build());
    assertThrows(IllegalArgumentException.class, () -> Iban.builder()
                                                           .paddingCharacter('-'));
    assertThrows(IllegalArgumentException.class, () -> Iban.builder()
                                                           .paddingCharacter(' '));
  }

  @Test
  void emptyAndNullInputs() {
    // padding does not turn a missing value into zeros
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                              .countryCode("AT")
                                                                                              .bankIdentifier(null)
                                                                                              .padBankIdentifier(true)
                                                                                              .accountNumber("00234573201")
                                                                                              .build()).error());
    assertEquals(IbanError.INVALID_LENGTH, assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                              .countryCode("DE")
                                                                                              .bankIdentifier("37040044")
                                                                                              .accountNumber("")
                                                                                              .padAccountNumber(true)
                                                                                              .build()).error());
  }

  @Test
  void compositeRemainderIsNotPadded() {
    // IT: accountNumber() contains the CIN in front of the account number; padding would shift it
    InvalidIbanException italian = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                      .countryCode("IT")
                                                                                      .bankIdentifier("05428")
                                                                                      .branchIdentifier("11101")
                                                                                      .accountNumber("X123456")
                                                                                      .padAccountNumber(true)
                                                                                      .build());
    assertEquals(IbanError.INVALID_LENGTH, italian.error());
    // ES: national check digits in front of the account number
    assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                       .countryCode("ES")
                                                       .bankIdentifier("2100")
                                                       .branchIdentifier("0418")
                                                       .accountNumber("45200051332")
                                                       .padAccountNumber(true)
                                                       .build());
    // the national fields can be padded instead
    assertEquals("IT60X0542811101000000123456", Iban.builder()
                                                    .countryCode("IT")
                                                    .field(BbanField.NATIONAL_CHECK_DIGITS, "X")
                                                    .bankIdentifier("05428")
                                                    .branchIdentifier("11101")
                                                    .field(BbanField.ACCOUNT_NUMBER, "123456")
                                                    .padAccountNumber(true)
                                                    .build()
                                                    .toString());
    assertEquals("ES9121000418450200051332", Iban.builder()
                                                 .countryCode("ES")
                                                 .bankIdentifier("2100")
                                                 .branchIdentifier("418")
                                                 .padBranchIdentifier(true)
                                                 .field(BbanField.NATIONAL_CHECK_DIGITS, "45")
                                                 .field(BbanField.ACCOUNT_NUMBER, "200051332")
                                                 .padAccountNumber(true)
                                                 .build()
                                                 .toString());
    // a complete remainder is accepted with padding switched on
    assertEquals("IT60X0542811101000000123456", Iban.builder()
                                                    .countryCode("IT")
                                                    .bankIdentifier("05428")
                                                    .branchIdentifier("11101")
                                                    .accountNumber("X000000123456")
                                                    .padAccountNumber(true)
                                                    .build()
                                                    .toString());
  }
}

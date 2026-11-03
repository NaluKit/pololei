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
package io.github.nalukit.pololeitest.client;

import com.google.gwt.junit.client.GWTTestCase;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.IbanFormat;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.registry.CharacterType;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.Random;

/**
 * Runs the IBAN API compiled to JavaScript.
 */
public class GwtTestIban
    extends GWTTestCase {

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  public void testRegistryExamplesAreValid() {
    for (IbanCountry country : IbanRegistry.countries()) {
      assertTrue(country.ibanExample(), Iban.isValid(country.ibanExample()));
    }
  }

  public void testParseAndFormat() {
    Iban iban = Iban.parse("de89 3704 0044 0532 0130 00");
    assertEquals("DE89370400440532013000", iban.toString());
    assertEquals("DE89 3704 0044 0532 0130 00", iban.toPrintFormat());
    assertEquals("37040044", iban.bankIdentifier()
                                 .orElseThrow());
    assertEquals("0532013000", iban.accountNumber());
    assertTrue(iban.isSepa());
  }

  public void testValidationErrors() {
    assertEquals(IbanError.EMPTY, Iban.validate(" ")
                                      .error()
                                      .orElseThrow());
    assertEquals(IbanError.INVALID_CHARACTER, Iban.validate("DE89-370400440532013000")
                                                  .error()
                                                  .orElseThrow());
    assertEquals(IbanError.UNSUPPORTED_COUNTRY, Iban.validate("US89370400440532013000")
                                                    .error()
                                                    .orElseThrow());
    assertEquals(IbanError.INVALID_LENGTH, Iban.validate("DE8937040044053201300")
                                               .error()
                                               .orElseThrow());
    assertEquals(IbanError.INVALID_BBAN, Iban.validate("DE8937040044053201300A")
                                             .error()
                                             .orElseThrow());
    assertEquals(IbanError.INVALID_CHECK_DIGITS, Iban.validate("DE88370400440532013000")
                                                     .error()
                                                     .orElseThrow());
    assertEquals(IbanError.INVALID_FORMAT, Iban.validate("DE89370400440532013000", IbanFormat.PRINT)
                                               .error()
                                               .orElseThrow());
    try {
      Iban.parse("DE88370400440532013000");
      fail("exception expected");
    } catch (InvalidIbanException e) {
      assertEquals(IbanError.INVALID_CHECK_DIGITS, e.error());
    }
  }

  public void testErrorDetails() {
    ValidationResult<Iban, IbanError> bban = Iban.validate("DE89 3704 0044 0532 013X 00");
    assertEquals(20, bban.position()
                         .getAsInt());
    assertEquals(CharacterType.DIGITS, bban.expectedType()
                                           .orElseThrow());
    ValidationResult<Iban, IbanError> length = Iban.validate("DE8937040044053201300");
    assertEquals(22, length.expectedLength()
                           .getAsInt());
    assertEquals(21, length.actualLength()
                           .getAsInt());
    assertFalse(length.position()
                      .isPresent());
  }

  public void testBuilderAndCheckDigits() {
    assertEquals("GB29NWBK60161331926819", Iban.builder()
                                               .countryCode("GB")
                                               .bankIdentifier("NWBK")
                                               .branchIdentifier("601613")
                                               .accountNumber("31926819")
                                               .build()
                                               .toString());
    assertEquals("GB29NWBK60161331926819", Iban.of("GB", "NWBK60161331926819")
                                               .toString());
  }

  public void testRandomIbansAreValid() {
    Random random = new Random(42);
    for (int i = 0; i < 200; i++) {
      Iban iban = Iban.random(random);
      assertTrue(iban.toString(), Iban.isValid(iban.toString()));
    }
  }
}

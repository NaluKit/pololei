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
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.CharacterType;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.BRANCH_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.NATIONAL_CHECK_DIGITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbanTest {

  @Test
  void germanBankIdentifier() {
    assertEquals(Optional.of("37040044"), Bban.validateField("DE", BANK_IDENTIFIER, "37040044")
                                              .value());
    ValidationResult<String, IbanError> tooShort = Bban.validateField("DE", BANK_IDENTIFIER, "3704004");
    assertEquals(Optional.of(IbanError.INVALID_LENGTH), tooShort.error());
    assertEquals(OptionalInt.of(8), tooShort.expectedLength());
    assertEquals(OptionalInt.of(7), tooShort.actualLength());
    ValidationResult<String, IbanError> letter = Bban.validateField("DE", BANK_IDENTIFIER, "3704004X");
    assertEquals(Optional.of(IbanError.INVALID_BBAN), letter.error());
    assertEquals(OptionalInt.of(8), letter.position());
    assertEquals(Optional.of(CharacterType.DIGITS), letter.expectedType());
    assertEquals("expected a digit at position 8 of the bank identifier of DE", letter.message()
                                                                                       .orElseThrow());
  }

  @Test
  void britishBankAndBranch() {
    assertTrue(Bban.validateField("GB", BANK_IDENTIFIER, "NWBK")
                   .isValid());
    assertTrue(Bban.validateField("gb", BANK_IDENTIFIER, "nwbk")
                   .isValid());
    assertEquals(Optional.of(IbanError.INVALID_BBAN), Bban.validateField("GB", BANK_IDENTIFIER, "NWB1")
                                                          .error());
    assertTrue(Bban.validateField("GB", BRANCH_IDENTIFIER, "601613")
                   .isValid());
    ValidationResult<String, IbanError> branch = Bban.validateField("GB", BRANCH_IDENTIFIER, "60161A");
    assertEquals(OptionalInt.of(6), branch.position());
    assertEquals(Optional.of(IbanError.INVALID_LENGTH), Bban.validateField("GB", BRANCH_IDENTIFIER, "6016")
                                                            .error());
  }

  @Test
  void nationalCheckDigitFields() {
    assertTrue(Bban.validateField("IT", NATIONAL_CHECK_DIGITS, "X")
                   .isValid());
    assertEquals(Optional.of(IbanError.INVALID_BBAN), Bban.validateField("IT", NATIONAL_CHECK_DIGITS, "1")
                                                          .error());
    assertTrue(Bban.validateField("ES", NATIONAL_CHECK_DIGITS, "45")
                   .isValid());
    assertEquals(Optional.of(CharacterType.DIGITS), Bban.validateField("ES", NATIONAL_CHECK_DIGITS, "4A")
                                                        .expectedType());
  }

  @Test
  void errors() {
    assertEquals(Optional.of(IbanError.UNDEFINED_FIELD), Bban.validateField("DE", NATIONAL_CHECK_DIGITS, "12")
                                                             .error());
    assertEquals(Optional.of(IbanError.EMPTY), Bban.validateField("DE", BANK_IDENTIFIER, null)
                                                   .error());
    assertEquals(Optional.of(IbanError.EMPTY), Bban.validateField("DE", BANK_IDENTIFIER, " ")
                                                   .error());
    assertEquals(Optional.of(IbanError.INVALID_CHARACTER), Bban.validateField("DE", BANK_IDENTIFIER, "3704-044")
                                                               .error());
    assertEquals(Optional.of(IbanError.UNSUPPORTED_COUNTRY), Bban.validateField("US", BANK_IDENTIFIER, "1")
                                                                 .error());
    assertEquals(Optional.of(IbanError.INVALID_COUNTRY_CODE), Bban.validateField(null, BANK_IDENTIFIER, "1")
                                                                  .error());
    assertEquals(Optional.of(IbanError.INVALID_COUNTRY_CODE), Bban.validateField("D1", BANK_IDENTIFIER, "1")
                                                                  .error());
    assertThrows(NullPointerException.class, () -> Bban.validateField("DE", null, "1"));
    assertEquals(Optional.of(IbanError.EMPTY), Bban.validate("DE", null)
                                                   .error());
    assertEquals(Optional.of(IbanError.INVALID_LENGTH), Bban.validate("DE", "37040044053201300")
                                                            .error());
  }

  @Test
  void wholeBban() {
    assertEquals(Optional.of("NWBK60161331926819"), Bban.validate("GB", "nwbk 6016 1331 9268 19")
                                                        .value());
    ValidationResult<String, IbanError> result = Bban.validate("DE", "3704004405320130X0");
    assertEquals(OptionalInt.of(17), result.position());
    assertEquals("expected a digit at position 17 (account number) of the BBAN of DE", result.message()
                                                                                               .orElseThrow());
  }

  /** Field and BBAN validation agree with the IBAN validation, positions differ by 4. */
  @Test
  void consistentWithIbanValidation() {
    Random random = new Random(11);
    for (IbanCountry country : IbanRegistry.countries()) {
      Iban iban = Iban.parse(country.ibanExample());
      assertTrue(Bban.validate(country.countryCode(), iban.bban())
                     .isValid());
      iban.fields()
          .forEach((field, value) -> assertTrue(Bban.validateField(country.countryCode(), field, value)
                                                    .isValid(), country.countryCode() + " " + field));
      // a character of the wrong type
      String bban  = iban.bban();
      int    index = random.nextInt(bban.length());
      char   wrong = country.bbanStructure()
                            .typeAt(index) == CharacterType.DIGITS ? 'A' : country.bbanStructure()
                                                                                  .typeAt(index) == CharacterType.UPPER_CASE_LETTERS ? '1' : '-';
      String changed = bban.substring(0, index) + wrong + bban.substring(index + 1);
      ValidationResult<String, IbanError> bbanResult = Bban.validate(country.countryCode(), changed);
      ValidationResult<Iban, IbanError>   ibanResult = Iban.validate(country.countryCode() + iban.checkDigits() + changed);
      assertFalse(bbanResult.isValid());
      assertEquals(ibanResult.error(), bbanResult.error(), country.countryCode());
      assertEquals(ibanResult.position()
                             .getAsInt(), bbanResult.position()
                                                    .getAsInt() + 4);
      BbanField field = country.bbanLayout()
                               .fieldAt(index)
                               .orElseThrow();
      int fieldStart = country.bbanLayout()
                              .find(field)
                              .orElseThrow()
                              .range()
                              .start();
      String value = iban.field(field)
                         .orElseThrow();
      int    offset = index - (fieldStart - 1);
      ValidationResult<String, IbanError> fieldResult = Bban.validateField(country.countryCode(), field, value.substring(0, offset) + wrong + value.substring(offset + 1));
      assertEquals(bbanResult.error(), fieldResult.error());
      assertEquals(offset + 1, fieldResult.position()
                                          .getAsInt());
    }
  }
}

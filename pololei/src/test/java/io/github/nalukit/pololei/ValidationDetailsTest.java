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
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidBicException;
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.registry.CharacterType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationDetailsTest {

  @Test
  void validResultHasNoDetails() {
    ValidationResult<Iban, IbanError> result = Iban.validate("DE89370400440532013000");
    assertEquals(OptionalInt.empty(), result.position());
    assertEquals(OptionalInt.empty(), result.expectedLength());
    assertEquals(OptionalInt.empty(), result.actualLength());
    assertEquals(Optional.empty(), result.expectedType());
  }

  @Test
  void invalidCharacter() {
    ValidationResult<Iban, IbanError> result = Iban.validate("DE89 3704-0044 0532 0130 00");
    assertEquals(OptionalInt.of(9), result.position());
    assertEquals(Optional.of(CharacterType.ALPHANUMERIC), result.expectedType());
  }

  @Test
  void invalidCountryCode() {
    ValidationResult<Iban, IbanError> result = Iban.validate("D189370400440532013000");
    assertEquals(OptionalInt.of(2), result.position());
    assertEquals(Optional.of(CharacterType.UPPER_CASE_LETTERS), result.expectedType());

    ValidationResult<Iban, IbanError> tooShort = Iban.validate("D");
    assertEquals(OptionalInt.empty(), tooShort.position());
    assertEquals(OptionalInt.of(2), tooShort.expectedLength());
    assertEquals(OptionalInt.of(1), tooShort.actualLength());
  }

  @Test
  void unsupportedCountry() {
    assertEquals(OptionalInt.of(1), Iban.validate("US89370400440532013000")
                                        .position());
  }

  @Test
  void invalidLength() {
    ValidationResult<Iban, IbanError> result = Iban.validate("DE89 3704 0044 0532 0130 0");
    assertEquals(OptionalInt.of(22), result.expectedLength());
    assertEquals(OptionalInt.of(21), result.actualLength());
    assertEquals(OptionalInt.empty(), result.position());
  }

  @Test
  void invalidBban() {
    ValidationResult<Iban, IbanError> result = Iban.validate("DE89370400440532013X00");
    assertEquals(OptionalInt.of(20), result.position());
    assertEquals(Optional.of(CharacterType.DIGITS), result.expectedType());
  }

  @Test
  void checkDigits() {
    ValidationResult<Iban, IbanError> notDigits = Iban.validate("DE8X370400440532013000");
    assertEquals(OptionalInt.of(4), notDigits.position());
    assertEquals(Optional.of(CharacterType.DIGITS), notDigits.expectedType());

    assertEquals(OptionalInt.of(3), Iban.validate("DE88370400440532013000")
                                        .position());
  }

  @Test
  void exceptionCarriesResult() {
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> Iban.parse("DE89370400440532013X00"));
    assertEquals(IbanError.INVALID_BBAN, exception.error());
    assertEquals(OptionalInt.of(20), exception.result()
                                              .position());
    assertEquals(exception.getMessage(), exception.result()
                                                  .message()
                                                  .orElseThrow());
  }

  @Test
  void builderErrorsCarryDetails() {
    InvalidIbanException length = assertThrows(InvalidIbanException.class, () -> Iban.builder()
                                                                                     .countryCode("DE")
                                                                                     .bankIdentifier("3704004")
                                                                                     .accountNumber("0532013000")
                                                                                     .build());
    assertEquals(OptionalInt.of(8), length.result()
                                          .expectedLength());
    assertEquals(OptionalInt.of(7), length.result()
                                          .actualLength());

    InvalidIbanException character = assertThrows(InvalidIbanException.class, () -> Iban.of("DE", "3704004405320130-0"));
    assertEquals(OptionalInt.of(21), character.result()
                                              .position());
  }

  @Test
  void bicDetails() {
    ValidationResult<Bic, BicError> length = Bic.validate("DEUTDEFF5");
    assertEquals(OptionalInt.of(11), length.expectedLength());
    assertEquals(OptionalInt.of(9), length.actualLength());
    assertEquals(OptionalInt.of(8), Bic.validate("DEUTDE")
                                       .expectedLength());

    ValidationResult<Bic, BicError> character = Bic.validate("DEUT1EFF");
    assertEquals(OptionalInt.of(5), character.position());
    assertEquals(Optional.of(CharacterType.UPPER_CASE_LETTERS), character.expectedType());
    assertEquals("invalid character '1' at position 5, expected a letter", character.message()
                                                                                    .orElseThrow());

    assertEquals(OptionalInt.of(5), Bic.validate("DEUTAAFF")
                                       .position());

    InvalidBicException exception = assertThrows(InvalidBicException.class, () -> Bic.parse("DEUTAAFF"));
    assertSame(BicError.UNKNOWN_COUNTRY, exception.error());
    assertEquals(OptionalInt.of(5), exception.result()
                                             .position());
  }
}

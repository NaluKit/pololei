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
import io.github.nalukit.pololei.national.NationalCheckOutcome;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanRange;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanValidatorTest {

  private static final String VALID_FR    = "FR1420041010050500013M02606";
  // RIB key 07 instead of 06, IBAN check digits recalculated: valid for the international checks only
  private static final String NATIONAL_FR = "FR8420041010050500013M02607";

  private static final IbanValidator NATIONAL = IbanValidator.standard()
                                                             .nationalCheck(NationalCheckMode.IF_SUPPORTED);
  private static final IbanValidator REQUIRED = IbanValidator.standard()
                                                             .nationalCheck(NationalCheckMode.REQUIRED);

  @Test
  void frenchRibKey() {
    assertTrue(Iban.isValid(VALID_FR));
    assertTrue(Iban.isValid(NATIONAL_FR));
    assertTrue(IbanValidator.standard()
                            .isValid(NATIONAL_FR));
    assertTrue(NATIONAL.isValid(VALID_FR));
    assertFalse(NATIONAL.isValid(NATIONAL_FR));
  }

  @Test
  void standardBehavesLikeIban() {
    for (String input : new String[] { VALID_FR, NATIONAL_FR, "DE89 3704 0044 0532 0130 00", "DE88370400440532013000", "", "XX" }) {
      ValidationResult<Iban, IbanError> expected = Iban.validate(input);
      ValidationResult<Iban, IbanError> actual   = IbanValidator.standard()
                                                                .validate(input);
      assertEquals(expected.toString(), actual.toString());
      assertEquals(NationalCheckStatus.NOT_PERFORMED, actual.nationalCheck());
    }
    assertSame(IbanValidator.standard(), IbanValidator.standard());
    assertEquals(NationalCheckMode.OFF, IbanValidator.standard()
                                                     .nationalCheckMode());
  }

  @Test
  void failedNationalCheckDetails() {
    ValidationResult<Iban, IbanError> result = NATIONAL.validate(NATIONAL_FR);
    assertEquals(Optional.of(IbanError.INVALID_NATIONAL_CHECK_DIGITS), result.error());
    assertEquals(NationalCheckStatus.FAILED, result.nationalCheck());
    assertEquals(Optional.of("FR_RIB_KEY"), result.rule());
    assertEquals(OptionalInt.of(26), result.position());
    assertEquals(OptionalInt.of(27), result.lastPosition());
    assertEquals("national check digits of FR are not valid (rule FR_RIB_KEY, positions 26-27)", result.message()
                                                                                                         .orElseThrow());
    // messages never contain the input
    assertFalse(result.message()
                      .orElseThrow()
                      .contains("20041010050500013M02607"));
  }

  @Test
  void passedNationalCheck() {
    ValidationResult<Iban, IbanError> result = NATIONAL.validate(VALID_FR);
    assertTrue(result.isValid());
    assertEquals(NationalCheckStatus.PASSED, result.nationalCheck());
    assertEquals(Optional.empty(), result.rule());
  }

  @Test
  void countryWithoutRule() {
    ValidationResult<Iban, IbanError> lenient = NATIONAL.validate("DE89370400440532013000");
    assertTrue(lenient.isValid());
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, lenient.nationalCheck());

    ValidationResult<Iban, IbanError> strict = REQUIRED.validate("DE89370400440532013000");
    assertEquals(Optional.of(IbanError.NATIONAL_CHECK_NOT_SUPPORTED), strict.error());
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, strict.nationalCheck());
    assertEquals("no national check for country DE", strict.message()
                                                           .orElseThrow());
    // the Netherlands deliberately have no rule
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, NATIONAL.validate("NL91ABNA0417164300")
                                                            .nationalCheck());
  }

  @Test
  void ruleThatCannotJudgeTheBban() {
    // North Macedonia allows letters in the account number, the MOD 97-10 rule needs digits
    String iban = Iban.of("MK", "250A20000058984")
                      .toString();
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, NATIONAL.validate(iban)
                                                            .nationalCheck());
    ValidationResult<Iban, IbanError> strict = REQUIRED.validate(iban);
    assertEquals(Optional.of(IbanError.NATIONAL_CHECK_NOT_SUPPORTED), strict.error());
    assertEquals(Optional.of("MK_MOD97"), strict.rule());
  }

  @Test
  void internationalErrorsComeFirst() {
    // wrong IBAN check digits and wrong RIB key: the IBAN check digits are reported
    ValidationResult<Iban, IbanError> result = NATIONAL.validate("FR8520041010050500013M02607");
    assertEquals(Optional.of(IbanError.INVALID_CHECK_DIGITS), result.error());
    assertEquals(NationalCheckStatus.NOT_PERFORMED, result.nationalCheck());
    assertEquals(Optional.of(IbanError.EMPTY), NATIONAL.validate(" ")
                                                       .error());
    assertEquals(Optional.of(IbanError.INVALID_FORMAT), NATIONAL.format(IbanFormat.PRINT)
                                                                .validate(NATIONAL_FR)
                                                                .error());
  }

  @Test
  void validationParsingAndBooleanAgree() {
    for (String input : new String[] { VALID_FR, NATIONAL_FR, "DE89370400440532013000", "FR85" }) {
      for (IbanValidator validator : new IbanValidator[] { IbanValidator.standard(), NATIONAL, REQUIRED }) {
        ValidationResult<Iban, IbanError> result = validator.validate(input);
        assertEquals(result.isValid(), validator.isValid(input));
        assertEquals(result.value(), validator.tryParse(input));
        if (result.isValid()) {
          assertEquals(result.value()
                             .orElseThrow(), validator.parse(input));
        } else {
          InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> validator.parse(input));
          assertEquals(result.error()
                             .orElseThrow(), exception.error());
          assertEquals(result.rule(), exception.result()
                                               .rule());
        }
      }
    }
  }

  @Test
  void validatorsAreImmutableAndReusable() {
    IbanValidator print = NATIONAL.format(IbanFormat.PRINT);
    assertEquals(NationalCheckMode.IF_SUPPORTED, print.nationalCheckMode());
    assertEquals(Optional.of(IbanFormat.PRINT), print.requiredFormat());
    assertEquals(Optional.empty(), NATIONAL.requiredFormat());
    assertTrue(print.isValid("FR14 2004 1010 0505 0001 3M02 606"));
    assertFalse(print.isValid(VALID_FR));
    // reusing one validator does not carry state from one input to the next
    for (int i = 0; i < 3; i++) {
      assertTrue(NATIONAL.isValid(VALID_FR));
      assertFalse(NATIONAL.isValid(NATIONAL_FR));
    }
    assertThrows(NullPointerException.class, () -> NATIONAL.nationalCheck(null));
  }

  @Test
  void allRegistryExamplesPassOrAreUnsupported() {
    for (IbanCountry country : IbanRegistry.countries()) {
      ValidationResult<Iban, IbanError> result = NATIONAL.validate(country.ibanExample());
      assertTrue(result.isValid(), country.ibanExample());
      assertTrue(result.nationalCheck() == NationalCheckStatus.PASSED || result.nationalCheck() == NationalCheckStatus.NOT_SUPPORTED);
    }
  }

  @Test
  void ownRuleAddsACountry() {
    IbanValidator required = REQUIRED.nationalRule(new LastDigitZero("DE"));
    ValidationResult<Iban, IbanError> passed = required.validate("DE89370400440532013000");
    assertTrue(passed.isValid());
    assertEquals(NationalCheckStatus.PASSED, passed.nationalCheck());
    // DE89370400440532013000 with the last digit 1, IBAN check digits recalculated
    String                            wrong  = Iban.of("DE", "370400440532013001")
                                                   .toString();
    ValidationResult<Iban, IbanError> failed = required.validate(wrong);
    assertEquals(Optional.of(IbanError.INVALID_NATIONAL_CHECK_DIGITS), failed.error());
    assertEquals(NationalCheckStatus.FAILED, failed.nationalCheck());
    assertEquals(Optional.of("TEST_DE"), failed.rule());
    assertEquals(OptionalInt.of(22), failed.position());
    // IF_SUPPORTED behaves the same, OFF ignores the rule
    assertFalse(NATIONAL.nationalRule(new LastDigitZero("DE"))
                        .isValid(wrong));
    ValidationResult<Iban, IbanError> off = IbanValidator.standard()
                                                         .nationalRule(new LastDigitZero("DE"))
                                                         .validate(wrong);
    assertTrue(off.isValid());
    assertEquals(NationalCheckStatus.NOT_PERFORMED, off.nationalCheck());
    // other validators are not changed
    assertEquals(Optional.of(IbanError.NATIONAL_CHECK_NOT_SUPPORTED), REQUIRED.validate(wrong)
                                                                               .error());
    assertEquals(Optional.empty(), REQUIRED.findNationalRule("DE"));
    assertEquals("TEST_DE", required.findNationalRule("DE")
                                    .orElseThrow()
                                    .id());
  }

  @Test
  void ownRuleReplacesBuiltInRule() {
    IbanValidator replaced = NATIONAL.nationalRule(new LastDigitZero("FR"));
    // the built-in RIB key accepts VALID_FR; the own rule rejects it (last digit 6)
    assertFalse(replaced.isValid(VALID_FR));
    assertEquals(Optional.of("TEST_FR"), replaced.validate(VALID_FR)
                                                 .rule());
    assertTrue(NATIONAL.isValid(VALID_FR));
    assertEquals(NationalChecks.find("FR"), NATIONAL.findNationalRule("FR"));
    // the last rule for a country wins; the options survive
    IbanValidator again = replaced.nationalRule(NationalChecks.find("FR")
                                                              .orElseThrow())
                                  .format(IbanFormat.ELECTRONIC);
    assertTrue(again.isValid(VALID_FR));
    assertEquals(NationalCheckMode.IF_SUPPORTED, again.nationalCheckMode());
    assertEquals("TEST_FR", replaced.nationalCheck(NationalCheckMode.REQUIRED)
                                    .findNationalRule("FR")
                                    .orElseThrow()
                                    .id());
  }

  @Test
  void ownRuleNotApplicable() {
    NationalCheck notApplicable = new LastDigitZero("DE") {
      @Override
      public NationalCheckOutcome check(String bban) {
        return NationalCheckOutcome.NOT_APPLICABLE;
      }
    };
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, NATIONAL.nationalRule(notApplicable)
                                                            .validate("DE89370400440532013000")
                                                            .nationalCheck());
    assertEquals(Optional.of(IbanError.NATIONAL_CHECK_NOT_SUPPORTED), REQUIRED.nationalRule(notApplicable)
                                                                              .validate("DE89370400440532013000")
                                                                              .error());
  }

  @Test
  void invalidOwnRules() {
    assertThrows(NullPointerException.class, () -> NATIONAL.nationalRule(null));
    assertThrows(IllegalArgumentException.class, () -> NATIONAL.nationalRule(new LastDigitZero("US")));
    assertThrows(IllegalArgumentException.class, () -> NATIONAL.nationalRule(new LastDigitZero("DE") {
      @Override
      public BbanRange checkRange() {
        return new BbanRange(18, 19);
      }
    }));
  }

  /** Test rule: the last digit of the 18 or 23 character BBAN must be 0. */
  private static class LastDigitZero
      implements NationalCheck {

    private final String countryCode;

    LastDigitZero(String countryCode) {
      this.countryCode = countryCode;
    }

    @Override
    public String id() {
      return "TEST_" + countryCode;
    }

    @Override
    public String countryCode() {
      return countryCode;
    }

    @Override
    public String description() {
      return "last digit is 0";
    }

    @Override
    public BbanRange checkRange() {
      int length = IbanRegistry.find(countryCode)
                               .map(IbanCountry::bbanLength)
                               .orElse(1);
      return new BbanRange(length, length);
    }

    @Override
    public NationalCheckOutcome check(String bban) {
      return bban.endsWith("0") ? NationalCheckOutcome.VALID : NationalCheckOutcome.INVALID;
    }

    @Override
    public Optional<String> complete(String bban) {
      return Optional.of(bban.substring(0, bban.length() - 1) + "0");
    }

    @Override
    public boolean requiresDigits() {
      return false;
    }
  }
}

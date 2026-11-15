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
import io.github.nalukit.pololei.Bban;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.IbanBuilder;
import io.github.nalukit.pololei.IbanValidator;
import io.github.nalukit.pololei.NationalCheckMode;
import io.github.nalukit.pololei.NationalCheckStatus;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.national.NationalCheck;
import io.github.nalukit.pololei.national.NationalCheckOutcome;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.BbanRange;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Runs national checks, BBAN fields, padding and random generation compiled to JavaScript; the
 * same cases as the JVM tests IbanValidatorTest, IbanFieldsTest, IbanPaddingTest and IbanGenerationTest.
 */
public class GwtTestNational
    extends GWTTestCase {

  private static final IbanValidator NATIONAL = IbanValidator.standard()
                                                             .nationalCheck(NationalCheckMode.IF_SUPPORTED);

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  public void testFrenchRibKey() {
    assertTrue(Iban.isValid("FR8420041010050500013M02607"));
    assertTrue(NATIONAL.isValid("FR1420041010050500013M02606"));
    ValidationResult<Iban, IbanError> result = NATIONAL.validate("FR8420041010050500013M02607");
    assertEquals(IbanError.INVALID_NATIONAL_CHECK_DIGITS, result.error()
                                                                .orElseThrow());
    assertEquals("FR_RIB_KEY", result.rule()
                                     .orElseThrow());
    assertEquals(26, result.position()
                           .getAsInt());
    assertEquals(27, result.lastPosition()
                           .getAsInt());
    assertEquals(NationalCheckStatus.NOT_SUPPORTED, NATIONAL.validate("DE89370400440532013000")
                                                            .nationalCheck());
    assertEquals(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, IbanValidator.standard()
                                                                      .nationalCheck(NationalCheckMode.REQUIRED)
                                                                      .validate("DE89370400440532013000")
                                                                      .error()
                                                                      .orElseThrow());
  }

  public void testRegistryExamplesPassNationalChecks() {
    for (NationalCheck rule : NationalChecks.all()) {
      IbanCountry country = IbanRegistry.find(rule.countryCode())
                                        .orElseThrow();
      Iban iban = Iban.parse(country.ibanExample());
      assertEquals(rule.id(), NationalCheckOutcome.VALID, rule.check(iban.bban()));
    }
  }

  public void testFields() {
    Iban iban = Iban.parse("FR1420041010050500013M02606");
    assertEquals("01005", iban.field(BbanField.BRANCH_IDENTIFIER)
                              .orElseThrow());
    assertEquals("0500013M026", iban.field(BbanField.ACCOUNT_NUMBER)
                                    .orElseThrow());
    assertEquals("000000123456", Iban.parse("IT60X0542811101000000123456")
                                     .field(BbanField.ACCOUNT_NUMBER)
                                     .orElseThrow());
    assertEquals(iban, Iban.builder()
                           .countryCode("FR")
                           .bankIdentifier("20041")
                           .branchIdentifier("01005")
                           .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
                           .nationalCheckDigits(true)
                           .build());
    assertTrue(Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "37040044")
                   .isValid());
    assertEquals(8, Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "3704004X")
                        .position()
                        .getAsInt());
    assertEquals(IbanError.UNDEFINED_FIELD, Bban.validateField("DE", BbanField.NATIONAL_CHECK_DIGITS, "12")
                                                .error()
                                                .orElseThrow());
  }

  public void testNationalLayouts() {
    Iban sweden = Iban.parse("SE4550000000058398257466");
    assertEquals("6", sweden.field(BbanField.NATIONAL_CHECK_DIGITS)
                            .orElseThrow());
    assertEquals("0000005839825746", sweden.field(BbanField.ACCOUNT_NUMBER)
                                           .orElseThrow());
    Iban poland = Iban.parse("PL61109010140000071219812874");
    assertEquals("10901014", poland.bankIdentifier()
                                   .orElseThrow());
    assertEquals("4", poland.field(BbanField.NATIONAL_CHECK_DIGITS)
                            .orElseThrow());
    assertEquals("0", Iban.parse("TR330006100519786457841326")
                          .field(BbanField.RESERVED)
                          .orElseThrow());
    // the fields of every country build the same IBAN
    for (IbanCountry country : IbanRegistry.countries()) {
      Iban        iban    = Iban.parse(country.ibanExample());
      IbanBuilder builder = Iban.builder()
                                .countryCode(iban.countryCode());
      for (Map.Entry<BbanField, String> field : iban.fields()
                                                     .entrySet()) {
        builder.field(field.getKey(), field.getValue());
      }
      assertEquals(country.countryCode(), iban, builder.build());
    }
  }

  /** iban4j #64, #83: routing check digit at position 8, account number of 16 characters at 9-24. */
  public void testHungarianFields() {
    Iban iban = Iban.parse("HU42117730161111101800000000");
    assertEquals("117", iban.field(BbanField.BANK_IDENTIFIER).orElseThrow());
    assertEquals("7301", iban.field(BbanField.BRANCH_IDENTIFIER).orElseThrow());
    assertEquals("6", iban.field(BbanField.ROUTING_CHECK_DIGIT).orElseThrow());
    assertEquals("1111101800000000", iban.field(BbanField.ACCOUNT_NUMBER).orElseThrow());
    assertFalse(iban.field(BbanField.NATIONAL_CHECK_DIGITS).isPresent());
    assertEquals(iban, Iban.builder()
                           .countryCode("HU")
                           .field(BbanField.BANK_IDENTIFIER, "117")
                           .field(BbanField.BRANCH_IDENTIFIER, "7301")
                           .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                           .field(BbanField.ACCOUNT_NUMBER, "1111101800000000")
                           .build());
    Iban second = Iban.parse("HU74116000060000000034953226");
    assertEquals("0000", second.field(BbanField.BRANCH_IDENTIFIER).orElseThrow());
    assertEquals("6", second.field(BbanField.ROUTING_CHECK_DIGIT).orElseThrow());
    assertEquals("0000000034953226", second.field(BbanField.ACCOUNT_NUMBER).orElseThrow());
    assertEquals(second, Iban.builder()
                             .countryCode("HU")
                             .field(BbanField.BANK_IDENTIFIER, "116")
                             .field(BbanField.BRANCH_IDENTIFIER, "0000")
                             .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                             .field(BbanField.ACCOUNT_NUMBER, "0000000034953226")
                             .build());
  }

  /** Small cases of IssueCasesTest (iban4j issues #17, #29, #34, #64, #83, #145). */
  public void testIssueCases() {
    IbanValidator base     = IbanValidator.standard();
    IbanValidator national = IbanValidator.standard()
                                          .nationalCheck(NationalCheckMode.REQUIRED);
    // #34: the Spanish control digits 45 fail, 48 pass
    assertEquals(IbanError.INVALID_NATIONAL_CHECK_DIGITS, national.validate("ES7021000418450200051322")
                                                                 .error()
                                                                 .orElseThrow());
    assertTrue(national.isValid("ES7821000418480200051322"));
    assertTrue(base.isValid("ES7021000418450200051322"));
    // #29: old 32 character NI example, current registry example
    assertEquals(IbanError.INVALID_LENGTH, base.validate("NI92BAMC000000000000000003123123")
                                               .error()
                                               .orElseThrow());
    assertTrue(base.isValid("NI45BAPR00000013000003558124"));
    // #17: TR01 original and its canonical counterpart TR98
    assertEquals(IbanError.INVALID_CHECK_DIGITS, base.validate("TR010012300882101517977799")
                                                     .error()
                                                     .orElseThrow());
    assertTrue(base.isValid("TR980012300882101517977799"));
    // #64: wrong check digits of the original
    assertEquals(IbanError.INVALID_CHECK_DIGITS, base.validate("HU58117730111111018000000006")
                                                     .error()
                                                     .orElseThrow());
    // #83: both HU values are valid, position 8 is the routing check digit
    Iban first  = Iban.parse("HU36116000000000000349532266");
    assertEquals("0", first.field(BbanField.ROUTING_CHECK_DIGIT).orElseThrow());
    assertEquals("0000000349532266", first.field(BbanField.ACCOUNT_NUMBER).orElseThrow());
    // #145: MA is not in the registry
    assertEquals(IbanError.UNSUPPORTED_COUNTRY, base.validate("MA64011519000001205000534921")
                                                    .error()
                                                    .orElseThrow());
    assertEquals(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, national.validate("DE89370400440532013000")
                                                                 .error()
                                                                 .orElseThrow());
  }

  public void testOwnNationalRule() {
    NationalCheck lastDigitZero = new NationalCheck() {
      @Override
      public String id() {
        return "TEST_DE";
      }

      @Override
      public String countryCode() {
        return "DE";
      }

      @Override
      public String description() {
        return "last digit is 0";
      }

      @Override
      public BbanRange checkRange() {
        return new BbanRange(18, 18);
      }

      @Override
      public NationalCheckOutcome check(String bban) {
        return bban.endsWith("0") ? NationalCheckOutcome.VALID : NationalCheckOutcome.INVALID;
      }

      @Override
      public Optional<String> complete(String bban) {
        return Optional.of(bban.substring(0, 17) + "0");
      }

      @Override
      public boolean requiresDigits() {
        return true;
      }
    };
    IbanValidator required = IbanValidator.standard()
                                          .nationalCheck(NationalCheckMode.REQUIRED)
                                          .nationalRule(lastDigitZero);
    assertTrue(required.isValid("DE89370400440532013000"));
    String wrong = Iban.of("DE", "370400440532013001")
                       .toString();
    assertEquals("TEST_DE", required.validate(wrong)
                                    .rule()
                                    .orElseThrow());
    assertEquals(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, IbanValidator.standard()
                                                                      .nationalCheck(NationalCheckMode.REQUIRED)
                                                                      .validate(wrong)
                                                                      .error()
                                                                      .orElseThrow());
  }

  public void testPadding() {
    assertEquals("DE89370400440532013000", Iban.builder()
                                               .countryCode("DE")
                                               .bankIdentifier("37040044")
                                               .accountNumber("532013000")
                                               .padAccountNumber(true)
                                               .build()
                                               .toString());
    assertEquals("XXBK", Iban.builder()
                             .countryCode("GB")
                             .bankIdentifier("BK")
                             .padBankIdentifier(true)
                             .paddingCharacter('X')
                             .branchIdentifier("601613")
                             .accountNumber("31926819")
                             .build()
                             .bankIdentifier()
                             .orElseThrow());
  }

  public void testNationalRandomIbans() {
    IbanValidator required = IbanValidator.standard()
                                          .nationalCheck(NationalCheckMode.REQUIRED);
    Random        random   = new Random(42);
    for (NationalCheck rule : NationalChecks.all()) {
      for (int i = 0; i < 50; i++) {
        Iban iban = Iban.builder()
                        .countryCode(rule.countryCode())
                        .nationalCheckDigits(true)
                        .buildRandom(random);
        assertTrue(iban.toString(), required.isValid(iban.toString()));
      }
    }
    Iban british = Iban.builder()
                       .countryCode("GB")
                       .bankIdentifier("NWBK")
                       .buildRandom(random);
    assertEquals("NWBK", british.bankIdentifier()
                                .orElseThrow());
  }
}

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
package io.github.nalukit.pololei.national;

import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationalChecksTest {

  /** The 15 countries iban4j (commit b2f6be5) has national rules for; pololei adds MC and SM and leaves out NL. */
  private static final Set<String> COUNTRIES = Set.of("BA", "BE", "ES", "FI", "FR", "IT", "MC", "ME", "MK", "NO", "PT", "RS", "SI", "SK", "SM", "TN");

  /**
   * Valid IBANs from two independent sources per country: the example of the SWIFT IBAN registry
   * (release 100) and published accounts or test vectors; sources in docs/national-checks.md.
   */
  @ParameterizedTest
  @CsvSource({
      // SWIFT IBAN registry examples
      "BA391290079401028494", "BE68539007547034", "ES9121000418450200051332", "FI2112345600000785", "FR1420041010050500013M02606",
      "IT60X0542811101000000123456", "MC5811222000010123456789030", "ME25505000012345678951", "MK07250120000058984", "NO9386011117947",
      "PT50000201231234567890154", "RS35260005601001611379", "SI56263300012039086", "SK3112000000198742637541", "SM86U0322509800000000270100",
      "TN5910006035183598478831",
      // published accounts
      "BA391610000205360069",          // xtransfer.com listing of a UniCredit Bank d.d. account
      "BE29001510480764",              // Rode Kruis-Vlaanderen, SEPA mandate form 2024
      "ES5000491837542210249123",      // donation account listed by RTVC 2023
      "FI0557134520027557",            // Suomen Punainen Risti, Turunmaan piiri
      "FR7610278012500001648634055",   // OCOVAS donation form, CHRU Strasbourg
      "IT38R0760103000000000900050",   // Croce Rossa Italiana, esteri.it 2016
      "IT60P0200805255000104428939",   // Regione Lazio, esteri.it 2016
      "ME25510000000006234133",        // python-stdnum test vector (stdnum.me.iban)
      "MK07300701000001228",           // Red Cross of North Macedonia, ckrm.org.mk
      "PT50003500270008240223053",     // Cruz Vermelha Portuguesa, Millennium bcp press release 2019
      "RS35205007080003980905",        // Crveni krst Srbije, eurohoops.net 2014
      "SI56029220019831742",           // Rdeci kriz Slovenije, bizi.si
      "SK5411000000002945456524",      // Slovenska katolicka charita, teraz.sk
      "TN5905000000001508508829",      // Croissant-Rouge tunisien, webdo.tn 2023
      "BE32123456789002",              // python-stdnum test vector (stdnum.be.iban)
  })
  void validExamples(String input) {
    Iban          iban = Iban.parse(input);
    NationalCheck rule = NationalChecks.find(iban.countryCode())
                                       .orElseThrow();
    assertEquals(NationalCheckOutcome.VALID, rule.check(iban.bban()), input);
    assertEquals(Optional.of(iban.bban()), rule.complete(iban.bban()), input);
  }

  @Test
  void norwegianPostgiroUsesLuhn() {
    // python-stdnum test vector stdnum.no.kontonr: '0000.4090403' (postgiro bank code)
    Iban iban = Iban.of("NO", "00004090403");
    assertEquals(NationalCheckOutcome.VALID, rule("NO").check(iban.bban()));
    assertEquals(NationalCheckOutcome.INVALID, rule("NO").check("00004090404"));
  }

  @Test
  void rulesAreOrderedByCountryCode() {
    List<String> codes = NationalChecks.all()
                                       .stream()
                                       .map(NationalCheck::countryCode)
                                       .toList();
    assertEquals(codes.stream()
                      .sorted()
                      .toList(), codes);
  }

  @Test
  void rulesCoverTheExpectedCountries() {
    assertEquals(new TreeSet<>(COUNTRIES), NationalChecks.all()
                                                        .stream()
                                                        .map(NationalCheck::countryCode)
                                                        .collect(Collectors.toCollection(TreeSet::new)));
    for (NationalCheck rule : NationalChecks.all()) {
      assertTrue(rule.id()
                     .startsWith(rule.countryCode() + "_"), rule.id());
      assertFalse(rule.description()
                      .isEmpty());
      IbanCountry country = IbanRegistry.find(rule.countryCode())
                                        .orElseThrow();
      assertTrue(rule.checkRange()
                     .end() <= country.bbanLength(), rule.id());
    }
    // new Dutch account numbers need not pass the eleven test since 2020 (Betaalvereniging)
    assertEquals(Optional.empty(), NationalChecks.find("NL"));
    assertEquals(Optional.empty(), NationalChecks.find(null));
  }

  static Stream<String> countries() {
    return COUNTRIES.stream()
                    .sorted();
  }

  /**
   * Changes a national check digit of the registry example and recalculates the IBAN check digits:
   * the IBAN stays valid, the national check fails.
   */
  @ParameterizedTest
  @MethodSource("countries")
  void nationallyInvalidButInternationallyValid(String countryCode) {
    NationalCheck rule    = rule(countryCode);
    String        example = Iban.parse(IbanRegistry.find(countryCode)
                                                   .orElseThrow()
                                                   .ibanExample())
                                .bban();
    for (int index = rule.checkRange()
                         .start() - 1; index < rule.checkRange()
                                                   .end(); index++) {
      String changed = replace(example, index, next(example.charAt(index)));
      Iban   iban    = Iban.of(countryCode, changed);
      assertTrue(Iban.isValid(iban.toString()));
      NationalCheckOutcome outcome = rule.check(iban.bban());
      // a digit inside a protected range may be changed without effect only for SK and NO, where
      // a changed position can produce another valid combination; the check digit itself never
      if (index == rule.checkRange()
                       .end() - 1) {
        assertEquals(NationalCheckOutcome.INVALID, outcome, countryCode + " " + changed);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("countries")
  void completeProducesValidBbans(String countryCode) {
    NationalCheck rule    = rule(countryCode);
    IbanCountry   country = IbanRegistry.find(countryCode)
                                        .orElseThrow();
    Random        random  = new Random(7);
    int           solved  = 0;
    for (int i = 0; i < 500; i++) {
      String bban = country.bbanStructure()
                           .random(random);
      if (rule.requiresDigits()) {
        bban = bban.replaceAll("[A-Z]", "1");
      }
      Optional<String> completed = rule.complete(bban);
      if (completed.isPresent()) {
        solved++;
        assertEquals(NationalCheckOutcome.VALID, rule.check(completed.get()), completed.get());
        assertTrue(country.bbanStructure()
                          .matches(completed.get()));
      } else {
        assertNotEquals(NationalCheckOutcome.VALID, rule.check(bban));
      }
    }
    assertTrue(solved > 400, countryCode + ": " + solved);
  }

  @Test
  void macedonianLettersAreNotApplicable() {
    // the registry allows letters (3!n10!c2!n), the MOD 97-10 rule is only defined for digits
    NationalCheck rule = rule("MK");
    assertTrue(rule.requiresDigits());
    assertEquals(NationalCheckOutcome.NOT_APPLICABLE, rule.check("250A20000058984"));
    assertEquals(Optional.empty(), rule.complete("250A20000058984"));
  }

  @Test
  void norwegianRemainderOneHasNoCheckDigit() {
    // 8601111794: weighted sum with weights 5,4,3,2,7,6,5,4,3,2 gives remainder 1 -> no valid check digit
    NationalCheck rule = rule("NO");
    String        bban = findNorwegianWithoutCheckDigit();
    assertEquals(Optional.empty(), rule.complete(bban));
    for (char digit = '0'; digit <= '9'; digit++) {
      assertEquals(NationalCheckOutcome.INVALID, rule.check(bban.substring(0, 10) + digit));
    }
  }

  static String findNorwegianWithoutCheckDigit() {
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
  void spanishCheckDigitsMapTenAndElevenToOneAndZero() {
    NationalCheck rule = rule("ES");
    // python-stdnum test vector stdnum.es.ccc: 1234-1234-16 1234567890
    assertEquals(NationalCheckOutcome.VALID, rule.check("12341234161234567890"));
    assertEquals(NationalCheckOutcome.INVALID, rule.check("12341234001234567890"));
    // remainders 0 and 1 for the first check digit
    Random random = new Random(3);
    boolean zero = false;
    boolean one  = false;
    for (int i = 0; i < 2000 && !(zero && one); i++) {
      String completed = rule.complete(IbanRegistry.find("ES")
                                                   .orElseThrow()
                                                   .bbanStructure()
                                                   .random(random))
                             .orElseThrow();
      zero |= completed.charAt(8) == '0';
      one |= completed.charAt(8) == '1';
    }
    assertTrue(zero && one);
  }

  @Test
  void belgianRemainderZeroBecomes97() {
    // 0000000097 mod 97 = 0 -> check digits 97
    assertEquals(Optional.of("000000009797"), rule("BE").complete("000000009700"));
    assertEquals(NationalCheckOutcome.INVALID, rule("BE").check("000000009700"));
  }

  @Test
  void lettersInFrenchAndItalianAccounts() {
    // letters are converted (FR) or have their own values (IT)
    assertEquals(NationalCheckOutcome.VALID, rule("FR").check("20041010050500013M02606"));
    assertEquals(NationalCheckOutcome.INVALID, rule("FR").check("20041010050500013N02606"));
    String italian = rule("IT").complete("A05428111010A0000123456")
                               .orElseThrow();
    assertEquals(NationalCheckOutcome.VALID, rule("IT").check(italian));
    assertEquals(NationalCheckOutcome.INVALID, rule("IT").check(replace(italian, 12, 'B')));
  }

  @Test
  void slovakChecksBothParts() {
    NationalCheck rule = rule("SK");
    String        bban = "12000000198742637541";
    assertEquals(NationalCheckOutcome.VALID, rule.check(bban));
    // prefix 000019 instead of 000000 breaks the prefix check
    assertEquals(NationalCheckOutcome.INVALID, rule.check("12000000190742637541"));
    // the basic number check
    assertEquals(NationalCheckOutcome.INVALID, rule.check("12000000198742637542"));
    // python-stdnum test vector stdnum.cz.bankaccount (same algorithm): 034278-0727558021
    assertEquals(NationalCheckOutcome.VALID, rule.check("0100" + "034278" + "0727558021"));
  }

  @Test
  void ruleListIsImmutable() {
    List<NationalCheck> all = NationalChecks.all();
    org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> all.add(all.getFirst()));
  }

  private static NationalCheck rule(String countryCode) {
    return NationalChecks.find(countryCode)
                         .orElseThrow();
  }

  private static String replace(String value,
                                int index,
                                char c) {
    return value.substring(0, index) + c + value.substring(index + 1);
  }

  private static char next(char c) {
    if (c == '9') {
      return '0';
    }
    if (c == 'Z') {
      return 'A';
    }
    return (char) (c + 1);
  }
}

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
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.BbanRange;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the decisions of the data audit against iban4j (commit b2f6be5), see
 * docs/registry-audit.md: pololei follows the SWIFT IBAN registry, release 100.
 */
class RegistryAuditTest {

  /** Builds an IBAN with correct check digits, so only the structure decides. */
  private static String iban(String countryCode,
                             String bban) {
    return countryCode + IbanCheckDigits.calculate(countryCode, bban) + bban;
  }

  @Test
  void registryRelease() {
    assertEquals("SWIFT IBAN Registry Release 100", IbanRegistry.source());
    assertEquals(89, IbanRegistry.countries()
                                 .size());
  }

  /** Prefixes only iban4j accepts: territories the registry lists under FR, GB or FI, and countries without registry entry. */
  @ParameterizedTest
  @ValueSource(strings = { "AO", "AX", "BL", "CV", "GA", "GF", "GG", "GP", "IM", "IR", "JE", "MA", "MF", "MQ", "MZ", "NC", "PF", "PM", "RE", "SN", "TF", "WF", "YT" })
  void prefixesOnlyIban4jAccepts(String prefix) {
    assertTrue(IbanRegistry.find(prefix)
                           .isEmpty());
    // even with a BBAN of the parent country and correct check digits
    String bban = switch (prefix) {
      case "AX" -> "12345600000785";
      case "GG", "IM", "JE" -> "NWBK60161331926819";
      default -> "20041010050500013M02606";
    };
    assertEquals(Optional.of(IbanError.UNSUPPORTED_COUNTRY), Iban.validate(iban(prefix, bban))
                                                                 .error());
  }

  /** Prefixes only pololei accepts: part of release 100, not of iban4j. */
  @ParameterizedTest
  @ValueSource(strings = { "FK", "HN", "MN", "NI", "SO", "YE" })
  void prefixesOnlyPololeiAccepts(String prefix) {
    IbanCountry country = IbanRegistry.find(prefix)
                                      .orElseThrow();
    assertTrue(Iban.isValid(country.ibanExample()));
  }

  /** The six character masks that differ from iban4j; first the pololei mask, then the case that tells them apart. */
  @ParameterizedTest
  @CsvSource({
      // AE 3!n16!n (iban4j 3!n16!c): letters in the account number are rejected
      "AE, 0331234567890123456, true", "AE, 033123456789012345A, false",
      // BR 8!c5!n10!n1!c1!c (iban4j 8!n...1!a1!c): letters in the bank identifier and a digit as account type are accepted
      "BR, 00360305000010009795493C1, true", "BR, 99999A03000010009795493C1, true", "BR, 0036030500001000979549311, true",
      // MU 4!a2!n2!n12!n3!n3!a (iban4j 6!c2!n18!c): the account number is numeric, the currency alphabetic
      "MU, BOMM0101101030300200000MUR, true", "MU, BOMM01011010303002000A0MUR, false", "MU, BOMM0101101030300200000MU1, false",
      // PK 4!a16!c (iban4j 4!c16!n): the bank identifier has letters only, the account number may contain letters
      "PK, SCBL0000001123456702, true", "PK, SCB10000001123456702, false", "PK, SCBL000000112345670A, true",
      // TR 5!n1!n16!c (iban4j 5!n1!c16!c): the reserved sixth character is a digit
      "TR, 0006100519786457841326, true", "TR, 00061A0519786457841326, false",
      // UA 6!n19!c (iban4j 6!n19!n): the account number may contain letters
      "UA, 3223130000026007233566001, true", "UA, 322313000002600723356600A, true",
  })
  void characterMasks(String countryCode,
                      String bban,
                      boolean valid) {
    assertEquals(valid, Iban.isValid(iban(countryCode, bban)), countryCode + bban);
  }

  /** Bank and branch identifiers follow the registry where iban4j defines other positions. */
  @ParameterizedTest
  @CsvSource({
      "AL, 1-3, 4-8", "BY, 1-4, ", "FI, 1-3, ", "FR, 1-5, ", "PL, 1-8, ", "PT, 1-4, ", "SI, 1-5, ",
  })
  void bankAndBranchPositions(String countryCode,
                              String bank,
                              String branch) {
    IbanCountry country = IbanRegistry.find(countryCode)
                                      .orElseThrow();
    assertEquals(bank, country.bankIdentifier()
                              .map(BbanRange::toString)
                              .orElse(null));
    assertEquals(branch, country.branchIdentifier()
                                .map(BbanRange::toString)
                                .orElse(null));
  }

  @Test
  void frenchBranchComesFromTheNationalLayout() {
    IbanCountry france = IbanRegistry.find("FR")
                                     .orElseThrow();
    assertEquals(Optional.empty(), france.branchIdentifier());
    assertEquals(Optional.of(new BbanRange(6, 10)), france.bbanLayout()
                                                          .find(BbanField.BRANCH_IDENTIFIER)
                                                          .map(definition -> definition.range()));
  }
}

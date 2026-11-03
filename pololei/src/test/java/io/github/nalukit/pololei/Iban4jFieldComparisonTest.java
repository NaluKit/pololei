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

import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_NUMBER;
import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_TYPE;
import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.BRANCH_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.IDENTIFICATION_NUMBER;
import static io.github.nalukit.pololei.registry.BbanField.NATIONAL_CHECK_DIGITS;
import static io.github.nalukit.pololei.registry.BbanField.OWNER_ACCOUNT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the national fields of pololei with the getters of iban4j, see {@code iban4j-fields.tsv}:
 * 82 of the 83 country codes both support; BR is left out because iban4j rejects its registry
 * example. A difference fails the test unless it is listed in {@link #EXPECTED_DIFFERENCES} with
 * exactly these values; docs/migration-iban4j.md explains them to users.
 */
class Iban4jFieldComparisonTest {

  /** Columns of iban4j-fields.tsv after country code and example, with the pololei field. */
  private static final BbanField[] COLUMNS = { BANK_IDENTIFIER, BRANCH_IDENTIFIER, ACCOUNT_NUMBER, NATIONAL_CHECK_DIGITS, ACCOUNT_TYPE, OWNER_ACCOUNT_TYPE, IDENTIFICATION_NUMBER };

  /** The country codes of iban4j-fields.tsv: all 83 common country codes except BR. */
  private static final Set<String> COMPARED = new TreeSet<>(Set.of("AD", "AE", "AL", "AT", "AZ", "BA", "BE", "BG", "BH", "BI", "BY", "CH", "CR", "CY", "CZ", "DE", "DJ", "DK", "DO", "EE", "EG", "ES", "FI",
                                                                   "FO", "FR", "GB", "GE", "GI", "GL", "GR", "GT", "HR", "HU", "IE", "IL", "IQ", "IS", "IT", "JO", "KW", "KZ", "LB", "LC", "LI",
                                                                   "LT", "LU", "LV", "LY", "MC", "MD", "ME", "MK", "MR", "MT", "MU", "NL", "NO", "OM", "PK", "PL", "PS", "PT", "QA", "RO", "RS",
                                                                   "RU", "SA", "SC", "SD", "SE", "SI", "SK", "SM", "ST", "SV", "TL", "TN", "TR", "UA", "VA", "VG", "XK"));

  /** "country field" -> the values of iban4j and pololei; the comments say why pololei deliberately differs. */
  private static final Map<String, Difference> EXPECTED_DIFFERENCES = new TreeMap<>(Map.ofEntries(
      // bank and branch identifier always follow the SWIFT IBAN registry
      // registry branch identifier 4-8 includes the check digit; it is also NATIONAL_CHECK_DIGITS
      Map.entry("AL BRANCH_IDENTIFIER", new Difference("1100", "11009")),
      // the registry defines no branch identifier for Belarus, the account number covers positions 5-28
      Map.entry("BY BRANCH_IDENTIFIER", new Difference("3600", "")),
      Map.entry("BY ACCOUNT_NUMBER", new Difference("900000002Z00AB00", "3600900000002Z00AB00")),
      // registry bank identifier is 3 digits; iban4j uses 6
      Map.entry("FI BANK_IDENTIFIER", new Difference("123456", "123")),
      Map.entry("FI ACCOUNT_NUMBER", new Difference("0000078", "4560000078")),
      // registry bank identifier is the 8 digit sort code including branch and check digit, no branch identifier
      Map.entry("PL BANK_IDENTIFIER", new Difference("109", "10901014")),
      Map.entry("PL BRANCH_IDENTIFIER", new Difference("0101", "")),
      // registry bank identifier includes the 2 digit extension (BANK_IDENTIFIER_EXTENSION)
      Map.entry("SC BANK_IDENTIFIER", new Difference("SSCB", "SSCB11")),
      // registry bank identifier is 5 digits including the branch, no branch identifier
      Map.entry("SI BANK_IDENTIFIER", new Difference("26", "26330")),
      Map.entry("SI BRANCH_IDENTIFIER", new Difference("330", "")),
      // different meaning: the last three characters are the currency, field(CURRENCY)
      Map.entry("SC ACCOUNT_TYPE", new Difference("USD", "")),
      // position 6 is a reserved position, not a check digit: field(RESERVED)
      Map.entry("TR NATIONAL_CHECK_DIGITS", new Difference("0", ""))));

  @Test
  void fieldsAgreeWithIban4jExceptForDocumentedDifferences() throws IOException {
    Map<String, Difference> differences = new TreeMap<>();
    for (String[] row : rows()) {
      Iban iban = Iban.parse(row[1]);
      for (int i = 0; i < COLUMNS.length; i++) {
        String pololei = iban.field(COLUMNS[i])
                             .orElse("");
        if (!pololei.equals(row[i + 2])) {
          differences.put(row[0] + " " + COLUMNS[i], new Difference(row[i + 2], pololei));
        }
      }
    }
    // keys and values: a changed value of an expected difference fails as well
    assertEquals(EXPECTED_DIFFERENCES, differences);
  }

  @Test
  void coversAllCommonCountries() throws IOException {
    Set<String> countries = new TreeSet<>();
    for (String[] row : rows()) {
      assertTrue(countries.add(row[0]), "duplicate " + row[0]);
      assertEquals(IbanRegistry.find(row[0])
                               .orElseThrow()
                               .ibanExample(), row[1], row[0]);
    }
    assertEquals(COMPARED, countries);
    // 82 getter comparisons; BR is the 83rd common country code, iban4j rejects its registry example
    assertEquals(82, countries.size());
    assertFalse(countries.contains("BR"));
    assertTrue(IbanRegistry.isSupported("BR"));
  }

  @Test
  void fieldsBuildTheSameIban() throws IOException {
    for (String[] row : rows()) {
      Iban        iban    = Iban.parse(row[1]);
      IbanBuilder builder = Iban.builder()
                                .countryCode(iban.countryCode());
      iban.fields()
          .forEach(builder::field);
      assertEquals(iban, builder.build(), row[0]);
    }
  }

  /** The values of one field in iban4j and pololei; an empty string is a missing field. */
  private record Difference(String iban4j,
                            String pololei) {
  }

  private static List<String[]> rows() throws IOException {
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Iban4jFieldComparisonTest.class.getResourceAsStream("iban4j-fields.tsv"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isEmpty() && !line.startsWith("#")) {
          rows.add(line.split("\t", -1));
        }
      }
    }
    return rows;
  }
}

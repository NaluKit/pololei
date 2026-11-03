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
package io.github.nalukit.pololei.registry;

import java.util.ArrayList;
import java.util.List;

import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_NUMBER;
import static io.github.nalukit.pololei.registry.BbanField.ACCOUNT_TYPE;
import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.BANK_IDENTIFIER_EXTENSION;
import static io.github.nalukit.pololei.registry.BbanField.BRANCH_IDENTIFIER;
import static io.github.nalukit.pololei.registry.BbanField.CURRENCY;
import static io.github.nalukit.pololei.registry.BbanField.IDENTIFICATION_NUMBER;
import static io.github.nalukit.pololei.registry.BbanField.NATIONAL_CHECK_DIGITS;
import static io.github.nalukit.pololei.registry.BbanField.OWNER_ACCOUNT_TYPE;
import static io.github.nalukit.pololei.registry.BbanField.RESERVED;

/**
 * National BBAN layouts that supplement the SWIFT IBAN registry. Maintained by hand, the sources
 * are listed in {@code docs/bban-fields.md}.
 * <p>
 * A supplement must agree with the registry: same BBAN length, same bank identifier and, if the
 * registry defines one, same branch identifier. A supplement that does not agree (for example
 * after a registry update) is ignored and the country gets the registry layout; IbanRegistryTest
 * fails in that case.
 */
final class BbanLayoutData {

  static final String REGISTRY_SOURCE = "SWIFT IBAN registry: bank and branch identifier, remaining positions as account number";

  private BbanLayoutData() {
  }

  static BbanLayout layout(String countryCode,
                           int bbanLength,
                           BbanRange bank,
                           BbanRange branch) {
    Supplement supplement = supplement(countryCode);
    if (supplement != null && supplement.problem(bbanLength, bank, branch) == null) {
      return new BbanLayout(bbanLength, supplement.fields, supplement.source, true);
    }
    return registryLayout(bbanLength, bank, branch);
  }

  /**
   * @return why the supplement of a country does not agree with the registry, {@code null} if it
   *         agrees or the country has no supplement
   */
  static String problem(String countryCode,
                        int bbanLength,
                        BbanRange bank,
                        BbanRange branch) {
    Supplement supplement = supplement(countryCode);
    return supplement == null ? null : supplement.problem(bbanLength, bank, branch);
  }

  private static BbanLayout registryLayout(int bbanLength,
                                           BbanRange bank,
                                           BbanRange branch) {
    List<BbanFieldDefinition> fields = new ArrayList<>();
    if (bank != null) {
      fields.add(new BbanFieldDefinition(BANK_IDENTIFIER, bank));
    }
    if (branch != null) {
      fields.add(new BbanFieldDefinition(BRANCH_IDENTIFIER, branch));
    }
    // the account number is the remaining part, if it is one contiguous block
    int first = -1;
    int last  = -1;
    int count = 0;
    for (int i = 0; i < bbanLength; i++) {
      if (!(bank != null && bank.contains(i)) && !(branch != null && branch.contains(i))) {
        first = first < 0 ? i : first;
        last  = i;
        count++;
      }
    }
    if (count > 0 && last - first + 1 == count) {
      fields.add(new BbanFieldDefinition(ACCOUNT_NUMBER, new BbanRange(first + 1, last + 1)));
    }
    fields.sort((a, b) -> a.range()
                           .start() - b.range()
                                       .start());
    return new BbanLayout(bbanLength, fields, REGISTRY_SOURCE, false);
  }

  private static Supplement supplement(String countryCode) {
    switch (countryCode) {
      case "AL":
        return new Supplement("Albania: bank 3, branch 4, check digit 1 (over bank and branch), account 16; the registry branch identifier 4-8 contains the check digit").add(BANK_IDENTIFIER, 3)
                                                                                                                                                                              .add(BRANCH_IDENTIFIER, 5)
                                                                                                                                                                              .nested(NATIONAL_CHECK_DIGITS, 8, 8)
                                                                                                                                                                              .add(ACCOUNT_NUMBER, 16);
      case "BA":
        return new Supplement("Bosnia and Herzegovina: bank 3, branch 3, account 8, check digits 2 (ISO 7064 MOD 97-10 over the BBAN)").add(BANK_IDENTIFIER, 3)
                                                                                                                                       .add(BRANCH_IDENTIFIER, 3)
                                                                                                                                       .add(ACCOUNT_NUMBER, 8)
                                                                                                                                       .add(NATIONAL_CHECK_DIGITS, 2);
      case "BE":
        return new Supplement("Belgium (National Bank of Belgium): bank 3, account 7, check digits 2 (mod 97 of the first 10 digits)").add(BANK_IDENTIFIER, 3)
                                                                                                                                       .add(ACCOUNT_NUMBER, 7)
                                                                                                                                       .add(NATIONAL_CHECK_DIGITS, 2);
      case "BG":
        return new Supplement("Bulgaria (Bulgarian National Bank, Ordinance 3): bank 4, branch 4, account type 2, account 8").add(BANK_IDENTIFIER, 4)
                                                                                                                                 .add(BRANCH_IDENTIFIER, 4)
                                                                                                                                 .add(ACCOUNT_TYPE, 2)
                                                                                                                                 .add(ACCOUNT_NUMBER, 8);
      case "BR":
        return new Supplement("Brazil (SWIFT IBAN registry, Banco Central do Brasil): bank (ISPB) 8, branch 5, account 10, account type 1, account holder position 1").add(BANK_IDENTIFIER, 8)
                                                                                                                                                                         .add(BRANCH_IDENTIFIER, 5)
                                                                                                                                                                         .add(ACCOUNT_NUMBER, 10)
                                                                                                                                                                         .add(ACCOUNT_TYPE, 1)
                                                                                                                                                                         .add(OWNER_ACCOUNT_TYPE, 1);
      case "DJ":
        return new Supplement("Djibouti (registry structure 5!n5!n11!n2!n): bank 5, branch 5, account 11, key 2").add(BANK_IDENTIFIER, 5)
                                                                                                               .add(BRANCH_IDENTIFIER, 5)
                                                                                                               .add(ACCOUNT_NUMBER, 11)
                                                                                                               .add(NATIONAL_CHECK_DIGITS, 2);
      case "EE":
        return new Supplement("Estonia (Eesti Pank): bank 2, account 13, check digit 1 (7-3-1 method)").add(BANK_IDENTIFIER, 2)
                                                                                                       .add(ACCOUNT_NUMBER, 13)
                                                                                                       .add(NATIONAL_CHECK_DIGITS, 1);
      case "ES":
        return new Supplement("Spain, Codigo Cuenta Cliente (CCC): entidad 4, oficina 4, digitos de control 2, cuenta 10").add(BANK_IDENTIFIER, 4)
                                                                                                                             .add(BRANCH_IDENTIFIER, 4)
                                                                                                                             .add(NATIONAL_CHECK_DIGITS, 2)
                                                                                                                             .add(ACCOUNT_NUMBER, 10);
      case "FI":
        return new Supplement("Finland, account number in machine format: bank 3, account 10, check digit 1 (Luhn)").add(BANK_IDENTIFIER, 3)
                                                                                                                       .add(ACCOUNT_NUMBER, 10)
                                                                                                                       .add(NATIONAL_CHECK_DIGITS, 1);
      case "FO":
        return new Supplement("Faroe Islands (registry structure 4!n9!n1!n): bank 4, account 9, check digit 1").add(BANK_IDENTIFIER, 4)
                                                                                                             .add(ACCOUNT_NUMBER, 9)
                                                                                                             .add(NATIONAL_CHECK_DIGITS, 1);
      case "FR":
        return rib("France, RIB (CFONB): code banque 5, code guichet 5, numero de compte 11, cle RIB 2; the registry defines no branch identifier");
      case "MC":
        return rib("Monaco uses the French RIB (CFONB): code banque 5, code guichet 5, numero de compte 11, cle RIB 2");
      case "HU":
        return new Supplement("Hungary (registry structure 3!n4!n1!n15!n1!n): bank 3, branch 4, account 16 (check digit of bank and branch 1, account 15), check digit 1").add(BANK_IDENTIFIER, 3)
                                                                                                                                                                         .add(BRANCH_IDENTIFIER, 4)
                                                                                                                                                                         .add(ACCOUNT_NUMBER, 16)
                                                                                                                                                                         .add(NATIONAL_CHECK_DIGITS, 1);
      case "IS":
        return new Supplement("Iceland: bank 2, branch 2, account type 2, account 6, kennitala of the account holder 10").add(BANK_IDENTIFIER, 2)
                                                                                                                          .add(BRANCH_IDENTIFIER, 2)
                                                                                                                          .add(ACCOUNT_TYPE, 2)
                                                                                                                          .add(ACCOUNT_NUMBER, 6)
                                                                                                                          .add(IDENTIFICATION_NUMBER, 10);
      case "IT":
        return cin("Italy (Banca d'Italia, ABI): CIN 1, ABI 5, CAB 5, numero di conto 12");
      case "SM":
        return cin("San Marino uses the Italian BBAN: CIN 1, ABI 5, CAB 5, numero di conto 12");
      case "ME":
        return mod97("Montenegro");
      case "RS":
        return mod97("Serbia");
      case "MK":
        return new Supplement("North Macedonia: bank 3, account 10, check digits 2 (ISO 7064 MOD 97-10 over the BBAN)").add(BANK_IDENTIFIER, 3)
                                                                                                                         .add(ACCOUNT_NUMBER, 10)
                                                                                                                         .add(NATIONAL_CHECK_DIGITS, 2);
      case "MR":
        return new Supplement("Mauritania (registry structure 5!n5!n11!n2!n): bank 5, branch 5, account 11, key 2").add(BANK_IDENTIFIER, 5)
                                                                                                                 .add(BRANCH_IDENTIFIER, 5)
                                                                                                                 .add(ACCOUNT_NUMBER, 11)
                                                                                                                 .add(NATIONAL_CHECK_DIGITS, 2);
      case "NO":
        return new Supplement("Norway, kontonummer: bank 4, account 6, check digit 1 (mod 11)").add(BANK_IDENTIFIER, 4)
                                                                                                 .add(ACCOUNT_NUMBER, 6)
                                                                                                 .add(NATIONAL_CHECK_DIGITS, 1);
      case "PL":
        return new Supplement("Poland (Narodowy Bank Polski): sort code 8 (bank 3, branch 4, check digit 1), account 16; the registry bank identifier 1-8 contains the check digit").add(BANK_IDENTIFIER, 8)
                                                                                                                                                                                   .nested(NATIONAL_CHECK_DIGITS, 8, 8)
                                                                                                                                                                                   .add(ACCOUNT_NUMBER, 16);
      case "PT":
        return new Supplement("Portugal, NIB (Banco de Portugal): banco 4, balcao 4, conta 11, digitos de controlo 2; the registry defines no branch identifier").add(BANK_IDENTIFIER, 4)
                                                                                                                                                                   .add(BRANCH_IDENTIFIER, 4)
                                                                                                                                                                   .add(ACCOUNT_NUMBER, 11)
                                                                                                                                                                   .add(NATIONAL_CHECK_DIGITS, 2);
      case "SC":
        return new Supplement("Seychelles (SWIFT IBAN registry): bank 6 (4 letters and a 2 digit extension), branch 2, account 16, currency 3").add(BANK_IDENTIFIER, 6)
                                                                                                                                                 .nested(BANK_IDENTIFIER_EXTENSION, 5, 6)
                                                                                                                                                 .add(BRANCH_IDENTIFIER, 2)
                                                                                                                                                 .add(ACCOUNT_NUMBER, 16)
                                                                                                                                                 .add(CURRENCY, 3);
      case "SE":
        return new Supplement("Sweden (registry structure 3!n16!n1!n): bank 3, account 16, check digit 1").add(BANK_IDENTIFIER, 3)
                                                                                                        .add(ACCOUNT_NUMBER, 16)
                                                                                                        .add(NATIONAL_CHECK_DIGITS, 1);
      case "SI":
        return new Supplement("Slovenia: bank 5, account 8, check digits 2 (ISO 7064 MOD 97-10 over the BBAN)").add(BANK_IDENTIFIER, 5)
                                                                                                                .add(ACCOUNT_NUMBER, 8)
                                                                                                                .add(NATIONAL_CHECK_DIGITS, 2);
      case "SK":
        return new Supplement("Slovakia: bank 4, account 16 (prefix 6 and basic number 10, each protected by a weighted mod 11 check)").add(BANK_IDENTIFIER, 4)
                                                                                                                                         .add(ACCOUNT_NUMBER, 16);
      case "TL":
        return new Supplement("Timor-Leste (registry structure 3!n14!n2!n): bank 3, account 14, check digits 2").add(BANK_IDENTIFIER, 3)
                                                                                                              .add(ACCOUNT_NUMBER, 14)
                                                                                                              .add(NATIONAL_CHECK_DIGITS, 2);
      case "TN":
        return new Supplement("Tunisia, RIB: bank 2, branch 3, account 13, key 2").add(BANK_IDENTIFIER, 2)
                                                                                  .add(BRANCH_IDENTIFIER, 3)
                                                                                  .add(ACCOUNT_NUMBER, 13)
                                                                                  .add(NATIONAL_CHECK_DIGITS, 2);
      case "TR":
        return new Supplement("Turkiye (registry structure 5!n1!n16!c): bank 5, reserved position 1 (currently 0), account 16").add(BANK_IDENTIFIER, 5)
                                                                                                                                .add(RESERVED, 1)
                                                                                                                                .add(ACCOUNT_NUMBER, 16);
      case "XK":
        return new Supplement("Kosovo (registry structure 4!n10!n2!n): bank 2, branch 2, account 10, check digits 2").add(BANK_IDENTIFIER, 2)
                                                                                                                   .add(BRANCH_IDENTIFIER, 2)
                                                                                                                   .add(ACCOUNT_NUMBER, 10)
                                                                                                                   .add(NATIONAL_CHECK_DIGITS, 2);
      default:
        return null;
    }
  }

  private static Supplement rib(String source) {
    return new Supplement(source).add(BANK_IDENTIFIER, 5)
                                 .add(BRANCH_IDENTIFIER, 5)
                                 .add(ACCOUNT_NUMBER, 11)
                                 .add(NATIONAL_CHECK_DIGITS, 2);
  }

  private static Supplement cin(String source) {
    return new Supplement(source).add(NATIONAL_CHECK_DIGITS, 1)
                                 .add(BANK_IDENTIFIER, 5)
                                 .add(BRANCH_IDENTIFIER, 5)
                                 .add(ACCOUNT_NUMBER, 12);
  }

  private static Supplement mod97(String country) {
    return new Supplement(country + ": bank 3, account 13, check digits 2 (ISO 7064 MOD 97-10 over the BBAN)").add(BANK_IDENTIFIER, 3)
                                                                                                                .add(ACCOUNT_NUMBER, 13)
                                                                                                                .add(NATIONAL_CHECK_DIGITS, 2);
  }

  /** Fields in BBAN order, each following the previous one. */
  private static final class Supplement {

    private final String                    source;
    private final List<BbanFieldDefinition> fields = new ArrayList<>();
    private       int                       length;

    private Supplement(String source) {
      this.source = source;
    }

    private Supplement add(BbanField field,
                           int fieldLength) {
      fields.add(new BbanFieldDefinition(field, new BbanRange(length + 1, length + fieldLength)));
      length += fieldLength;
      return this;
    }

    private Supplement nested(BbanField field,
                              int start,
                              int end) {
      fields.add(new BbanFieldDefinition(field, new BbanRange(start, end)));
      return this;
    }

    private String problem(int bbanLength,
                           BbanRange bank,
                           BbanRange branch) {
      if (length != bbanLength) {
        return "supplement covers " + length + " characters, the BBAN has " + bbanLength;
      }
      BbanRange supplementBank   = range(BANK_IDENTIFIER);
      BbanRange supplementBranch = range(BRANCH_IDENTIFIER);
      if (bank != null && !bank.equals(supplementBank)) {
        return "bank identifier " + supplementBank + " differs from the registry: " + bank;
      }
      if (branch != null && !branch.equals(supplementBranch)) {
        return "branch identifier " + supplementBranch + " differs from the registry: " + branch;
      }
      try {
        new BbanLayout(bbanLength, fields, source, true);
      } catch (IllegalArgumentException e) {
        return e.getMessage();
      }
      return null;
    }

    private BbanRange range(BbanField field) {
      for (BbanFieldDefinition definition : fields) {
        if (definition.field() == field) {
          return definition.range();
        }
      }
      return null;
    }
  }
}

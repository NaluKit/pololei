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

import io.github.nalukit.pololei.registry.BbanRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The national check digit rules pololei knows, one per country.
 * <p>
 * The set is fixed and works on the JVM and in GWT; there is no global registration. Countries
 * without a rule, for example the Netherlands (where new account numbers need not pass the
 * eleven test since 2020), are documented in {@code docs/national-checks.md}.
 * <p>
 * The rules are created once when the class is initialized; they are stateless, and all methods
 * are thread-safe.
 */
public final class NationalChecks {

  private static final String DIGITS = "0123456789";

  private static final List<NationalCheck>        RULES;
  private static final Map<String, NationalCheck> BY_COUNTRY = new HashMap<>();

  static {
    List<NationalCheck> rules = new ArrayList<>();
    rules.add(mod97("BA", 16));
    rules.add(new CheckDigitRule("BE_MOD97", "BE", "mod 97 of bank and account number (10 digits), 0 becomes 97", new BbanRange(11, 12), new int[] { 10, 11 }, true,
                                 bban -> {
                                   int remainder = mod97(bban.substring(0, 10));
                                   return twoDigits(remainder == 0 ? 97 : remainder);
                                 }));
    rules.add(new CheckDigitRule("ES_CCC", "ES", "two weighted mod 11 check digits of the CCC: over 00 + entidad + oficina and over the account number", new BbanRange(9, 10), new int[] { 8, 9 }, true,
                                 bban -> "" + spanishCheckDigit("00" + bban.substring(0, 8)) + spanishCheckDigit(bban.substring(10, 20))));
    rules.add(new CheckDigitRule("FI_LUHN", "FI", "Luhn check digit over bank and account number (13 digits)", new BbanRange(14, 14), new int[] { 13 }, true,
                                 bban -> String.valueOf(luhn(bban.substring(0, 13)))));
    rules.add(rib("FR"));
    rules.add(cin("IT"));
    rules.add(rib("MC"));
    rules.add(mod97("ME", 18));
    rules.add(mod97("MK", 15));
    rules.add(new CheckDigitRule("NO_MOD11", "NO", "weighted mod 11 check digit (weights 5,4,3,2,7,6,5,4,3,2), remainder 1 has no valid check digit; bank 0000 (postgiro): Luhn over the account number", new BbanRange(11, 11), new int[] { 10 }, true,
                                 NationalChecks::norwegianCheckDigit));
    rules.add(mod97("PT", 21));
    rules.add(mod97("RS", 18));
    rules.add(mod97("SI", 15));
    rules.add(new CheckDigitRule("SK_MOD11", "SK", "weighted mod 11 checks over the account prefix (weights 10,5,8,4,2,1) and the basic account number (weights 6,3,7,9,10,5,8,4,2,1)", new BbanRange(5, 20), new int[] { 9, 19 }, true,
                                 bban -> {
                                   String prefix = mod11Digit(bban.substring(4, 9), new int[] { 10, 5, 8, 4, 2 });
                                   String basic  = mod11Digit(bban.substring(10, 19), new int[] { 6, 3, 7, 9, 10, 5, 8, 4, 2 });
                                   return prefix == null || basic == null ? null : prefix + basic;
                                 }));
    rules.add(cin("SM"));
    rules.add(new CheckDigitRule("TN_RIB_KEY", "TN", "RIB key: 97 - (bank, branch and account number * 100 mod 97)", new BbanRange(19, 20), new int[] { 18, 19 }, true,
                                 bban -> twoDigits(97 - mod97(bban.substring(0, 18) + "00"))));
    RULES = Collections.unmodifiableList(rules);
    for (NationalCheck rule : RULES) {
      BY_COUNTRY.put(rule.countryCode(), rule);
    }
  }

  private NationalChecks() {
  }

  /**
   * Returns the national check digit rule of a country.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code, upper case (the lookup is case sensitive);
   *                    may be {@code null}
   * @return the national rule of the country, empty if pololei has none
   */
  public static Optional<NationalCheck> find(String countryCode) {
    return Optional.ofNullable(countryCode == null ? null : BY_COUNTRY.get(countryCode));
  }

  /**
   * Returns all national check digit rules pololei knows.
   *
   * @return an unmodifiable list of all rules, ordered by country code
   */
  public static List<NationalCheck> all() {
    return RULES;
  }

  /** ISO 7064 MOD 97-10 over the whole BBAN; the last two digits are the check digits. */
  private static NationalCheck mod97(String countryCode,
                                     int bbanLength) {
    return new CheckDigitRule(countryCode + "_MOD97", countryCode, "ISO 7064 MOD 97-10 over the BBAN: the BBAN mod 97 must be 1", new BbanRange(bbanLength - 1, bbanLength),
                              new int[] { bbanLength - 2, bbanLength - 1 }, true,
                              bban -> twoDigits(98 - mod97(bban.substring(0, bbanLength - 2) + "00")));
  }

  /** French RIB key; letters of the account number are converted as defined by the CFONB. */
  private static NationalCheck rib(String countryCode) {
    return new CheckDigitRule(countryCode + "_RIB_KEY", countryCode, "RIB key: 97 - (bank, branch and account number * 100 mod 97), letters converted A/J=1 ... I/R/Z=9", new BbanRange(22, 23), new int[] { 21, 22 }, false,
                              bban -> {
                                StringBuilder digits = new StringBuilder(23);
                                for (int i = 0; i < 21; i++) {
                                  digits.append(ribDigit(bban.charAt(i)));
                                }
                                return twoDigits(97 - mod97(digits.append("00")));
                              });
  }

  /** Italian CIN, a letter calculated from ABI, CAB and account number. */
  private static NationalCheck cin(String countryCode) {
    return new CheckDigitRule(countryCode + "_CIN", countryCode, "CIN: odd and even position values of ABI, CAB and account number, sum mod 26 as letter", new BbanRange(1, 1), new int[] { 0 }, false,
                              NationalChecks::cinLetter);
  }

  // values of 0-9 and A-Z on odd (1st, 3rd, ...) positions
  private static final int[] CIN_ODD = { 1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23 };

  private static String cinLetter(String bban) {
    int sum = 0;
    for (int i = 1; i < 23; i++) {
      char    c     = bban.charAt(i);
      boolean digit = DIGITS.indexOf(c) >= 0;
      // positions are counted from 1 starting with the ABI, so i = 1 is odd; even positions
      // count 0-9 for digits and A-Z alike
      sum += i % 2 == 1 ? CIN_ODD[digit ? c - '0' : c - 'A' + 10] : digit ? c - '0' : c - 'A';
    }
    return String.valueOf((char) ('A' + sum % 26));
  }

  private static String norwegianCheckDigit(String bban) {
    if (bban.startsWith("0000")) {
      return String.valueOf(luhn(bban.substring(4, 10)));
    }
    return mod11Digit(bban.substring(0, 10), new int[] { 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 });
  }

  /**
   * @return the check digit that makes the weighted sum (check digit weight 1) divisible by 11,
   *         {@code null} if that would be 10
   */
  private static String mod11Digit(String digits,
                                   int[] weights) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      sum += (digits.charAt(i) - '0') * weights[i];
    }
    int check = (11 - sum % 11) % 11;
    return check == 10 ? null : String.valueOf(check);
  }

  private static final int[] SPANISH_WEIGHTS = { 1, 2, 4, 8, 5, 10, 9, 7, 3, 6 };

  private static char spanishCheckDigit(String digits) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      sum += (digits.charAt(i) - '0') * SPANISH_WEIGHTS[i];
    }
    int check = 11 - sum % 11;
    return check == 11 ? '0' : check == 10 ? '1' : (char) ('0' + check);
  }

  /** Luhn check digit to append to the given digits. */
  private static int luhn(String digits) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      int digit = digits.charAt(digits.length() - 1 - i) - '0';
      if (i % 2 == 0) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
    }
    return (10 - sum % 10) % 10;
  }

  private static char ribDigit(char c) {
    if (DIGITS.indexOf(c) >= 0) {
      return c;
    }
    // A-I = 1-9, J-R = 1-9, S-Z = 2-9
    int index = c - 'A';
    int value = index < 9 ? index + 1 : index < 18 ? index - 8 : index - 16;
    return (char) ('0' + value);
  }

  private static int mod97(CharSequence digits) {
    int remainder = 0;
    for (int i = 0; i < digits.length(); i++) {
      remainder = (remainder * 10 + (digits.charAt(i) - '0')) % 97;
    }
    return remainder;
  }

  private static String twoDigits(int value) {
    return value < 10 ? "0" + value : String.valueOf(value);
  }
}

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
package io.github.nalukit.pololei.checkdigit;

import io.github.nalukit.pololei.internal.Text;

/**
 * ISO 7064 MOD 97-10 check digits as used by IBAN.
 * <p>
 * The calculation runs character by character on {@code int} values, so it needs neither
 * {@code BigInteger} nor {@code long} and is fast in GWT as well. All methods are stateless and
 * thread-safe.
 */
public final class IbanCheckDigits {

  private IbanCheckDigits() {
  }

  /**
   * Calculates the two check digits for a country code and a BBAN. Neither the country nor the
   * BBAN structure is checked; use {@link io.github.nalukit.pololei.Iban#of(String, String)} to
   * build a validated IBAN. Input is used as given, without normalization; the country code
   * length and BBAN length are not checked.
   *
   * <pre>{@code
   * IbanCheckDigits.calculate("DE", "370400440532013000");   // "89"
   * }</pre>
   *
   * @param countryCode two upper case letters
   * @param bban        the BBAN, upper case letters and digits
   * @return the check digits, always two characters (02 - 98)
   * @throws IllegalArgumentException if an argument is {@code null} or contains characters
   *                                  other than A-Z and 0-9
   */
  public static String calculate(String countryCode,
                                 String bban) {
    int checkDigits = 98 - mod97(bban + countryCode + "00");
    return checkDigits < 10 ? "0" + checkDigits : String.valueOf(checkDigits);
  }

  /**
   * Checks the check digits of an IBAN in electronic format. Country, length and BBAN structure
   * are not checked; use {@link io.github.nalukit.pololei.Iban#isValid(CharSequence)} for a full
   * validation.
   *
   * @param iban the IBAN in electronic format, upper case
   * @return {@code true} if the mod 97 check succeeds and the check digits are not 00, 01 or 99;
   *         {@code false} for inputs shorter than 5 characters
   * @throws IllegalArgumentException if the MOD 97 calculation encounters characters other than
   *                                  A-Z and 0-9; inputs shorter than 5 characters or with check
   *                                  digits 00, 01 or 99 return {@code false} before that calculation
   * @throws NullPointerException     if {@code iban} is {@code null}
   */
  public static boolean isValid(String iban) {
    if (iban.length() < 5) {
      return false;
    }
    String checkDigits = iban.substring(2, 4);
    if ("00".equals(checkDigits) || "01".equals(checkDigits) || "99".equals(checkDigits)) {
      return false;
    }
    return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
  }

  static int mod97(CharSequence value) {
    int remainder = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Text.isDigit(c)) {
        remainder = (remainder * 10 + (c - '0')) % 97;
      } else if (Text.isUpperCaseLetter(c)) {
        remainder = (remainder * 100 + (c - 'A' + 10)) % 97;
      } else {
        throw new IllegalArgumentException("invalid character for mod 97 at position " + (i + 1) + ": " + Text.describe(c));
      }
    }
    return remainder;
  }
}

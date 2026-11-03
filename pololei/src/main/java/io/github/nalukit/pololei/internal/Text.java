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
package io.github.nalukit.pololei.internal;

import io.github.nalukit.pololei.registry.CharacterType;

/**
 * Locale independent string helpers that work on the JVM and in GWT.
 * <p>
 * Internal to pololei, not part of the API: may change in any release.
 */
public final class Text {

  private Text() {
  }

  /**
   * Checks for the separators accepted by normalization: space, tab, line feed, carriage
   * return and non-breaking space (U+00A0).
   *
   * @param c the character
   * @return {@code true} for one of the supported separators
   */
  public static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ' ';
  }

  /**
   * Checks whether a character is an ASCII digit.
   *
   * @param c the character
   * @return {@code true} for 0-9
   */
  public static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /**
   * Checks whether a character is an ASCII upper case letter.
   *
   * @param c the character
   * @return {@code true} for A-Z
   */
  public static boolean isUpperCaseLetter(char c) {
    return c >= 'A' && c <= 'Z';
  }

  /**
   * Checks whether a character is an ASCII upper case letter or digit.
   *
   * @param c the character
   * @return {@code true} for A-Z or 0-9
   */
  public static boolean isAlphanumeric(char c) {
    return isDigit(c) || isUpperCaseLetter(c);
  }

  /**
   * Removes whitespace and converts a-z to upper case; other characters are kept so that
   * validation can report them. Supported whitespace is defined by {@link #isWhitespace(char)}.
   *
   * @param input the input to normalize
   * @return a normalized string
   * @throws NullPointerException if {@code input} is {@code null}
   */
  public static String normalize(CharSequence input) {
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (isWhitespace(c)) {
        continue;
      }
      sb.append(c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c);
    }
    return sb.toString();
  }

  /**
   * Checks whether input is absent, empty or consists only of supported whitespace.
   *
   * @param input the input, may be {@code null}
   * @return {@code true} if no non-whitespace characters are present
   */
  public static boolean isBlank(CharSequence input) {
    if (input == null) {
      return true;
    }
    for (int i = 0; i < input.length(); i++) {
      if (!isWhitespace(input.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Describes a character as printable ASCII in quotes or a Unicode code unit such as U+000A.
   *
   * @param c the character
   * @return the character description
   */
  public static String describe(char c) {
    return c >= ' ' && c <= '~' ? "'" + c + "'" : "U+" + Integer.toHexString(c | 0x10000)
                                                                 .substring(1)
                                                                 .toUpperCase();
  }

  /**
   * Returns an English description of a character type for error messages.
   *
   * @param type the character type
   * @return the description: digit, letter, or letter or digit
   * @throws NullPointerException if {@code type} is {@code null}
   */
  public static String describe(CharacterType type) {
    return switch (type) {
      case DIGITS -> "digit";
      case UPPER_CASE_LETTERS -> "letter";
      case ALPHANUMERIC -> "letter or digit";
    };
  }
}

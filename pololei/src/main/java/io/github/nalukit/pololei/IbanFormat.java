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

/**
 * Representations of an IBAN defined by ISO 13616-1.
 */
public enum IbanFormat {

  /** Electronic format: no separators, upper case, for example {@code DE89370400440532013000}. */
  ELECTRONIC,
  /** Print format: groups of four characters separated by one space, for example {@code DE89 3704 0044 0532 0130 00}. */
  PRINT;

  /**
   * Formats an IBAN given in electronic format. The input is not validated; to format an input of
   * unknown quality, parse it first and use {@link Iban#format(IbanFormat)}. Characters are
   * preserved as given: this method neither removes whitespace nor converts letters to upper
   * case. {@link #ELECTRONIC} returns the input unchanged; {@link #PRINT} inserts one space
   * after each group of four characters, leaving the final group shorter if necessary.
   *
   * @param electronic the IBAN in electronic format; {@code null} is returned unchanged by
   *                   {@link #ELECTRONIC}
   * @return the IBAN in this format, or {@code null} for a {@code null} electronic input
   * @throws NullPointerException if this is {@link #PRINT} and {@code electronic} is {@code null}
   */
  public String format(String electronic) {
    if (this == ELECTRONIC) {
      return electronic;
    }
    StringBuilder sb = new StringBuilder(electronic.length() + electronic.length() / 4);
    for (int i = 0; i < electronic.length(); i++) {
      if (i > 0 && i % 4 == 0) {
        sb.append(' ');
      }
      sb.append(electronic.charAt(i));
    }
    return sb.toString();
  }
}

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

import java.util.Random;

/**
 * Character classes used by the SWIFT IBAN registry to describe a BBAN.
 */
public enum CharacterType {

  /** {@code n}: digits 0-9 */
  DIGITS('n', "0123456789"),
  /** {@code a}: upper case letters A-Z */
  UPPER_CASE_LETTERS('a', "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
  /** {@code c}: upper case letters and digits (pololei always works on upper case input) */
  ALPHANUMERIC('c', "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");

  private final char   code;
  private final String alphabet;

  CharacterType(char code,
                String alphabet) {
    this.code     = code;
    this.alphabet = alphabet;
  }

  /**
   * Returns the type for a registry code ({@code n}, {@code a} or {@code c}).
   *
   * @param code the registry code
   * @return the matching type
   * @throws IllegalArgumentException if the code is unknown
   */
  public static CharacterType fromCode(char code) {
    for (CharacterType type : values()) {
      if (type.code == code) {
        return type;
      }
    }
    throw new IllegalArgumentException("unknown BBAN character type: '" + code + "'");
  }

  /**
   * Returns the code of this type in the registry notation of a {@link BbanStructure}.
   *
   * @return the registry code: {@code n}, {@code a} or {@code c}
   */
  public char code() {
    return code;
  }

  /**
   * Checks whether a character belongs to this type. Lower case letters never match.
   *
   * @param c the character to test
   * @return {@code true} if the character belongs to this type
   */
  public boolean matches(char c) {
    return alphabet.indexOf(c) >= 0;
  }

  /**
   * Draws a random character of this type, each character with the same probability.
   *
   * @param random the source of randomness
   * @return a random character of this type
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public char randomChar(Random random) {
    return alphabet.charAt(random.nextInt(alphabet.length()));
  }
}

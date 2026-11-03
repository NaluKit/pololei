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

/**
 * A position range within the BBAN, 1-based and inclusive like in the SWIFT IBAN registry
 * (for example {@code 1-8} for the German bank identifier).
 *
 * @param start first position, starting with 1
 * @param end   last position, inclusive
 */
public record BbanRange(int start,
                        int end) {

  /**
   * Creates a range.
   *
   * @param start first position, starting with 1
   * @param end   last position, inclusive
   * @throws IllegalArgumentException if {@code start} is less than 1 or {@code end} is less than
   *                                  {@code start}
   */
  public BbanRange {
    if (start < 1 || end < start) {
      throw new IllegalArgumentException("invalid BBAN range: " + start + "-" + end);
    }
  }

  /**
   * Returns the length of the range.
   *
   * @return number of characters covered by this range
   */
  public int length() {
    return end - start + 1;
  }

  /**
   * Checks whether a 0-based index lies within this 1-based range; index {@code start - 1} is the
   * first one inside.
   *
   * @param index 0-based position within the BBAN
   * @return {@code true} if the position is part of this range
   */
  public boolean contains(int index) {
    return index >= start - 1 && index < end;
  }

  /**
   * Extracts the characters of this range from a BBAN, for example the bank identifier.
   *
   * @param bban the BBAN
   * @return the characters of the BBAN covered by this range
   * @throws IndexOutOfBoundsException if the BBAN is shorter than {@link #end()}
   * @throws NullPointerException if {@code bban} is {@code null}
   */
  public String extract(String bban) {
    return bban.substring(start - 1, end);
  }

  /**
   * Returns the range in registry notation.
   *
   * @return the range, for example {@code 1-8}
   */
  @Override
  public String toString() {
    return start + "-" + end;
  }
}

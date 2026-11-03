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
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The structure of a Basic Bank Account Number as published in the SWIFT IBAN registry,
 * for example {@code 8!n10!n} for Germany.
 */
public final class BbanStructure {

  private final List<BbanSegment> segments;
  private final CharacterType[]   positions;

  private BbanStructure(List<BbanSegment> segments) {
    this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    int length = 0;
    for (BbanSegment segment : segments) {
      length += segment.length();
    }
    this.positions = new CharacterType[length];
    int index = 0;
    for (BbanSegment segment : segments) {
      for (int i = 0; i < segment.length(); i++) {
        this.positions[index++] = segment.type();
      }
    }
  }

  /**
   * Parses a structure in registry notation, for example {@code 4!n4!n12!c}.
   *
   * @param notation the structure in registry notation
   * @return the parsed structure
   * @throws IllegalArgumentException if the notation is not valid
   */
  static BbanStructure parse(String notation) {
    if (notation == null || notation.isEmpty()) {
      throw new IllegalArgumentException("BBAN structure must not be empty");
    }
    List<BbanSegment> segments = new ArrayList<>();
    int               index    = 0;
    while (index < notation.length()) {
      int start = index;
      while (index < notation.length() && Character.isDigit(notation.charAt(index))) {
        index++;
      }
      if (start == index || index + 1 >= notation.length() || notation.charAt(index) != '!') {
        throw new IllegalArgumentException("invalid BBAN structure '" + notation + "' at position " + (start + 1));
      }
      int length = Integer.parseInt(notation.substring(start, index));
      segments.add(new BbanSegment(length, CharacterType.fromCode(notation.charAt(index + 1))));
      index += 2;
    }
    return new BbanStructure(segments);
  }

  /**
   * Returns the segments of the structure, for example {@code 8!n} and {@code 10!n} for Germany.
   *
   * @return an unmodifiable list of the segments, in BBAN order
   */
  public List<BbanSegment> segments() {
    return segments;
  }

  /**
   * Returns the length a BBAN of this structure has.
   *
   * @return the total BBAN length, the sum of all segment lengths
   */
  public int length() {
    return positions.length;
  }

  /**
   * Returns the character type a BBAN must have at a position.
   *
   * @param index 0-based position within the BBAN
   * @return the character type required at that position
   * @throws IndexOutOfBoundsException if the index is negative or not less than {@link #length()}
   */
  public CharacterType typeAt(int index) {
    return positions[index];
  }

  /**
   * Returns the 0-based index of the first character that does not match the structure.
   *
   * The input is not normalized: lower case letters and whitespace are mismatches.
   *
   * @param bban the BBAN to check
   * @return the first mismatching index; if all compared characters match but lengths differ,
   *         the smaller of the input length and {@link #length()}; {@code -1} for an exact match
   * @throws NullPointerException if {@code bban} is {@code null}
   */
  public int indexOfMismatch(CharSequence bban) {
    int compared = Math.min(bban.length(), positions.length);
    for (int i = 0; i < compared; i++) {
      if (!positions[i].matches(bban.charAt(i))) {
        return i;
      }
    }
    return bban.length() == positions.length ? -1 : compared;
  }

  /**
   * Checks whether a BBAN matches this structure. The input is not normalized: lower case letters
   * and whitespace do not match.
   *
   * @param bban the BBAN to check
   * @return {@code true} if the BBAN has the right length and characters
   * @throws NullPointerException if {@code bban} is {@code null}
   */
  public boolean matches(CharSequence bban) {
    return indexOfMismatch(bban) < 0;
  }

  /**
   * Creates a random BBAN of this structure, for example as test data. National check digits are
   * not calculated.
   *
   * @param random the source of randomness; the same initial state gives the same BBAN for this structure
   * @return a random BBAN that matches this structure
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public String random(Random random) {
    StringBuilder sb = new StringBuilder(positions.length);
    for (CharacterType type : positions) {
      sb.append(type.randomChar(random));
    }
    return sb.toString();
  }

  /**
   * Compares structures by their ordered segments, including segment boundaries.
   *
   * @param o the object to compare with, may be {@code null}
   * @return {@code true} if the object has equal segments in the same order
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof BbanStructure other && segments.equals(other.segments);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this value
   */
  @Override
  public int hashCode() {
    return segments.hashCode();
  }

  /**
   * Returns the ordered segments in registry notation without separators.
   *
   * @return the structure in registry notation, for example {@code 8!n10!n}
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (BbanSegment segment : segments) {
      sb.append(segment);
    }
    return sb.toString();
  }
}

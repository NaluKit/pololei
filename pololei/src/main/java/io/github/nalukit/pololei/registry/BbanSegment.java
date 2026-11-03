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
 * A fixed length part of a BBAN, for example {@code 8!n}.
 *
 * @param length number of characters
 * @param type   allowed characters
 */
public record BbanSegment(int length,
                          CharacterType type) {

  /**
   * Creates a segment.
   *
   * @param length number of characters, at least 1
   * @param type   allowed characters
   * @throws IllegalArgumentException if the length is less than 1 or the type is {@code null}
   */
  public BbanSegment {
    if (length < 1) {
      throw new IllegalArgumentException("segment length must be positive: " + length);
    }
    if (type == null) {
      throw new IllegalArgumentException("segment type must not be null");
    }
  }

  /**
   * Returns the segment in registry notation.
   *
   * @return the segment in registry notation, for example {@code 8!n}
   */
  @Override
  public String toString() {
    return length + "!" + type.code();
  }
}

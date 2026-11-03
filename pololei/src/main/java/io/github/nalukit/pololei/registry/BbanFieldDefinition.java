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
 * Position of one {@link BbanField} within the BBAN of a country.
 *
 * @param field the field
 * @param range its position within the BBAN, 1-based and inclusive
 */
public record BbanFieldDefinition(BbanField field,
                                  BbanRange range) {

  /**
   * Creates a field definition.
   *
   * @param field the field
   * @param range its position within the BBAN, 1-based and inclusive
   * @throws IllegalArgumentException if an argument is {@code null}
   */
  public BbanFieldDefinition {
    if (field == null || range == null) {
      throw new IllegalArgumentException("field and range must not be null");
    }
  }

  /**
   * Returns the length of the field, the same as {@code range().length()}.
   *
   * @return number of characters of the field
   */
  public int length() {
    return range.length();
  }

  /**
   * Returns the field name and its 1-based BBAN range.
   *
   * @return the field and range, for example {@code BANK_IDENTIFIER 1-8}
   */
  @Override
  public String toString() {
    return field + " " + range;
  }
}

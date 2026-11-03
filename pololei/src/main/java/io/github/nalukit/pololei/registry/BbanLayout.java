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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The fields of the BBAN of one country: where bank identifier, account number, national check
 * digits and similar parts are.
 * <p>
 * Bank and branch identifier always follow the SWIFT IBAN registry. For most countries the
 * registry is the only source, and the account number covers all remaining positions. For the
 * countries listed in {@code docs/bban-fields.md} pololei adds the national structure, for
 * example the French RIB key; {@link #isSupplemented()} tells which kind of layout it is and
 * {@link #source()} names the source.
 * <p>
 * Fields do not overlap, with two exceptions: {@link BbanField#BANK_IDENTIFIER_EXTENSION} is the
 * trailing part of the bank identifier (Seychelles), and {@link BbanField#NATIONAL_CHECK_DIGITS}
 * can lie within the bank or branch identifier of the registry (Albania, Poland). A field a
 * country does not have is not part of its layout.
 */
public final class BbanLayout {

  // fields that may lie within another field
  private static final Set<BbanField> NESTABLE = EnumSet.of(BbanField.BANK_IDENTIFIER_EXTENSION, BbanField.NATIONAL_CHECK_DIGITS);

  private final List<BbanFieldDefinition> fields;
  private final String                    source;
  private final boolean                   supplemented;
  // 0-based BBAN index -> field, without nested fields
  private final BbanField[]               fieldAt;

  BbanLayout(int bbanLength,
             List<BbanFieldDefinition> fields,
             String source,
             boolean supplemented) {
    this.fields       = Collections.unmodifiableList(new ArrayList<>(fields));
    this.source       = source;
    this.supplemented = supplemented;
    this.fieldAt      = new BbanField[bbanLength];
    List<BbanFieldDefinition> nested = new ArrayList<>();
    for (BbanFieldDefinition definition : fields) {
      BbanRange range = definition.range();
      if (range.end() > bbanLength) {
        throw new IllegalArgumentException(definition + " exceeds the BBAN length of " + bbanLength);
      }
      for (BbanFieldDefinition other : fields) {
        if (other != definition && other.field() == definition.field()) {
          throw new IllegalArgumentException("duplicate field " + definition.field());
        }
      }
      if (isNested(definition, fields)) {
        nested.add(definition);
        continue;
      }
      for (int i = range.start() - 1; i < range.end(); i++) {
        if (fieldAt[i] != null) {
          throw new IllegalArgumentException(definition + " overlaps " + fieldAt[i]);
        }
        fieldAt[i] = definition.field();
      }
    }
    for (BbanFieldDefinition definition : nested) {
      if (!NESTABLE.contains(definition.field())) {
        throw new IllegalArgumentException(definition + " cannot be part of another field");
      }
      if (definition.field() == BbanField.BANK_IDENTIFIER_EXTENSION && fieldAt[definition.range()
                                                                                         .start() - 1] != BbanField.BANK_IDENTIFIER) {
        throw new IllegalArgumentException("the bank identifier extension must be part of the bank identifier");
      }
    }
    if (contains(BbanField.BANK_IDENTIFIER_EXTENSION) && !nested.contains(find(BbanField.BANK_IDENTIFIER_EXTENSION).get())) {
      throw new IllegalArgumentException("the bank identifier extension must be part of the bank identifier");
    }
  }

  /** A field is nested if it lies within a longer field, for example check digits within a bank code. */
  private static boolean isNested(BbanFieldDefinition definition,
                                  List<BbanFieldDefinition> fields) {
    for (BbanFieldDefinition other : fields) {
      if (other != definition && other.length() > definition.length() && other.range()
                                                                               .start() <= definition.range()
                                                                                                     .start() && definition.range()
                                                                                                                           .end() <= other.range()
                                                                                                                                          .end()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns all fields of the layout.
   *
   * @return an unmodifiable list of the fields in BBAN order; a nested field follows the field it
   *         lies in
   */
  public List<BbanFieldDefinition> fields() {
    return fields;
  }

  /**
   * Returns the definition, and with it the position, of a field.
   *
   * @param field the field, or {@code null} for no match
   * @return its definition, empty if the BBAN of the country has no such field
   */
  public Optional<BbanFieldDefinition> find(BbanField field) {
    for (BbanFieldDefinition definition : fields) {
      if (definition.field() == field) {
        return Optional.of(definition);
      }
    }
    return Optional.empty();
  }

  /**
   * Checks whether the BBAN of the country has a field.
   *
   * @param field the field, or {@code null} for no match
   * @return {@code true} if the BBAN of the country has the field
   */
  public boolean contains(BbanField field) {
    return find(field).isPresent();
  }

  /**
   * Returns the field a BBAN position belongs to. For a position inside a nested field, for
   * example the bank identifier extension, this is the enclosing field, for example
   * {@link BbanField#BANK_IDENTIFIER}.
   *
   * @param index 0-based position within the BBAN
   * @return the field, empty if no field covers the position
   * @throws IndexOutOfBoundsException if the index is negative or not less than the BBAN length
   */
  public Optional<BbanField> fieldAt(int index) {
    return Optional.ofNullable(fieldAt[index]);
  }

  /**
   * Tells whether pololei supplements the registry with the national structure of the BBAN, see
   * {@code docs/bban-fields.md}.
   *
   * @return {@code true} if the layout adds national fields to the SWIFT IBAN registry,
   *         {@code false} if it only consists of the registry's bank and branch identifier and
   *         the remaining positions as account number
   */
  public boolean isSupplemented() {
    return supplemented;
  }

  /**
   * Returns the source of the layout, for example the SWIFT IBAN registry or a national standard.
   *
   * @return where the layout comes from, as human readable text
   */
  public String source() {
    return source;
  }

  /**
   * Returns the field definitions in layout order.
   *
   * @return a diagnostic list of fields and their ranges
   */
  @Override
  public String toString() {
    return fields.toString();
  }
}

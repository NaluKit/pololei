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

import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.internal.Text;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.BbanFieldDefinition;
import io.github.nalukit.pololei.registry.BbanStructure;
import io.github.nalukit.pololei.registry.CharacterType;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * Validates a BBAN or a single BBAN field of a country without building an IBAN, for example to
 * check form fields one by one.
 * <p>
 * Both methods use the structure and the {@link io.github.nalukit.pololei.registry.BbanLayout}
 * of the country, the same data {@link Iban#validate(CharSequence)} uses. Whitespace is ignored
 * and lower case letters are accepted; the valid result contains the normalized value.
 * <p>
 * Positions in the results are 1-based and relative to what was validated:
 * {@link #validate(String, CharSequence)} counts BBAN characters, {@link #validateField} counts
 * the characters of the field. Add 4 to a BBAN position to get the IBAN position.
 *
 * <pre>{@code
 * Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "37040044").isValid();   // true
 * Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "3704004X").position();  // OptionalInt[8]
 * Bban.validate("GB", "NWBK60161331926819").isValid();                         // true
 * }</pre>
 * <p>
 * National check digits are only checked for their structure here; use {@link IbanValidator} to
 * verify them.
 * <p>
 * All methods are stateless and thread-safe.
 */
public final class Bban {

  private Bban() {
  }

  /**
   * Validates a complete BBAN against the length and structure of a country. Never throws for
   * invalid input, including {@code null}. The country is checked first, followed by empty BBAN,
   * allowed characters, length and country-specific character types. National check digit values
   * and account existence are not checked. Possible errors are {@link IbanError#INVALID_COUNTRY_CODE},
   * {@link IbanError#UNSUPPORTED_COUNTRY}, {@link IbanError#EMPTY},
   * {@link IbanError#INVALID_CHARACTER}, {@link IbanError#INVALID_LENGTH} and
   * {@link IbanError#INVALID_BBAN}.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; ASCII lower case and supported whitespace are normalized
   * @param bban        the BBAN, may be {@code null}
   * @return the result; if valid, its value is the normalized BBAN (upper case, no whitespace)
   */
  public static ValidationResult<String, IbanError> validate(String countryCode,
                                                             CharSequence bban) {
    ValidationResult<IbanCountry, IbanError> country = country(countryCode);
    if (!country.isValid()) {
      return country.retype();
    }
    IbanCountry spec = country.value()
                              .orElseThrow();
    if (Text.isBlank(bban)) {
      return ValidationResult.invalid(IbanError.EMPTY, "BBAN is empty");
    }
    String normalized = Text.normalize(bban);
    ValidationResult<String, IbanError> characters = checkCharacters(normalized, "BBAN");
    if (characters != null) {
      return characters;
    }
    if (normalized.length() != spec.bbanLength()) {
      return ValidationResult.<String, IbanError>invalid(IbanError.INVALID_LENGTH, "BBAN of " + spec.countryCode() + " must have " + spec.bbanLength() + " characters, but has " + normalized.length())
                             .withLength(spec.bbanLength(), normalized.length());
    }
    BbanStructure structure = spec.bbanStructure();
    int           mismatch  = structure.indexOfMismatch(normalized);
    if (mismatch >= 0) {
      CharacterType expected = structure.typeAt(mismatch);
      String        field    = spec.bbanLayout()
                                   .fieldAt(mismatch)
                                   .map(f -> " (" + f.label() + ")")
                                   .orElse("");
      return ValidationResult.<String, IbanError>invalid(IbanError.INVALID_BBAN, "expected a " + Text.describe(expected) + " at position " + (mismatch + 1) + field + " of the BBAN of " + spec.countryCode())
                             .atPosition(mismatch + 1)
                             .expecting(expected);
    }
    return ValidationResult.valid(normalized);
  }

  /**
   * Validates a single BBAN field of a country against the length and character types of its
   * positions. A field the country does not have is reported as {@link IbanError#UNDEFINED_FIELD};
   * otherwise the errors are those of {@link #validate(String, CharSequence)}. Short values are not
   * padded. The field argument is checked for {@code null} first, then the country and field
   * definition are checked before the value. National check digit values are not verified.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; ASCII lower case and supported whitespace are normalized
   * @param field       the field
   * @param value       the value of the field, for example the bank identifier; may be {@code null}
   * @return the result; if valid, its value is the normalized field value (upper case, no whitespace)
   * @throws NullPointerException if {@code field} is {@code null}
   */
  public static ValidationResult<String, IbanError> validateField(String countryCode,
                                                                  BbanField field,
                                                                  CharSequence value) {
    Objects.requireNonNull(field, "field");
    ValidationResult<IbanCountry, IbanError> country = country(countryCode);
    if (!country.isValid()) {
      return country.retype();
    }
    IbanCountry                   spec       = country.value()
                                                      .orElseThrow();
    Optional<BbanFieldDefinition> definition = spec.bbanLayout()
                                                   .find(field);
    if (definition.isEmpty()) {
      return ValidationResult.invalid(IbanError.UNDEFINED_FIELD, "the BBAN of " + spec.countryCode() + " has no " + field.label());
    }
    if (Text.isBlank(value)) {
      return ValidationResult.<String, IbanError>invalid(IbanError.EMPTY, field.label() + " of " + spec.countryCode() + " is empty")
                             .withLength(definition.get()
                                                   .length(), 0);
    }
    return checkField(spec, definition.get(), Text.normalize(value));
  }

  /** Checks a normalized field value; also used by the builder. */
  static ValidationResult<String, IbanError> checkField(IbanCountry spec,
                                                        BbanFieldDefinition definition,
                                                        String value) {
    String label      = definition.field()
                                  .label();
    ValidationResult<String, IbanError> characters = checkCharacters(value, label);
    if (characters != null) {
      return characters;
    }
    if (value.length() != definition.length()) {
      return ValidationResult.<String, IbanError>invalid(IbanError.INVALID_LENGTH, label + " of " + spec.countryCode() + " must have " + definition.length() + " characters, but has " + value.length())
                             .withLength(definition.length(), value.length());
    }
    int offset = definition.range()
                           .start() - 1;
    for (int i = 0; i < value.length(); i++) {
      CharacterType expected = spec.bbanStructure()
                                   .typeAt(offset + i);
      if (!expected.matches(value.charAt(i))) {
        return ValidationResult.<String, IbanError>invalid(IbanError.INVALID_BBAN, "expected a " + Text.describe(expected) + " at position " + (i + 1) + " of the " + label + " of " + spec.countryCode())
                               .atPosition(i + 1)
                               .expecting(expected);
      }
    }
    return ValidationResult.valid(value);
  }

  private static ValidationResult<String, IbanError> checkCharacters(String value,
                                                                     String label) {
    for (int i = 0; i < value.length(); i++) {
      if (!Text.isAlphanumeric(value.charAt(i))) {
        return ValidationResult.<String, IbanError>invalid(IbanError.INVALID_CHARACTER, "invalid character " + Text.describe(value.charAt(i)) + " at position " + (i + 1) + " of the " + label)
                               .atPosition(i + 1)
                               .expecting(CharacterType.ALPHANUMERIC);
      }
    }
    return null;
  }

  private static ValidationResult<IbanCountry, IbanError> country(String countryCode) {
    String code = countryCode == null ? "" : Text.normalize(countryCode);
    if (code.length() != 2 || !Text.isUpperCaseLetter(code.charAt(0)) || !Text.isUpperCaseLetter(code.charAt(1))) {
      return ValidationResult.invalid(IbanError.INVALID_COUNTRY_CODE, "country code must have two letters");
    }
    return IbanRegistry.find(code)
                       .map(ValidationResult::<IbanCountry, IbanError>valid)
                       .orElseGet(() -> ValidationResult.invalid(IbanError.UNSUPPORTED_COUNTRY, "country " + code + " does not use IBAN"));
  }
}

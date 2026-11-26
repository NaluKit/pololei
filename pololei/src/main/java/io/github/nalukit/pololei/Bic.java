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

import io.github.nalukit.pololei.error.BicError;
import io.github.nalukit.pololei.error.InvalidBicException;
import io.github.nalukit.pololei.internal.IsoCountryCodes;
import io.github.nalukit.pololei.internal.Text;
import io.github.nalukit.pololei.registry.CharacterType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;
import java.util.Random;

/**
 * A Business Identifier Code (ISO 9362), also known as SWIFT code, with 8 or 11 characters:
 * <ul>
 *   <li>1-4: business party prefix (institution code), letters or digits</li>
 *   <li>5-6: ISO 3166-1 alpha-2 country code</li>
 *   <li>7-8: business party suffix (location code), letters or digits</li>
 *   <li>9-11: optional branch code, letters or digits</li>
 * </ul>
 * Instances are immutable and thread-safe, and serializable (Java serialization and, with
 * pololei-gwt, GWT-RPC). All static methods are thread-safe.
 */
public final class Bic
    implements Serializable {

  @Serial
  private static final long   serialVersionUID = 1L;
  private static final String PRIMARY_OFFICE   = "XXX";

  /** The BIC in its electronic format; GWT-RPC uses Bic_CustomFieldSerializer from pololei-gwt. */
  private final String value;

  private Bic(String value) {
    this.value = value;
  }

  /**
   * Parses a BIC with 8 or 11 characters. Spaces, tabs, line feeds, carriage returns and
   * non-breaking spaces (U+00A0) are ignored; ASCII letters a-z are converted to A-Z, so
   * {@code "deut de ff"} is parsed as {@code DEUTDEFF}.
   * <p>
   * The country code must be a known ISO 3166-1 alpha-2 code; whether the BIC is actually
   * registered with SWIFT cannot be checked offline.
   *
   * @param input the BIC, may be {@code null}
   * @return the BIC
   * @throws InvalidBicException if the input is not a valid BIC; {@link InvalidBicException#result()}
   *                             carries the error details
   * @see #validate(CharSequence)
   */
  public static Bic parse(CharSequence input) {
    ValidationResult<Bic, BicError> result = validate(input);
    if (result.isValid()) {
      return result.value()
                   .orElseThrow();
    }
    throw new InvalidBicException(result);
  }

  /**
   * Parses a BIC like {@link #parse(CharSequence)}, but returns an empty optional instead of
   * throwing an exception.
   *
   * @param input the BIC, may be {@code null}
   * @return the BIC, or empty if the input is not valid
   */
  public static Optional<Bic> tryParse(CharSequence input) {
    return validate(input).value();
  }

  /**
   * Checks whether the input is a valid BIC, using the same rules as
   * {@link #parse(CharSequence)}.
   *
   * @param input the BIC, may be {@code null}
   * @return {@code true} if the input is a valid BIC
   */
  public static boolean isValid(CharSequence input) {
    return validate(input).isValid();
  }

  /**
   * Creates a BIC without branch code from its components. Shorthand for
   * {@link #of(String, String, String, String)} with a {@code null} branch code.
   *
   * @param institutionCode the institution code, 4 letters or digits
   * @param countryCode     the ISO 3166-1 alpha-2 country code
   * @param locationCode    the location code, 2 letters or digits
   * @return the BIC with 8 characters, for example {@code DEUTDEFF}
   * @throws InvalidBicException if a component is {@code null}, has the wrong length or contains
   *                             a character that is not allowed
   */
  public static Bic of(String institutionCode,
                       String countryCode,
                       String locationCode) {
    return of(institutionCode, countryCode, locationCode, null);
  }

  /**
   * Creates a BIC from its components, for example {@code DEUT}, {@code DE}, {@code FF} and
   * {@code 500} give {@code DEUTDEFF500}. The components are normalized like the input of
   * {@link #parse(CharSequence)} (whitespace is ignored, lower case letters are converted) and
   * must have exactly their own length, so a component cannot take over characters of its
   * neighbour. The BIC as a whole is then checked by the rules of {@link #parse(CharSequence)};
   * an alphanumeric institution code such as {@code A1B2} is valid.
   *
   * @param institutionCode the institution code, 4 letters or digits
   * @param countryCode     the ISO 3166-1 alpha-2 country code
   * @param locationCode    the location code, 2 letters or digits
   * @param branchCode      the branch code, 3 letters or digits, or {@code null} for a BIC with
   *                        8 characters; an empty branch code is invalid
   * @return the BIC
   * @throws InvalidBicException if a component is {@code null} (except the branch code), has the
   *                             wrong length or contains a character that is not allowed, or the
   *                             country code is unknown; the message names the component
   */
  public static Bic of(String institutionCode,
                       String countryCode,
                       String locationCode,
                       String branchCode) {
    StringBuilder bic = new StringBuilder(11);
    bic.append(component("institution code", institutionCode, 4));
    bic.append(component("country code", countryCode, 2));
    bic.append(component("location code", locationCode, 2));
    if (branchCode != null) {
      bic.append(component("branch code", branchCode, 3));
    }
    return parse(bic);
  }

  private static String component(String name,
                                  String value,
                                  int length) {
    if (value == null) {
      throw new InvalidBicException(ValidationResult.<Bic, BicError>invalid(BicError.EMPTY, name + " is missing"));
    }
    String normalized = Text.normalize(value);
    if (normalized.length() != length) {
      throw new InvalidBicException(ValidationResult.<Bic, BicError>invalid(BicError.INVALID_LENGTH, name + " must have " + length + " characters, but has " + normalized.length())
                                                    .withLength(length, normalized.length()));
    }
    return normalized;
  }

  /**
   * Creates a random BIC with 8 characters and a random country, for example as test data.
   * Shorthand for {@link #random(String, Random, int)} with a random country.
   *
   * @param random the source of randomness, for example a seeded {@link Random} for reproducible
   *               test data, or a {@link java.security.SecureRandom}; it is used directly
   * @return a random BIC; see {@link #random(String, Random, int)} for what that means
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public static Bic random(Random random) {
    return random(random, 8);
  }

  /**
   * Creates a random BIC with a random country, for example as test data.
   *
   * @param random the source of randomness, used directly
   * @param length 8 (without branch code) or 11 (with branch code)
   * @return a random BIC; see {@link #random(String, Random, int)} for what that means
   * @throws NullPointerException     if {@code random} is {@code null}
   * @throws IllegalArgumentException if {@code length} is not 8 or 11
   */
  public static Bic random(Random random,
                           int length) {
    checkRandom(random, length);
    return generate(IsoCountryCodes.get(random.nextInt(IsoCountryCodes.count())), random, length);
  }

  /**
   * Creates a random BIC with 8 characters of the given country, for example as test data.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; whitespace is ignored and lower case
   *                    letters are accepted
   * @param random      the source of randomness, used directly
   * @return a random BIC; see {@link #random(String, Random, int)} for what that means
   * @throws InvalidBicException  if the country code is missing or unknown
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public static Bic random(String countryCode,
                           Random random) {
    return random(countryCode, random, 8);
  }

  /**
   * Creates a random BIC of the given country, for example as test data. Institution code,
   * location code and, for 11 characters, branch code are drawn from letters and digits. The
   * second character of the location code is never {@code 0}, so the BIC is not a test BIC. The
   * same seed, country and length give the same BIC for the same pololei version.
   * <p>
   * The BIC is syntactically valid ({@link #isValid(CharSequence)}). That says nothing about the
   * SWIFT registration: the BIC is probably not registered, but it may by chance be the BIC of a
   * real institution. Do not use it where an unassigned BIC must be guaranteed.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; whitespace is ignored and lower case
   *                    letters are accepted
   * @param random      the source of randomness, used directly
   * @param length      8 (without branch code) or 11 (with branch code)
   * @return a random BIC of the country
   * @throws InvalidBicException      if the country code is missing or unknown
   * @throws NullPointerException     if {@code random} is {@code null}
   * @throws IllegalArgumentException if {@code length} is not 8 or 11
   */
  public static Bic random(String countryCode,
                           Random random,
                           int length) {
    checkRandom(random, length);
    if (Text.isBlank(countryCode)) {
      throw new InvalidBicException(ValidationResult.<Bic, BicError>invalid(BicError.UNKNOWN_COUNTRY, "country code is missing"));
    }
    String country = Text.normalize(countryCode);
    if (!IsoCountryCodes.isKnown(country)) {
      throw new InvalidBicException(ValidationResult.<Bic, BicError>invalid(BicError.UNKNOWN_COUNTRY, "unknown country code " + country));
    }
    return generate(country, random, length);
  }

  private static void checkRandom(Random random,
                                  int length) {
    if (random == null) {
      throw new NullPointerException("random");
    }
    if (length != 8 && length != 11) {
      throw new IllegalArgumentException("a BIC has 8 or 11 characters, not " + length);
    }
  }

  private static Bic generate(String country,
                              Random random,
                              int length) {
    StringBuilder bic = new StringBuilder(length);
    appendRandom(bic, random, 4, false);
    bic.append(country);
    appendRandom(bic, random, 1, false);
    // 0 as second character of the location code marks a test BIC
    appendRandom(bic, random, 1, true);
    if (length == 11) {
      appendRandom(bic, random, 3, false);
    }
    return parse(bic);
  }

  private static void appendRandom(StringBuilder bic,
                                   Random random,
                                   int count,
                                   boolean notZero) {
    for (int i = 0; i < count; i++) {
      char c;
      do {
        int n = random.nextInt(36);
        c = n < 26 ? (char) ('A' + n) : (char) ('0' + n - 26);
      } while (notZero && c == '0');
      bic.append(c);
    }
  }

  /**
   * Validates a BIC. Whitespace is ignored and lower case letters are accepted, as described for
   * {@link #parse(CharSequence)}.
   * <p>
   * Checks run in this order: empty input, normalized length, allowed characters and known
   * country code. The result reports the first failure; character positions refer to the
   * normalized BIC. Registration with SWIFT is not checked. Invalid input, including
   * {@code null}, is reported in the result.
   *
   * @param input the BIC, may be {@code null}
   * @return the validation result; if valid, its value is the parsed BIC
   */
  public static ValidationResult<Bic, BicError> validate(CharSequence input) {
    if (Text.isBlank(input)) {
      return ValidationResult.invalid(BicError.EMPTY, "BIC is empty");
    }
    String bic = Text.normalize(input);
    if (bic.length() != 8 && bic.length() != 11) {
      return ValidationResult.<Bic, BicError>invalid(BicError.INVALID_LENGTH, "BIC must have 8 or 11 characters, but has " + bic.length())
                             .withLength(bic.length() < 8 ? 8 : 11, bic.length());
    }
    for (int i = 0; i < bic.length(); i++) {
      char    c     = bic.charAt(i);
      CharacterType expected = i == 4 || i == 5 ? CharacterType.UPPER_CASE_LETTERS : CharacterType.ALPHANUMERIC;
      if (!expected.matches(c)) {
        return ValidationResult.<Bic, BicError>invalid(BicError.INVALID_CHARACTER, "invalid character " + Text.describe(c) + " at position " + (i + 1) + ", expected a " + Text.describe(expected))
                               .atPosition(i + 1)
                               .expecting(expected);
      }
    }
    if (!IsoCountryCodes.isKnown(bic.substring(4, 6))) {
      return ValidationResult.<Bic, BicError>invalid(BicError.UNKNOWN_COUNTRY, "unknown country code " + bic.substring(4, 6))
                             .atPosition(5);
    }
    return ValidationResult.valid(new Bic(bic));
  }

  /**
   * Returns the business party prefix, characters 1-4.
   *
   * @return the institution (bank) code, for example {@code DEUT} of {@code DEUTDEFF}
   */
  public String institutionCode() {
    return value.substring(0, 4);
  }

  /**
   * Returns the country code, characters 5-6.
   *
   * @return the ISO 3166-1 alpha-2 country code, for example {@code DE} of {@code DEUTDEFF}
   */
  public String countryCode() {
    return value.substring(4, 6);
  }

  /**
   * Returns the business party suffix, characters 7-8.
   *
   * @return the location code, for example {@code FF} of {@code DEUTDEFF}
   */
  public String locationCode() {
    return value.substring(6, 8);
  }

  /**
   * Returns the branch code, characters 9-11.
   *
   * @return the branch code, for example {@code 500} of {@code DEUTDEFF500}; empty if the BIC has
   *         8 characters. {@code XXX} is returned as is, see {@link #isPrimaryOffice()}.
   */
  public Optional<String> branchCode() {
    return value.length() == 11 ? Optional.of(value.substring(8)) : Optional.empty();
  }

  /**
   * Checks whether the BIC denotes the primary office of the institution rather than a branch.
   *
   * @return {@code true} if the BIC has no branch code or the branch code {@code XXX}
   */
  public boolean isPrimaryOffice() {
    return value.length() == 8 || PRIMARY_OFFICE.equals(value.substring(8));
  }

  /**
   * Checks whether the BIC is a test and training BIC, which must not be used for live payments.
   *
   * @return {@code true} if the second character of the location code is {@code 0}
   */
  public boolean isTestBic() {
    return value.charAt(7) == '0';
  }

  /**
   * Returns the BIC of the primary office, without branch code. {@code DEUTDEFF500} and
   * {@code DEUTDEFFXXX} both give {@code DEUTDEFF}.
   *
   * @return the 8 character BIC of the primary office; this BIC if it already has 8 characters
   */
  public Bic primaryOffice() {
    return value.length() == 8 ? this : new Bic(value.substring(0, 8));
  }

  /**
   * Validates a deserialized instance, so that a manipulated stream cannot create an invalid BIC.
   * Called by Java serialization; GWT-RPC validates in Bic_CustomFieldSerializer.
   *
   * @return the validated BIC that replaces the deserialized instance
   */
  @Serial
  private Object readResolve() {
    return parse(value == null ? "" : value);
  }

  /**
   * Two BICs are equal if they have the same characters. {@code DEUTDEFF} and {@code DEUTDEFFXXX}
   * denote the same office, but are not equal. Use {@link #primaryOffice()} to compare institutions
   * while ignoring branch codes; it also removes non-primary branch codes such as {@code 500}.
   *
   * @param o the object to compare with
   * @return {@code true} if {@code o} is a BIC with the same characters
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof Bic other && value.equals(other.value);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this BIC
   */
  @Override
  public int hashCode() {
    return value.hashCode();
  }

  /**
   * Returns the BIC with 8 or 11 characters, upper case and without whitespace.
   *
   * @return the BIC, for example {@code DEUTDEFF}
   */
  @Override
  public String toString() {
    return value;
  }
}

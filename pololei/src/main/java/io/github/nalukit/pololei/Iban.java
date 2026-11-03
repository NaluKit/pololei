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

import io.github.nalukit.pololei.checkdigit.IbanCheckDigits;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.internal.Text;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.BbanFieldDefinition;
import io.github.nalukit.pololei.registry.BbanLayout;
import io.github.nalukit.pololei.registry.BbanStructure;
import io.github.nalukit.pololei.registry.CharacterType;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * An International Bank Account Number (ISO 13616) that has passed validation: the country uses
 * IBAN, length and BBAN structure match the registry and the check digits are correct.
 * <p>
 * Instances are immutable and thread-safe, so they can be shared freely, and serializable (Java
 * serialization and, with pololei-gwt, GWT-RPC); {@link #toString()} returns the electronic format.
 * All static methods are thread-safe.
 *
 * <pre>{@code
 * Iban iban = Iban.parse("DE89 3704 0044 0532 0130 00");
 * iban.bankIdentifier();               // Optional[37040044]
 * iban.format(IbanFormat.PRINT);       // "DE89 3704 0044 0532 0130 00"
 *
 * ValidationResult<Iban, IbanError> result = Iban.validate(input);
 * if (!result.isValid()) {
 *   show(result.error().orElseThrow(), result.position());
 * }
 * }</pre>
 */
public final class Iban
    implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  // the electronic format; GWT-RPC uses Iban_CustomFieldSerializer from pololei-gwt
  private final String value;

  private Iban(String value) {
    this.value = value;
  }

  /**
   * Parses an IBAN in electronic or print format. Spaces, tabs, line feeds, carriage returns and
   * non-breaking spaces (U+00A0) are ignored; ASCII letters a-z are converted to A-Z, so {@code "de89 3704 0044 0532 0130 00"} is parsed as well.
   * <p>
   * Only the international rules are checked; use {@link IbanValidator} to verify national check
   * digits too.
   *
   * @param input the IBAN, may be {@code null}
   * @return the IBAN
   * @throws InvalidIbanException if the input is not a valid IBAN; {@link InvalidIbanException#result()}
   *                              carries the error details
   * @see #validate(CharSequence)
   */
  public static Iban parse(CharSequence input) {
    return orThrow(validate(input));
  }

  /**
   * Parses an IBAN that must be given exactly in the requested format: upper case and without
   * spaces for {@link IbanFormat#ELECTRONIC}, upper case in groups of four characters separated by
   * one space for {@link IbanFormat#PRINT}.
   *
   * @param input  the IBAN, may be {@code null}
   * @param format the expected format
   * @return the IBAN
   * @throws InvalidIbanException if the input is not a valid IBAN in that format
   * @throws NullPointerException if {@code format} is {@code null}
   * @see #validate(CharSequence, IbanFormat)
   */
  public static Iban parse(CharSequence input,
                           IbanFormat format) {
    return orThrow(validate(input, format));
  }

  /**
   * Parses an IBAN like {@link #parse(CharSequence)}, but returns an empty optional instead of
   * throwing an exception.
   *
   * @param input the IBAN in electronic or print format, may be {@code null}
   * @return the IBAN, or empty if the input is not valid
   */
  public static Optional<Iban> tryParse(CharSequence input) {
    return validate(input).value();
  }

  /**
   * Checks whether the input is a valid IBAN, using the same rules as
   * {@link #parse(CharSequence)}.
   *
   * @param input the IBAN in electronic or print format, may be {@code null}
   * @return {@code true} if the input is a valid IBAN
   */
  public static boolean isValid(CharSequence input) {
    return validate(input).isValid();
  }

  /**
   * Validates an IBAN in electronic or print format. Whitespace is ignored and lower case
   * letters are accepted.
   * <p>
   * Checks run in this order: empty input, allowed characters, country code syntax, supported
   * country, country-specific length, check digit syntax, BBAN structure and MOD 97 check digits.
   * The result reports the first failure. Character positions refer to the normalized IBAN.
   * National check digits and the existence of the account are not checked; use
   * {@link IbanValidator} for national rules. Invalid input, including {@code null}, is reported
   * in the result.
   *
   * @param input the IBAN, may be {@code null}
   * @return the validation result; if valid, its value is the parsed IBAN
   */
  public static ValidationResult<Iban, IbanError> validate(CharSequence input) {
    if (Text.isBlank(input)) {
      return invalid(IbanError.EMPTY, "IBAN is empty");
    }
    return validateNormalized(Text.normalize(input));
  }

  /**
   * Validates an IBAN that must be given exactly in the requested format. An input that is a
   * valid IBAN in another format is reported as {@link IbanError#INVALID_FORMAT}.
   * After checking {@code format} for {@code null}, empty input is reported first; otherwise the
   * exact format is checked before the international checks of {@link #validate(CharSequence)}.
   * National check digits and the existence of the account are not checked.
   *
   * @param input  the IBAN, may be {@code null}
   * @param format the expected format
   * @return the validation result; if valid, its value is the parsed IBAN
   * @throws NullPointerException if {@code format} is {@code null}
   */
  public static ValidationResult<Iban, IbanError> validate(CharSequence input,
                                                           IbanFormat format) {
    Objects.requireNonNull(format, "format");
    if (Text.isBlank(input)) {
      return invalid(IbanError.EMPTY, "IBAN is empty");
    }
    String normalized = Text.normalize(input);
    if (!format.format(normalized)
               .equals(input.toString())) {
      return invalid(IbanError.INVALID_FORMAT, format == IbanFormat.PRINT ?
                                               "IBAN must be upper case, in groups of four characters separated by one space" :
                                               "IBAN must be upper case without spaces");
    }
    return validateNormalized(normalized);
  }

  private static ValidationResult<Iban, IbanError> validateNormalized(String iban) {
    for (int i = 0; i < iban.length(); i++) {
      if (!Text.isAlphanumeric(iban.charAt(i))) {
        return invalid(IbanError.INVALID_CHARACTER, "invalid character " + Text.describe(iban.charAt(i)) + " at position " + (i + 1)).atPosition(i + 1)
                                                                                                                                     .expecting(CharacterType.ALPHANUMERIC);
      }
    }
    for (int i = 0; i < 2; i++) {
      if (i >= iban.length() || !Text.isUpperCaseLetter(iban.charAt(i))) {
        ValidationResult<Iban, IbanError> result = invalid(IbanError.INVALID_COUNTRY_CODE, "IBAN must start with a two letter country code").expecting(CharacterType.UPPER_CASE_LETTERS);
        return i < iban.length() ? result.atPosition(i + 1) : result.withLength(2, iban.length());
      }
    }
    String      countryCode = iban.substring(0, 2);
    IbanCountry spec        = IbanRegistry.find(countryCode)
                                          .orElse(null);
    if (spec == null) {
      return invalid(IbanError.UNSUPPORTED_COUNTRY, "country " + countryCode + " does not use IBAN").atPosition(1);
    }
    if (iban.length() != spec.ibanLength()) {
      return invalid(IbanError.INVALID_LENGTH, "IBAN of " + countryCode + " must have " + spec.ibanLength() + " characters, but has " + iban.length()).withLength(spec.ibanLength(), iban.length());
    }
    for (int i = 2; i < 4; i++) {
      if (!Text.isDigit(iban.charAt(i))) {
        return invalid(IbanError.INVALID_CHECK_DIGITS, "check digits must be two digits").atPosition(i + 1)
                                                                                          .expecting(CharacterType.DIGITS);
      }
    }
    BbanStructure structure = spec.bbanStructure();
    int           mismatch  = structure.indexOfMismatch(iban.substring(4));
    if (mismatch >= 0) {
      CharacterType expected = structure.typeAt(mismatch);
      return invalid(IbanError.INVALID_BBAN, "expected a " + Text.describe(expected) + " at position " + (mismatch + 5) + " (BBAN structure of " + countryCode + ": " + structure + ")").atPosition(mismatch + 5)
                                                                                                                                                                                       .expecting(expected);
    }
    if (!IbanCheckDigits.isValid(iban)) {
      return invalid(IbanError.INVALID_CHECK_DIGITS, "check digits " + iban.substring(2, 4) + " are not valid").atPosition(3);
    }
    return ValidationResult.valid(new Iban(iban));
  }

  /**
   * Creates an IBAN from a country code and a BBAN by calculating the check digits. Whitespace is
   * ignored and lower case letters are accepted in both arguments. National check digits inside
   * the BBAN are taken as given; use {@link #builder()} with
   * {@link IbanBuilder#nationalCheckDigits(boolean)} to calculate them.
   *
   * <pre>{@code
   * Iban.of("DE", "370400440532013000");   // DE89370400440532013000
   * }</pre>
   *
   * @param countryCode ISO 3166-1 alpha-2 country code
   * @param bban        the complete BBAN
   * @return the IBAN
   * @throws InvalidIbanException if the country code is missing, the country does not use IBAN or
   *                              the BBAN does not match its structure
   */
  public static Iban of(String countryCode,
                        String bban) {
    return builder().countryCode(countryCode)
                    .bban(bban)
                    .build();
  }

  /**
   * Returns a new builder that composes an IBAN from its parts and calculates the check digits.
   *
   * @return a new, empty builder
   * @see IbanBuilder
   */
  public static IbanBuilder builder() {
    return new IbanBuilder();
  }

  /**
   * Creates a random, valid IBAN of a random country, for example as test data. National check
   * digits inside the BBAN are not calculated; use {@link IbanBuilder#buildRandom(Random)} with
   * {@link IbanBuilder#nationalCheckDigits(boolean)} for that.
   *
   * @param random the source of randomness; the same initial state gives the same IBAN for the same registry data
   * @return a random IBAN
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public static Iban random(Random random) {
    List<IbanCountry> countries = IbanRegistry.countries();
    return random(countries.get(random.nextInt(countries.size()))
                           .countryCode(), random);
  }

  /**
   * Creates a random, valid IBAN of the given country, for example as test data. National check
   * digits inside the BBAN are not calculated; use {@link IbanBuilder#buildRandom(Random)} with
   * {@link IbanBuilder#nationalCheckDigits(boolean)} for that.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code, lower case is accepted
   * @param random      the source of randomness; the same initial state gives the same IBAN for the same registry data
   * @return a random IBAN
   * @throws InvalidIbanException if the country code is missing or the country does not use IBAN
   * @throws NullPointerException if {@code random} is {@code null}
   */
  public static Iban random(String countryCode,
                            Random random) {
    IbanCountry country = IbanBuilder.country(countryCode);
    return of(country.countryCode(), country.bbanStructure()
                                            .random(random));
  }

  static ValidationResult<Iban, IbanError> invalid(IbanError error,
                                                   String message) {
    return ValidationResult.invalid(error, message);
  }

  private static Iban orThrow(ValidationResult<Iban, IbanError> result) {
    if (result.isValid()) {
      return result.value()
                   .orElseThrow();
    }
    throw new InvalidIbanException(result);
  }

  /**
   * Returns the country code, the first two characters of the IBAN.
   *
   * @return ISO 3166-1 alpha-2 country code, for example {@code DE}
   */
  public String countryCode() {
    return value.substring(0, 2);
  }

  /**
   * Returns the IBAN check digits, characters 3 and 4.
   *
   * @return the two check digits, for example {@code 89}
   */
  public String checkDigits() {
    return value.substring(2, 4);
  }

  /**
   * Returns the Basic Bank Account Number, everything after the check digits.
   *
   * @return the BBAN, for example {@code 370400440532013000}
   */
  public String bban() {
    return value.substring(4);
  }

  /**
   * Returns the bank identifier (bank code) at the position the SWIFT IBAN registry defines for
   * the country.
   *
   * @return the bank identifier, for example {@code 37040044}; empty if the registry defines none
   *         for the country
   */
  public Optional<String> bankIdentifier() {
    String bban = bban();
    return country().bankIdentifier()
                    .map(range -> range.extract(bban));
  }

  /**
   * Returns the branch identifier (branch or sort code) at the position the SWIFT IBAN registry
   * defines for the country.
   *
   * @return the branch identifier; empty if the registry defines none for the country, for
   *         example for Germany
   */
  public Optional<String> branchIdentifier() {
    String bban = bban();
    return country().branchIdentifier()
                    .map(range -> range.extract(bban));
  }

  /**
   * Returns the BBAN characters outside the bank and branch identifier of the SWIFT IBAN registry.
   * Depending on the country this includes national check digits, account types or, for France,
   * the branch code, because the registry does not describe them separately: for
   * {@code IT60X0542811101000000123456} it is {@code X000000123456}.
   * <p>
   * For the account number in the national sense use {@link #field(BbanField)} with
   * {@link BbanField#ACCOUNT_NUMBER}, which gives {@code 000000123456} for the same IBAN.
   *
   * @return all BBAN characters outside the bank and branch identifier, in order
   */
  public String accountNumber() {
    IbanCountry   country = country();
    String        bban    = bban();
    StringBuilder sb      = new StringBuilder(bban.length());
    for (int i = 0; i < bban.length(); i++) {
      if (country.isAccountPosition(i)) {
        sb.append(bban.charAt(i));
      }
    }
    return sb.toString();
  }

  /**
   * Returns one field of the BBAN as described by the {@link BbanLayout} of the country, for
   * example the national check digits:
   *
   * <pre>{@code
   * Iban iban = Iban.parse("FR1420041010050500013M02606");
   * iban.field(BbanField.BRANCH_IDENTIFIER);      // Optional[01005]
   * iban.field(BbanField.ACCOUNT_NUMBER);         // Optional[0500013M026]
   * iban.field(BbanField.NATIONAL_CHECK_DIGITS);  // Optional[06]
   * }</pre>
   *
   * @param field the field, or {@code null} to return an empty optional
   * @return the value of the field, empty if the BBAN of the country has no such field
   * @see IbanCountry#bbanLayout()
   */
  public Optional<String> field(BbanField field) {
    String bban = bban();
    return country().bbanLayout()
                    .find(field)
                    .map(definition -> definition.range()
                                                 .extract(bban));
  }

  /**
   * Returns all fields of the BBAN as described by the {@link BbanLayout} of the country.
   *
   * <pre>{@code
   * Iban.parse("DE89370400440532013000").fields();
   * // {BANK_IDENTIFIER=37040044, ACCOUNT_NUMBER=0532013000}
   * }</pre>
   *
   * @return an unmodifiable map from field to value, in BBAN order
   */
  public Map<BbanField, String> fields() {
    String                 bban   = bban();
    Map<BbanField, String> fields = new LinkedHashMap<>();
    for (BbanFieldDefinition definition : country().bbanLayout()
                                                   .fields()) {
      fields.put(definition.field(), definition.range()
                                               .extract(bban));
    }
    return Collections.unmodifiableMap(fields);
  }

  /**
   * Returns the SWIFT IBAN registry definition of the IBAN's country, with its BBAN structure,
   * layout and SEPA membership.
   *
   * @return the registry definition, never {@code null}
   */
  public IbanCountry country() {
    return IbanRegistry.find(countryCode())
                       .orElseThrow();
  }

  /**
   * Checks whether the IBAN's country is part of the SEPA scheme, as published in the SWIFT IBAN
   * registry.
   *
   * @return {@code true} if the country is part of the SEPA scheme
   */
  public boolean isSepa() {
    return country().isSepa();
  }

  /**
   * Formats the IBAN in the requested format.
   *
   * @param format the requested format
   * @return the IBAN in that format
   * @throws NullPointerException if {@code format} is {@code null}
   * @see #toPrintFormat()
   * @see #toString()
   */
  public String format(IbanFormat format) {
    return format.format(value);
  }

  /**
   * Returns the IBAN in print format, the same as {@code format(IbanFormat.PRINT)}.
   *
   * @return the IBAN in print format, for example {@code DE89 3704 0044 0532 0130 00}
   */
  public String toPrintFormat() {
    return IbanFormat.PRINT.format(value);
  }

  /**
   * Validates a deserialized instance, so that a manipulated stream cannot create an invalid IBAN.
   * Called by Java serialization; GWT-RPC validates in Iban_CustomFieldSerializer.
   */
  @Serial
  private Object readResolve() {
    return parse(value, IbanFormat.ELECTRONIC);
  }

  /**
   * Two IBANs are equal if their electronic format is equal, regardless of how they were parsed
   * or built.
   *
   * @param o the object to compare with
   * @return {@code true} if {@code o} is an IBAN with the same electronic format
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof Iban other && value.equals(other.value);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this IBAN
   */
  @Override
  public int hashCode() {
    return value.hashCode();
  }

  /**
   * Returns the IBAN in electronic format, the same as {@code format(IbanFormat.ELECTRONIC)}.
   *
   * @return the IBAN in electronic format, for example {@code DE89370400440532013000}
   */
  @Override
  public String toString() {
    return value;
  }
}

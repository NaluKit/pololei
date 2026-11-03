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
package io.github.nalukit.pololei.creditor;

import io.github.nalukit.pololei.checkdigit.IbanCheckDigits;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * A valid SEPA Creditor Identifier (CI) as used in SEPA direct debits and mandates, defined by
 * the EPC in EPC262-08 (Creditor Identifier Overview).
 *
 * <pre>{@code
 * CreditorIdentifier ci = CreditorIdentifier.parse("DE98ZZZ09999999999");
 * ci.countryCode();          // DE
 * ci.checkDigits();          // 98
 * ci.businessCode();         // ZZZ
 * ci.nationalIdentifier();   // 09999999999
 * CreditorIdentifier.isValid("DE97ZZZ09999999999");   // false: wrong check digits
 * }</pre>
 * <p>
 * Structure: country code (2 letters), check digits (2 digits), creditor business code (3 letters
 * or digits, {@value #NO_BUSINESS_CODE} if not used) and the national identifier of the creditor
 * (up to 28 letters or digits), 8 to 35 characters in all. The check digits are calculated with
 * ISO 7064 MOD 97-10 like IBAN check digits, over the national identifier and the country code;
 * the business code is not part of them, so a creditor can use several business codes with the
 * same check digits. Input may contain whitespace and lower case letters.
 * <p>
 * Country scope: every country taking part in SEPA ({@link IbanCountry#isSepa()}) gets the
 * general checks above. For the countries of {@link #hasNationalFormat(String)} the national
 * length is checked as well; currently Germany (18 characters, Deutsche Bundesbank). For other
 * countries the national identifier is not checked against its national definition.
 * <p>
 * A valid identifier is formally correct; it is not necessarily issued. Creditor Identifiers are
 * assigned by national authorities (in Germany the Deutsche Bundesbank), and {@link #of} only
 * calculates check digits, for example for test data.
 * <p>
 * Instances are immutable and thread-safe and work on the JVM and in GWT (inherit
 * {@code io.github.nalukit.pololei.creditor.PololeiCreditor}).
 */
public final class CreditorIdentifier {

  /** The creditor business code of an identifier that does not use one. */
  public static final String NO_BUSINESS_CODE = "ZZZ";

  /** Minimum length: country code, check digits, business code and one national character. */
  public static final int MIN_LENGTH = 8;

  /** Maximum length of a Creditor Identifier. */
  public static final int MAX_LENGTH = 35;

  private final String value;

  private CreditorIdentifier(String value) {
    this.value = value;
  }

  /**
   * Parses and validates a Creditor Identifier.
   *
   * @param input the identifier, may be {@code null}; spaces, tabs, line feeds, carriage returns
   *              and non-breaking spaces (U+00A0) are ignored, and ASCII a-z are converted to A-Z
   * @return the identifier
   * @throws InvalidCreditorIdentifierException if the input is not a valid Creditor Identifier;
   *                                            {@link InvalidCreditorIdentifierException#error()}
   *                                            tells why
   */
  public static CreditorIdentifier parse(CharSequence input) {
    String normalized = normalize(input);
    check(normalized, true);
    return new CreditorIdentifier(normalized);
  }

  /**
   * Parses a Creditor Identifier like {@link #parse(CharSequence)}, but returns an empty optional
   * instead of throwing an exception.
   *
   * @param input the identifier, may be {@code null}
   * @return the identifier, empty if the input is not valid
   */
  public static Optional<CreditorIdentifier> tryParse(CharSequence input) {
    return validate(input).isPresent() ? Optional.empty() : Optional.of(parse(input));
  }

  /**
   * Checks whether the input is a valid Creditor Identifier.
   *
   * @param input the identifier, may be {@code null}
   * @return {@code true} if it is valid
   */
  public static boolean isValid(CharSequence input) {
    return validate(input).isEmpty();
  }

  /**
   * Validates a Creditor Identifier and returns the first error. Checks run in this order:
   * empty input, allowed characters, general length, country code syntax, supported country,
   * known national length, check digit syntax and MOD 97 check digits. No issuing authority is
   * contacted. Invalid input, including {@code null}, is reported as an error.
   *
   * <pre>{@code
   * CreditorIdentifier.validate("DE98ZZZ09999999999");   // Optional.empty()
   * CreditorIdentifier.validate("DE98ZZZ0999999999");    // Optional[INVALID_LENGTH]
   * CreditorIdentifier.validate("US98ZZZ09999999999");   // Optional[UNSUPPORTED_COUNTRY]
   * }</pre>
   *
   * @param input the identifier, may be {@code null}
   * @return the error, empty if the input is valid
   */
  public static Optional<CreditorIdentifierError> validate(CharSequence input) {
    try {
      check(normalize(input), true);
      return Optional.empty();
    } catch (InvalidCreditorIdentifierException e) {
      return Optional.of(e.error());
    }
  }

  /**
   * Creates a Creditor Identifier and calculates its check digits, for example as test data. The
   * result is formally valid; it is not an identifier issued by a national authority.
   *
   * <pre>{@code
   * CreditorIdentifier.of("DE", null, "09999999999");   // DE98ZZZ09999999999
   * }</pre>
   *
   * All parts are normalized as in {@link #parse(CharSequence)} before validation.
   *
   * @param countryCode        the country code of a SEPA country
   * @param businessCode       the creditor business code (3 letters or digits), {@code null} or
   *                           empty for {@value #NO_BUSINESS_CODE}
   * @param nationalIdentifier the national identifier of the creditor
   * @return the identifier
   * @throws InvalidCreditorIdentifierException if the parts do not form a valid identifier
   * @throws NullPointerException               if {@code countryCode} or
   *                                            {@code nationalIdentifier} is {@code null}
   */
  public static CreditorIdentifier of(String countryCode,
                                      String businessCode,
                                      String nationalIdentifier) {
    Objects.requireNonNull(countryCode, "countryCode");
    Objects.requireNonNull(nationalIdentifier, "nationalIdentifier");
    String business = businessCode == null || normalize(businessCode).isEmpty() ? NO_BUSINESS_CODE : normalize(businessCode);
    if (business.length() != 3) {
      throw new InvalidCreditorIdentifierException(CreditorIdentifierError.INVALID_LENGTH, "the business code must have 3 characters, but has " + business.length(), 0);
    }
    String country  = normalize(countryCode);
    String national = normalize(nationalIdentifier);
    String withoutCheckDigits = country + "00" + business + national;
    check(withoutCheckDigits, false);
    return parse(country + IbanCheckDigits.calculate(country, national) + business + national);
  }

  /**
   * Tells whether Creditor Identifiers of a country are accepted: the country takes part in SEPA.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; case sensitive, no normalization, may be {@code null}
   * @return {@code true} for a SEPA country in the bundled registry; {@code false} for
   *         {@code null}, an unknown code or a non-SEPA country
   */
  public static boolean isSupportedCountry(String countryCode) {
    return countryCode != null && IbanRegistry.find(countryCode)
                                              .map(IbanCountry::isSepa)
                                              .orElse(false);
  }

  /**
   * Tells whether pololei checks the national length of the identifiers of a country in addition
   * to the general rules; currently Germany only.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; case sensitive, no normalization, may be {@code null}
   * @return {@code true} if the national length is checked; {@code false} for {@code null}
   *         or an unknown code
   */
  public static boolean hasNationalFormat(String countryCode) {
    return nationalLength(countryCode) > 0;
  }

  /** @return the length of an identifier of the country, 0 if pololei does not know it */
  private static int nationalLength(String countryCode) {
    // Deutsche Bundesbank: DE, check digits, business code, national identifier of 11 characters
    return "DE".equals(countryCode) ? 18 : 0;
  }

  private static String normalize(CharSequence input) {
    if (input == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      // the whitespace Iban.parse ignores: space, tab, line breaks and no-break space
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '\u00A0') {
        sb.append(c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c);
      }
    }
    return sb.toString();
  }

  /** Throws for the first rule the identifier breaks; check digits only if requested. */
  private static void check(String ci,
                            boolean checkDigits) {
    if (ci.isEmpty()) {
      throw invalid(CreditorIdentifierError.EMPTY, "the Creditor Identifier is empty", 0);
    }
    for (int i = 0; i < ci.length(); i++) {
      if (!isAlphanumeric(ci.charAt(i))) {
        throw invalid(CreditorIdentifierError.INVALID_CHARACTER, "invalid character at position " + (i + 1) + ", only letters and digits are allowed", i + 1);
      }
    }
    if (ci.length() < MIN_LENGTH || ci.length() > MAX_LENGTH) {
      throw invalid(CreditorIdentifierError.INVALID_LENGTH, "a Creditor Identifier has " + MIN_LENGTH + " to " + MAX_LENGTH + " characters, but this one has " + ci.length(), 0);
    }
    for (int i = 0; i < 2; i++) {
      if (ci.charAt(i) < 'A' || ci.charAt(i) > 'Z') {
        throw invalid(CreditorIdentifierError.INVALID_COUNTRY_CODE, "the country code must consist of two letters", i + 1);
      }
    }
    String country = ci.substring(0, 2);
    if (!isSupportedCountry(country)) {
      throw invalid(CreditorIdentifierError.UNSUPPORTED_COUNTRY, "country " + country + " does not take part in SEPA", 1);
    }
    int length = nationalLength(country);
    if (length > 0 && ci.length() != length) {
      throw invalid(CreditorIdentifierError.INVALID_LENGTH, "a Creditor Identifier of " + country + " has " + length + " characters, but this one has " + ci.length(), 0);
    }
    if (!checkDigits) {
      return;
    }
    for (int i = 2; i < 4; i++) {
      if (ci.charAt(i) < '0' || ci.charAt(i) > '9') {
        throw invalid(CreditorIdentifierError.INVALID_CHECK_DIGITS, "the check digits must be digits", i + 1);
      }
    }
    if (!IbanCheckDigits.calculate(country, ci.substring(7))
                        .equals(ci.substring(2, 4))) {
      throw invalid(CreditorIdentifierError.INVALID_CHECK_DIGITS, "the check digits are not valid", 3);
    }
  }

  private static boolean isAlphanumeric(char c) {
    return c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
  }

  private static InvalidCreditorIdentifierException invalid(CreditorIdentifierError error,
                                                            String message,
                                                            int position) {
    return new InvalidCreditorIdentifierException(error, message, position);
  }

  /**
   * Returns the country code.
   *
   * @return characters 1-2, for example {@code DE}
   */
  public String countryCode() {
    return value.substring(0, 2);
  }

  /**
   * Returns the check digits.
   *
   * @return characters 3-4, for example {@code 98}
   */
  public String checkDigits() {
    return value.substring(2, 4);
  }

  /**
   * Returns the creditor business code, which a creditor chooses to tell apart its business
   * lines; it is not part of the check digits.
   *
   * @return characters 5-7, {@value #NO_BUSINESS_CODE} if not used
   */
  public String businessCode() {
    return value.substring(4, 7);
  }

  /**
   * Returns the national identifier of the creditor.
   *
   * @return characters 8 to the end, for example {@code 09999999999}
   */
  public String nationalIdentifier() {
    return value.substring(7);
  }

  /**
   * Returns the identifier with another creditor business code. The check digits stay the same,
   * because the business code is not part of them.
   *
   * @param businessCode 3 letters or digits, {@code null} or empty for {@value #NO_BUSINESS_CODE}
   * @return the identifier with the business code
   * @throws InvalidCreditorIdentifierException if the business code does not have 3 letters or
   *                                            digits
   */
  public CreditorIdentifier withBusinessCode(String businessCode) {
    return of(countryCode(), businessCode, nationalIdentifier());
  }

  /**
   * Tells whether two identifiers belong to the same creditor: they are equal except for the
   * creditor business code.
   *
   * @param other another identifier
   * @return {@code true} if country code and national identifier are equal
   * @throws NullPointerException if {@code other} is {@code null}
   */
  public boolean isSameCreditor(CreditorIdentifier other) {
    return countryCode().equals(other.countryCode()) && nationalIdentifier().equals(other.nationalIdentifier());
  }

  /**
   * Compares the complete normalized identifiers, including their business codes.
   *
   * @param o the object to compare with, may be {@code null}
   * @return {@code true} if the object is an identifier with the same electronic representation
   * @see #isSameCreditor(CreditorIdentifier)
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof CreditorIdentifier && value.equals(((CreditorIdentifier) o).value);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this value
   */
  @Override
  public int hashCode() {
    return value.hashCode();
  }

  /**
   * Returns the identifier in electronic format: upper case, without whitespace.
   *
   * @return for example {@code DE98ZZZ09999999999}
   */
  @Override
  public String toString() {
    return value;
  }
}

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
import io.github.nalukit.pololei.national.NationalCheck;
import io.github.nalukit.pololei.national.NationalCheckOutcome;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.BbanFieldDefinition;
import io.github.nalukit.pololei.registry.BbanLayout;
import io.github.nalukit.pololei.registry.BbanRange;
import io.github.nalukit.pololei.registry.BbanStructure;
import io.github.nalukit.pololei.registry.CharacterType;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;


/**
 * Composes an IBAN and calculates its check digits.
 * <p>
 * A builder is mutable and <b>not thread-safe</b>: create one per use with {@link Iban#builder()}
 * instead of sharing it between threads or requests. The built {@link Iban} is immutable.
 * <p>
 * There are three ways to describe the BBAN; they cannot be mixed:
 * <ul>
 *   <li>the complete {@link #bban(String) BBAN},</li>
 *   <li>the registry parts: {@link #bankIdentifier(String) bank identifier},
 *       {@link #branchIdentifier(String) branch identifier} and the
 *       {@link #accountNumber(String) account number}, which fills all remaining positions in order
 *       (the counterpart of {@link Iban#accountNumber()}),</li>
 *   <li>the national {@link #field(BbanField, String) fields} of the country's
 *       {@link BbanLayout}, for example account number and national check digits (the counterpart
 *       of {@link Iban#field(BbanField)}); bank and branch identifier can be set with their own
 *       methods as well.</li>
 * </ul>
 *
 * <pre>{@code
 * Iban.builder()
 *     .countryCode("DE")
 *     .bankIdentifier("37040044")
 *     .accountNumber("532013000")
 *     .padAccountNumber(true)
 *     .build();                                      // DE89370400440532013000
 *
 * Iban.builder()
 *     .countryCode("FR")
 *     .bankIdentifier("20041")
 *     .branchIdentifier("01005")
 *     .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
 *     .nationalCheckDigits(true)                     // calculates the RIB key 06
 *     .build();                                      // FR1420041010050500013M02606
 *
 * Iban.builder()
 *     .countryCode("GB")
 *     .bankIdentifier("NWBK")
 *     .buildRandom(new Random(42));                  // random branch and account number
 * }</pre>
 * <p>
 * {@link #buildRandom(Random)} only uses the given {@link Random}; for test data generated in
 * parallel, pass {@code ThreadLocalRandom.current()} or one seeded {@code Random} per thread
 * instead of sharing one instance, which is thread-safe but contended.
 */
public final class IbanBuilder {

  /** Upper bound for drawing random values again when national check digits cannot be solved. */
  static final int MAX_RANDOM_ATTEMPTS = 100;

  private final Map<BbanField, String> fields = new LinkedHashMap<>();

  private String  countryCode;
  private String  bban;
  private String  accountNumber;
  private boolean padBankIdentifier;
  private boolean padBranchIdentifier;
  private boolean padAccountNumber;
  private char    paddingCharacter = '0';
  private boolean nationalCheckDigits;

  IbanBuilder() {
  }

  static IbanCountry country(String countryCode) {
    if (countryCode == null || countryCode.isEmpty()) {
      throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_COUNTRY_CODE, "country code is missing"));
    }
    return IbanRegistry.find(Text.normalize(countryCode))
                       .orElseThrow(() -> new InvalidIbanException(Iban.invalid(IbanError.UNSUPPORTED_COUNTRY, "country " + countryCode + " does not use IBAN")));
  }

  /**
   * Sets the country of the IBAN; mandatory. Normalization and validation are deferred to
   * {@link #build()} or {@link #buildRandom(Random)}. A {@code null} value clears the country;
   * building without one fails.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code; ASCII lower case and supported whitespace are normalized
   * @return this builder
   */
  public IbanBuilder countryCode(String countryCode) {
    this.countryCode = countryCode;
    return this;
  }

  /**
   * Sets the complete BBAN; cannot be combined with {@link #accountNumber(String)} or
   * {@link #field(BbanField, String)} (including bank and branch identifier). Whitespace is ignored
   * and lower case letters are accepted.
   *
   * @param bban the complete BBAN, or {@code null} to remove it again
   * @return this builder
   */
  public IbanBuilder bban(String bban) {
    this.bban = bban;
    return this;
  }

  /**
   * Sets the bank identifier; the same as {@code field(BbanField.BANK_IDENTIFIER, bankIdentifier)}.
   * Can be combined with {@link #accountNumber(String)} as well as with the other fields.
   *
   * @param bankIdentifier the bank identifier (bank code), {@code null} or empty to remove it again
   * @return this builder
   * @see #padBankIdentifier(boolean)
   */
  public IbanBuilder bankIdentifier(String bankIdentifier) {
    return field(BbanField.BANK_IDENTIFIER, bankIdentifier);
  }

  /**
   * Sets the branch identifier; the same as
   * {@code field(BbanField.BRANCH_IDENTIFIER, branchIdentifier)}. Can be combined with
   * {@link #accountNumber(String)} as well as with the other fields.
   *
   * @param branchIdentifier the branch identifier (branch or sort code), {@code null} or empty to
   *                         remove it again
   * @return this builder
   * @see #padBranchIdentifier(boolean)
   */
  public IbanBuilder branchIdentifier(String branchIdentifier) {
    return field(BbanField.BRANCH_IDENTIFIER, branchIdentifier);
  }

  /**
   * Sets all BBAN characters outside the bank and branch identifier of the registry, in order;
   * the counterpart of {@link Iban#accountNumber()}. Cannot be combined with
   * {@link #field(BbanField, String)} for other fields than bank and branch identifier.
   * Whitespace is ignored and lower case letters are accepted.
   *
   * @param accountNumber all BBAN characters outside bank and branch identifier, in order, or
   *                      {@code null} to remove it again
   * @return this builder
   * @see #padAccountNumber(boolean)
   */
  public IbanBuilder accountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
    return this;
  }

  /**
   * Sets one field of the country's {@link BbanLayout}; the counterpart of
   * {@link Iban#field(BbanField)}. {@code null} or an empty value removes the field again.
   * Whitespace is ignored and lower case letters are accepted. Whether the country has the field
   * and whether the value fits is checked when building.
   *
   * @param field the field
   * @param value its value, or {@code null} or an empty string to remove the field;
   *              a whitespace-only value is retained and fails validation when building
   * @return this builder
   * @throws NullPointerException if {@code field} is {@code null}
   */
  public IbanBuilder field(BbanField field,
                           String value) {
    Objects.requireNonNull(field, "field");
    if (value == null || value.isEmpty()) {
      fields.remove(field);
    } else {
      fields.put(field, value);
    }
    return this;
  }

  /**
   * Left pads a short account number with the {@link #paddingCharacter(char) padding character}.
   * Applies to {@link #accountNumber(String)} and to {@link BbanField#ACCOUNT_NUMBER}. The
   * {@link #accountNumber(String)} of a country whose remaining positions contain more than the
   * account number (for example the Italian CIN) cannot be padded, use the field instead. An empty
   * value is not padded.
   *
   * @param padAccountNumber {@code true} to enable padding (disabled by default) of a short account number
   * @return this builder
   */
  public IbanBuilder padAccountNumber(boolean padAccountNumber) {
    this.padAccountNumber = padAccountNumber;
    return this;
  }

  /**
   * Left pads a short bank identifier with the {@link #paddingCharacter(char) padding character},
   * for example {@code 123} to {@code 00000123} for Germany. An empty value is not padded.
   *
   * @param padBankIdentifier {@code true} to enable padding (disabled by default) of a short bank identifier
   * @return this builder
   */
  public IbanBuilder padBankIdentifier(boolean padBankIdentifier) {
    this.padBankIdentifier = padBankIdentifier;
    return this;
  }

  /**
   * Left pads a short branch identifier with the {@link #paddingCharacter(char) padding
   * character}. An empty value is not padded.
   *
   * @param padBranchIdentifier {@code true} to enable padding (disabled by default) of a short branch identifier
   * @return this builder
   */
  public IbanBuilder padBranchIdentifier(boolean padBranchIdentifier) {
    this.padBranchIdentifier = padBranchIdentifier;
    return this;
  }

  /**
   * Sets the character used for padding, {@code 0} by default. It must be allowed at every
   * position it fills, so a letter cannot pad a numeric field; this is checked when building.
   *
   * @param paddingCharacter an ASCII letter or digit; a-z are converted to A-Z
   * @return this builder
   * @throws IllegalArgumentException if the character is neither a letter nor a digit
   */
  public IbanBuilder paddingCharacter(char paddingCharacter) {
    char upper = paddingCharacter >= 'a' && paddingCharacter <= 'z' ? (char) (paddingCharacter - 'a' + 'A') : paddingCharacter;
    if (!Text.isAlphanumeric(upper)) {
      throw new IllegalArgumentException("padding character must be a letter or a digit: " + Text.describe(paddingCharacter));
    }
    this.paddingCharacter = upper;
    return this;
  }

  /**
   * Enables or disables calculation of the national check digits. When enabled, the built-in
   * rule of the country is used (see
   * {@link io.github.nalukit.pololei.national.NationalChecks}) and writes them into the BBAN,
   * replacing what was given at their positions. Building fails if pololei has no rule for the
   * country; {@link BbanField#NATIONAL_CHECK_DIGITS} must not be set. When the national check
   * digits are part of the BBAN given with {@link #bban(String)} or
   * {@link #accountNumber(String)}, any characters allowed by the BBAN structure can be given at
   * their positions, for example {@code 00}. Rules configured on an {@link IbanValidator} are
   * not used by this builder. Disabling this option preserves the supplied national check digits
   * without verifying them.
   *
   * @param nationalCheckDigits {@code true} to calculate the national check digits,
   *                            {@code false} (the default) to take the BBAN as given
   * @return this builder
   * @see io.github.nalukit.pololei.national.NationalChecks#find(String)
   */
  public IbanBuilder nationalCheckDigits(boolean nationalCheckDigits) {
    this.nationalCheckDigits = nationalCheckDigits;
    return this;
  }

  /**
   * Builds the IBAN and calculates its check digits. The builder is not changed and can be used
   * again, for example after changing the account number. International check digits are always
   * calculated; national check digits are calculated only when explicitly enabled. No account
   * existence check is performed.
   *
   * @return the IBAN
   * @throws InvalidIbanException     if the country code is missing, the country does not use
   *                                  IBAN, a part is missing, has the wrong length or characters,
   *                                  parts contradict each other, or the national check digits
   *                                  cannot be calculated; {@link InvalidIbanException#result()}
   *                                  carries the details, positions are IBAN positions
   * @throws IllegalStateException    if the BBAN is described in more than one way, or national
   *                                  check digits are both set and calculated
   * @throws IllegalArgumentException if the padding character is not allowed in a field it pads
   */
  public Iban build() {
    IbanCountry country = country(countryCode);
    checkCombination();
    if (bban != null) {
      String normalized = Text.normalize(bban);
      checkCharacters(normalized);
      return finish(country, nationalCheckDigits ? complete(country, normalized) : normalized);
    }
    Composition composition = compose(country);
    if (nationalCheckDigits) {
      composition.placeholder(BbanField.NATIONAL_CHECK_DIGITS);
    }
    composition.requireComplete();
    String composed = new String(composition.chars);
    return finish(country, nationalCheckDigits ? complete(country, composed) : composed);
  }

  /**
   * Builds an IBAN whose missing BBAN parts are random, for example as test data. Values that are
   * set are normalized and padded according to the builder options. Their characters are kept,
   * except that enabling {@link #nationalCheckDigits(boolean)} replaces national check digit
   * positions with calculated values. The builder is unchanged; the random source is advanced.
   * Generated values satisfy the international rules but do not identify issued accounts.
   *
   * @param random the source of randomness; the same seed and the same settings give the same IBAN
   * @return the IBAN
   * @throws InvalidIbanException     if the country code is missing, the country does not use IBAN,
   *                                  a given value is not valid for the country, or no valid
   *                                  national rule is available, or national check digits cannot be
   *                                  calculated; random generation stops after at most
   *                                  {@value #MAX_RANDOM_ATTEMPTS} attempts, or after the first
   *                                  failed attempt if no BBAN positions are free
   * @throws IllegalStateException    if a complete BBAN is set, the BBAN is described in more than
   *                                  one way, or national check digits are both set and calculated
   * @throws IllegalArgumentException if the padding character is not allowed in a field it pads
   * @throws NullPointerException     if {@code random} is {@code null}
   */
  public Iban buildRandom(Random random) {
    Objects.requireNonNull(random, "random");
    IbanCountry country = country(countryCode);
    if (bban != null) {
      throw new IllegalStateException("a complete BBAN leaves nothing to choose at random");
    }
    checkCombination();
    Composition   composition = compose(country);
    NationalCheck rule        = nationalCheckDigits ? rule(country) : null;
    if (nationalCheckDigits) {
      // calculated, not drawn
      composition.placeholder(BbanField.NATIONAL_CHECK_DIGITS);
    }
    boolean       digitsOnly  = rule != null && rule.requiresDigits();
    for (int attempt = 1; ; attempt++) {
      String drawn = composition.fill(random, digitsOnly);
      if (!nationalCheckDigits) {
        return finish(country, drawn);
      }
      Optional<String> completed = rule.complete(drawn);
      if (completed.isPresent()) {
        return finish(country, completed.get());
      }
      if (attempt >= MAX_RANDOM_ATTEMPTS || !composition.hasFreePositions()) {
        throw new InvalidIbanException(nationalError(rule, country, "no valid national check digits of " + country.countryCode() + " found after " + attempt + " random attempt(s)"));
      }
    }
  }

  private void checkCombination() {
    if (bban != null && (accountNumber != null || !fields.isEmpty())) {
      throw new IllegalStateException("set either the BBAN or its parts, not both");
    }
    if (accountNumber != null) {
      for (BbanField field : fields.keySet()) {
        if (field != BbanField.BANK_IDENTIFIER && field != BbanField.BRANCH_IDENTIFIER) {
          throw new IllegalStateException("accountNumber() cannot be combined with field(" + field + ", ...); use field(ACCOUNT_NUMBER, ...)");
        }
      }
    }
    if (nationalCheckDigits && fields.containsKey(BbanField.NATIONAL_CHECK_DIGITS)) {
      throw new IllegalStateException("national check digits are either set or calculated, not both");
    }
  }

  private static void checkCharacters(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Text.isAlphanumeric(value.charAt(i))) {
        throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_CHARACTER, "invalid character " + Text.describe(value.charAt(i)) + " at position " + (i + 5))
                                           .atPosition(i + 5)
                                           .expecting(CharacterType.ALPHANUMERIC));
      }
    }
  }

  private static Iban finish(IbanCountry country,
                             String bban) {
    String code = country.countryCode();
    checkCharacters(bban);
    return Iban.parse(code + IbanCheckDigits.calculate(code, bban) + bban);
  }

  private static NationalCheck rule(IbanCountry country) {
    return NationalChecks.find(country.countryCode())
                         .orElseThrow(() -> new InvalidIbanException(Iban.invalid(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, "no national check for country " + country.countryCode())));
  }

  private static String complete(IbanCountry country,
                                 String bban) {
    NationalCheck rule = rule(country);
    if (!country.bbanStructure()
                .matches(bban)) {
      // let the IBAN validation report the structure error
      return bban;
    }
    if (rule.check(bban) == NationalCheckOutcome.NOT_APPLICABLE) {
      throw new InvalidIbanException(Iban.invalid(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, "national check " + rule.id() + " cannot check this BBAN of " + country.countryCode()));
    }
    return rule.complete(bban)
               .orElseThrow(() -> new InvalidIbanException(nationalError(rule, country, "the BBAN of " + country.countryCode() + " has no valid national check digits")));
  }

  private static ValidationResult<Iban, IbanError> nationalError(NationalCheck rule,
                                                                 IbanCountry country,
                                                                 String message) {
    BbanRange range = rule.checkRange();
    return Iban.invalid(IbanError.INVALID_NATIONAL_CHECK_DIGITS, message + " (rule " + rule.id() + ")")
               .withRule(rule.id(), range.start() + 4, range.end() + 4);
  }

  private Composition compose(IbanCountry country) {
    Composition composition = new Composition(country);
    if (accountNumber != null) {
      composeFromRegistryParts(composition, country);
    } else {
      composeFromFields(composition, country);
    }
    return composition;
  }

  /** Bank, branch and the remaining positions as defined by the registry. */
  private void composeFromRegistryParts(Composition composition,
                                        IbanCountry country) {
    BbanRange bankRange   = country.bankIdentifier()
                                   .orElse(null);
    BbanRange branchRange = country.branchIdentifier()
                                   .orElse(null);
    String    bank        = part(fields.get(BbanField.BANK_IDENTIFIER), bankRange, BbanField.BANK_IDENTIFIER, padBankIdentifier, country);
    String    branch      = part(fields.get(BbanField.BRANCH_IDENTIFIER), branchRange, BbanField.BRANCH_IDENTIFIER, padBranchIdentifier, country);
    int       free        = country.accountNumberLength();
    String    account     = Text.normalize(accountNumber);
    if (padAccountNumber && !account.isEmpty() && account.length() < free) {
      Optional<BbanFieldDefinition> nationalAccount = country.bbanLayout()
                                                             .find(BbanField.ACCOUNT_NUMBER);
      if (nationalAccount.isEmpty() || nationalAccount.get()
                                                      .length() != free) {
        throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_LENGTH, "account number of " + country.countryCode() + " must have " + free + " characters, but has " + account.length() +
                                                                              "; it also contains other fields and cannot be padded, use field(ACCOUNT_NUMBER, ...)")
                                           .withLength(free, account.length()));
      }
      account = pad(account, free, nationalAccount.get()
                                                  .range(), BbanField.ACCOUNT_NUMBER, country);
    }
    if (account.length() != free) {
      throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_LENGTH, "account number of " + country.countryCode() + " must have " + free + " characters, but has " + account.length())
                                         .withLength(free, account.length()));
    }
    int next = 0;
    for (int i = 0; i < country.bbanLength(); i++) {
      if (country.isAccountPosition(i)) {
        composition.set(i, account.charAt(next++), BbanField.ACCOUNT_NUMBER);
      }
    }
    composition.set(bankRange, bank, BbanField.BANK_IDENTIFIER);
    composition.set(branchRange, branch, BbanField.BRANCH_IDENTIFIER);
  }

  private String part(String value,
                      BbanRange range,
                      BbanField field,
                      boolean padding,
                      IbanCountry country) {
    if (range == null) {
      if (value != null && !value.isEmpty()) {
        throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_BBAN, "the BBAN of " + country.countryCode() + " has no " + field.label()));
      }
      return null;
    }
    String normalized = value == null ? "" : Text.normalize(value);
    int    expected   = range.length();
    if (padding && !normalized.isEmpty() && normalized.length() < expected) {
      normalized = pad(normalized, expected, range, field, country);
    }
    if (normalized.isEmpty() && value == null) {
      // missing; reported by Composition.requireComplete, or drawn at random
      return null;
    }
    if (normalized.length() != expected) {
      throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_LENGTH, field.label() + " of " + country.countryCode() + " must have " + expected + " characters, but has " + normalized.length())
                                         .withLength(expected, normalized.length()));
    }
    return normalized;
  }

  /** The national fields of the layout. */
  private void composeFromFields(Composition composition,
                                 IbanCountry country) {
    BbanLayout layout = country.bbanLayout();
    for (Map.Entry<BbanField, String> entry : fields.entrySet()) {
      BbanField                     field      = entry.getKey();
      Optional<BbanFieldDefinition> definition = layout.find(field);
      if (definition.isEmpty()) {
        throw new InvalidIbanException(Iban.invalid(IbanError.UNDEFINED_FIELD, "the BBAN of " + country.countryCode() + " has no " + field.label()));
      }
      String value = Text.normalize(entry.getValue());
      if (padding(field) && !value.isEmpty() && value.length() < definition.get()
                                                                           .length()) {
        value = pad(value, definition.get()
                                     .length(), definition.get()
                                                          .range(), field, country);
      }
      ValidationResult<String, IbanError> result = Bban.checkField(country, definition.get(), value);
      if (!result.isValid()) {
        ValidationResult<Iban, IbanError> error = result.retype();
        // field relative positions become IBAN positions
        throw new InvalidIbanException(error.position()
                                            .isPresent() ? error.atPosition(error.position()
                                                                                 .getAsInt() + definition.get()
                                                                                                         .range()
                                                                                                         .start() + 3) : error);
      }
      composition.set(definition.get()
                                .range(), value, field);
    }
  }

  private boolean padding(BbanField field) {
    return switch (field) {
      case BANK_IDENTIFIER -> padBankIdentifier;
      case BRANCH_IDENTIFIER -> padBranchIdentifier;
      case ACCOUNT_NUMBER -> padAccountNumber;
      default -> false;
    };
  }

  private String pad(String value,
                     int length,
                     BbanRange range,
                     BbanField field,
                     IbanCountry country) {
    BbanStructure structure = country.bbanStructure();
    for (int i = range.start() - 1; i < range.start() - 1 + length - value.length(); i++) {
      if (!structure.typeAt(i)
                    .matches(paddingCharacter)) {
        throw new IllegalArgumentException("padding character " + Text.describe(paddingCharacter) + " is not allowed in the " + field.label() + " of " + country.countryCode());
      }
    }
    return String.valueOf(paddingCharacter)
                 .repeat(length - value.length()) + value;
  }

  /** A BBAN under construction: positions set by the caller and positions still free. */
  private static final class Composition {

    private final IbanCountry country;
    private final char[]      chars;
    private final BbanField[] setBy;

    private Composition(IbanCountry country) {
      this.country = country;
      this.chars   = new char[country.bbanLength()];
      this.setBy   = new BbanField[country.bbanLength()];
    }

    private void set(BbanRange range,
                     String value,
                     BbanField field) {
      if (range != null && value != null) {
        for (int i = 0; i < value.length(); i++) {
          set(range.start() - 1 + i, value.charAt(i), field);
        }
      }
    }

    private void set(int index,
                     char c,
                     BbanField field) {
      if (setBy[index] != null && chars[index] != c) {
        throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_BBAN, field.label() + " of " + country.countryCode() + " contradicts the " + setBy[index].label() + " at position " + (index + 5))
                                           .atPosition(index + 5));
      }
      chars[index] = c;
      setBy[index] = field;
    }

    /** Sets the free positions of a field to a placeholder of the right type, to be calculated later. */
    private void placeholder(BbanField field) {
      country.bbanLayout()
             .find(field)
             .ifPresent(definition -> {
               for (int i = definition.range()
                                      .start() - 1; i < definition.range()
                                                                  .end(); i++) {
                 if (setBy[i] == null) {
                   chars[i] = country.bbanStructure()
                                     .typeAt(i) == CharacterType.UPPER_CASE_LETTERS ? 'A' : '0';
                   setBy[i] = field;
                 }
               }
             });
    }

    private void requireComplete() {
      for (int i = 0; i < chars.length; i++) {
        if (setBy[i] == null) {
          Optional<BbanField> field = country.bbanLayout()
                                             .fieldAt(i);
          int length = field.flatMap(f -> country.bbanLayout()
                                                 .find(f))
                            .map(BbanFieldDefinition::length)
                            .orElse(country.bbanLength());
          String label = field.map(BbanField::label)
                              .orElse("BBAN");
          throw new InvalidIbanException(Iban.invalid(IbanError.INVALID_LENGTH, label + " of " + country.countryCode() + " must have " + length + " characters, but has 0")
                                             .withLength(length, 0));
        }
      }
    }

    private boolean hasFreePositions() {
      for (int i = 0; i < setBy.length; i++) {
        if (setBy[i] == null) {
          return true;
        }
      }
      return false;
    }

    /** @return the BBAN with all free positions drawn at random */
    private String fill(Random random,
                        boolean digitsOnly) {
      char[]        result    = new char[chars.length];
      System.arraycopy(chars, 0, result, 0, chars.length);
      BbanStructure structure = country.bbanStructure();
      for (int i = 0; i < result.length; i++) {
        if (setBy[i] == null) {
          CharacterType type = structure.typeAt(i);
          result[i] = digitsOnly && type == CharacterType.ALPHANUMERIC ? CharacterType.DIGITS.randomChar(random) : type.randomChar(random);
        }
      }
      return new String(result);
    }
  }
}

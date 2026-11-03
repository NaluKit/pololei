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
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.national.NationalCheck;
import io.github.nalukit.pololei.national.NationalCheckOutcome;
import io.github.nalukit.pololei.national.NationalChecks;
import io.github.nalukit.pololei.registry.BbanRange;
import io.github.nalukit.pololei.registry.IbanRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A reusable, immutable IBAN validator with options, for example national check digits.
 * <p>
 * {@link #standard()} behaves exactly like {@link Iban#validate(CharSequence)}. Every option
 * returns a new validator; instances are immutable and thread-safe, so one validator can be kept in
 * a static field or a singleton and used by all requests at the same time.
 *
 * <pre>{@code
 * IbanValidator validator = IbanValidator.standard()
 *                                        .nationalCheck(NationalCheckMode.IF_SUPPORTED);
 * validator.isValid("FR1420041010050500013M02606");    // true
 * validator.isValid("FR8420041010050500013M02607");    // false: RIB key wrong, IBAN check digits fine
 * validator.validate(input).nationalCheck();           // PASSED, FAILED, NOT_SUPPORTED, ...
 * }</pre>
 * <p>
 * The national check runs after all international checks, so its error
 * {@link IbanError#INVALID_NATIONAL_CHECK_DIGITS} comes last. Parsing, {@link #tryParse} and
 * {@link #isValid} use the same rules as {@link #validate}.
 * <p>
 * Own national rules are added per validator with {@link #nationalRule(NationalCheck)}; they add
 * a rule for a country without one or replace the built-in rule of
 * {@link NationalChecks}. There is no global registration, so other validators keep their rules.
 */
public final class IbanValidator {

  private static final IbanValidator STANDARD = new IbanValidator(NationalCheckMode.OFF, null, Collections.emptyMap());

  private final NationalCheckMode          nationalCheck;
  private final IbanFormat                 format;
  // country code -> own rule, takes precedence over NationalChecks
  private final Map<String, NationalCheck> rules;

  private IbanValidator(NationalCheckMode nationalCheck,
                        IbanFormat format,
                        Map<String, NationalCheck> rules) {
    this.nationalCheck = nationalCheck;
    this.format        = format;
    this.rules         = rules;
  }

  /**
   * Returns the default validator: no national checks, electronic and print format, whitespace
   * and lower case are accepted. It behaves exactly like {@link Iban#validate(CharSequence)} and
   * is the starting point for validators with other options.
   *
   * @return the shared default validator
   */
  public static IbanValidator standard() {
    return STANDARD;
  }

  /**
   * Returns a validator that applies national check digit rules as given by the mode. This
   * validator is not changed.
   *
   * @param mode whether to apply national check digit rules, {@link NationalCheckMode#OFF} by default
   * @return a new validator with the given mode and the other options of this one
   * @throws NullPointerException if {@code mode} is {@code null}
   */
  public IbanValidator nationalCheck(NationalCheckMode mode) {
    return new IbanValidator(Objects.requireNonNull(mode, "mode"), format, rules);
  }

  /**
   * Returns a validator that uses an own national check digit rule for the country of the rule.
   * The rule replaces the built-in rule of {@link NationalChecks} for that country, or adds one
   * for a country without a built-in rule; a later rule for the same country replaces an earlier
   * one. The rule only applies if national checks are switched on with
   * {@link #nationalCheck(NationalCheckMode)}. This validator is not changed. The rule is retained
   * by reference and is not used by {@link IbanBuilder#nationalCheckDigits(boolean)}.
   *
   * <pre>{@code
   * IbanValidator validator = IbanValidator.standard()
   *                                        .nationalCheck(NationalCheckMode.REQUIRED)
   *                                        .nationalRule(new MyDutchElevenTest());
   * }</pre>
   *
   * @param rule the rule; it must be immutable and thread-safe, and for GWT it must be translatable
   * @return a new validator with the rule and the other options of this one
   * @throws NullPointerException     if {@code rule} is {@code null}
   * @throws IllegalArgumentException if the country of the rule does not use IBAN, or the check
   *                                  range of the rule is outside its BBAN
   */
  public IbanValidator nationalRule(NationalCheck rule) {
    Objects.requireNonNull(rule, "rule");
    String countryCode = rule.countryCode();
    int bbanLength = IbanRegistry.find(countryCode)
                                 .orElseThrow(() -> new IllegalArgumentException("country " + countryCode + " of rule " + rule.id() + " does not use IBAN"))
                                 .bbanLength();
    if (rule.checkRange()
            .end() > bbanLength) {
      throw new IllegalArgumentException("check range " + rule.checkRange() + " of rule " + rule.id() + " exceeds the BBAN length of " + bbanLength);
    }
    Map<String, NationalCheck> copy = new TreeMap<>(rules);
    copy.put(countryCode, rule);
    return new IbanValidator(nationalCheck, format, Collections.unmodifiableMap(copy));
  }

  /**
   * Returns the national check digit rule this validator applies to a country: an own rule added
   * with {@link #nationalRule(NationalCheck)}, otherwise the built-in rule of
   * {@link NationalChecks}.
   *
   * <p>
   * The lookup is case sensitive and does not depend on {@link #nationalCheckMode()}.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code, upper case; must not be {@code null}
   * @return the rule, empty if there is none for the country
   * @throws NullPointerException if {@code countryCode} is {@code null} and this validator has
   *                              custom rules; use a non-null code for consistent lookup behavior
   */
  public Optional<NationalCheck> findNationalRule(String countryCode) {
    NationalCheck own = rules.get(countryCode);
    return own != null ? Optional.of(own) : NationalChecks.find(countryCode);
  }

  /**
   * Returns a validator that requires the input in exactly the given format, see
   * {@link Iban#validate(CharSequence, IbanFormat)}. This validator is not changed.
   *
   * @param format the format the input must have exactly, or {@code null} (the default) to accept
   *               electronic and print format, whitespace and lower case
   * @return a new validator with the given format and the other options of this one
   */
  public IbanValidator format(IbanFormat format) {
    return new IbanValidator(nationalCheck, format, rules);
  }

  /**
   * Returns how this validator applies national check digit rules.
   *
   * @return the national check mode, never {@code null}
   */
  public NationalCheckMode nationalCheckMode() {
    return nationalCheck;
  }

  /**
   * Returns the format the input must have exactly.
   *
   * @return the required format, empty if electronic and print format are accepted
   */
  public Optional<IbanFormat> requiredFormat() {
    return Optional.ofNullable(format);
  }

  /**
   * Validates an IBAN with the options of this validator. The international checks run first, as
   * in {@link Iban#validate(CharSequence)}; the national check only runs for an otherwise valid
   * IBAN, and its outcome is available from {@link ValidationResult#nationalCheck()}. This method
   * reports invalid input, including {@code null}, in the result. Exceptions thrown by a custom
   * {@link NationalCheck} propagate to the caller. With {@link NationalCheckMode#IF_SUPPORTED},
   * a missing or inapplicable rule leaves an internationally valid IBAN valid; with
   * {@link NationalCheckMode#REQUIRED}, it produces {@link IbanError#NATIONAL_CHECK_NOT_SUPPORTED}.
   *
   * @param input the IBAN, may be {@code null}
   * @return the validation result; if valid, its value is the parsed IBAN
   */
  public ValidationResult<Iban, IbanError> validate(CharSequence input) {
    ValidationResult<Iban, IbanError> result = format == null ? Iban.validate(input) : Iban.validate(input, format);
    if (!result.isValid() || nationalCheck == NationalCheckMode.OFF) {
      return result;
    }
    Iban                    iban = result.value()
                                         .orElseThrow();
    Optional<NationalCheck> rule = findNationalRule(iban.countryCode());
    if (rule.isEmpty()) {
      return notSupported(result, "no national check for country " + iban.countryCode(), null);
    }
    NationalCheck        check   = rule.get();
    NationalCheckOutcome outcome = check.check(iban.bban());
    if (outcome == NationalCheckOutcome.VALID) {
      return result.withNationalCheck(NationalCheckStatus.PASSED);
    }
    if (outcome == NationalCheckOutcome.NOT_APPLICABLE) {
      return notSupported(result, "national check " + check.id() + " cannot check this BBAN of " + iban.countryCode(), check.id());
    }
    BbanRange range = check.checkRange();
    int       first = range.start() + 4;
    int       last  = range.end() + 4;
    return Iban.invalid(IbanError.INVALID_NATIONAL_CHECK_DIGITS,
                        "national check digits of " + iban.countryCode() + " are not valid (rule " + check.id() + ", positions " + first + "-" + last + ")")
               .withRule(check.id(), first, last)
               .withNationalCheck(NationalCheckStatus.FAILED);
  }

  private ValidationResult<Iban, IbanError> notSupported(ValidationResult<Iban, IbanError> valid,
                                                         String message,
                                                         String rule) {
    if (nationalCheck == NationalCheckMode.IF_SUPPORTED) {
      return valid.withNationalCheck(NationalCheckStatus.NOT_SUPPORTED);
    }
    ValidationResult<Iban, IbanError> result = Iban.invalid(IbanError.NATIONAL_CHECK_NOT_SUPPORTED, message)
                                                   .withNationalCheck(NationalCheckStatus.NOT_SUPPORTED);
    return rule == null ? result : result.withRule(rule, 5, valid.value()
                                                                 .orElseThrow()
                                                                 .toString()
                                                                 .length());
  }

  /**
   * Parses an IBAN with the options of this validator.
   *
   * @param input the IBAN, may be {@code null}
   * @return the IBAN
   * @throws InvalidIbanException if the input is not valid for this validator;
   *                              {@link InvalidIbanException#result()} carries the error details
   * @see #validate(CharSequence)
   */
  public Iban parse(CharSequence input) {
    ValidationResult<Iban, IbanError> result = validate(input);
    if (result.isValid()) {
      return result.value()
                   .orElseThrow();
    }
    throw new InvalidIbanException(result);
  }

  /**
   * Parses an IBAN like {@link #parse(CharSequence)}, but returns an empty optional instead of
   * throwing an exception.
   *
   * @param input the IBAN, may be {@code null}
   * @return the IBAN, empty if the input is not valid for this validator
   */
  public Optional<Iban> tryParse(CharSequence input) {
    return validate(input).value();
  }

  /**
   * Checks whether the input is valid for this validator, using the same rules as
   * {@link #validate(CharSequence)}.
   *
   * @param input the IBAN, may be {@code null}
   * @return {@code true} if the input is valid for this validator
   */
  public boolean isValid(CharSequence input) {
    return validate(input).isValid();
  }

  /**
   * Returns a diagnostic description of the mode, required format and custom rule countries.
   *
   * @return the validator configuration
   */
  @Override
  public String toString() {
    return "IbanValidator[nationalCheck=" + nationalCheck + ", format=" + format + (rules.isEmpty() ? "" : ", rules=" + rules.keySet()) + "]";
  }
}

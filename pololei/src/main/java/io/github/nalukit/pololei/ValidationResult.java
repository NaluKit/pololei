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
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.registry.CharacterType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Outcome of validating an IBAN, BIC, BBAN or BBAN field: either the parsed or normalized value,
 * or the reason why the input is not valid.
 * <p>
 * Besides the {@link #error() error} and an English {@link #message() message}, an invalid result
 * carries the details needed to build a localized message or to highlight the input:
 * {@link #position()}, {@link #expectedLength()}, {@link #actualLength()} and
 * {@link #expectedType()}, and for national checks {@link #rule()} and {@link #lastPosition()}.
 * Which details are present depends on the error.
 * <p>
 * Results are immutable. The value types returned by pololei are immutable and thread-safe,
 * so these results can be cached or shared between threads.
 *
 * @param <T> the parsed value, for example {@link Iban}, {@link Bic} or a normalized BBAN string
 * @param <E> the error type, {@link IbanError} or {@link BicError}
 */
public final class ValidationResult<T, E extends Enum<E>>
    implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Marks an absent position or length. */
  static final int NONE = -1;

  // final, so that a result is safely published to other threads even without synchronization;
  // GWT-RPC uses ValidationResult_CustomFieldSerializer from pololei-gwt
  @SuppressWarnings("serial") // T is Iban or Bic, both serializable
  private final T                   value;
  private final E                   error;
  private final String              message;
  private final int                 position;
  private final int                 expectedLength;
  private final int                 actualLength;
  private final CharacterType       expectedType;
  private final int                 lastPosition;
  private final String              rule;
  private final NationalCheckStatus nationalCheck;

  private ValidationResult(T value,
                           E error,
                           String message,
                           int position,
                           int expectedLength,
                           int actualLength,
                           CharacterType expectedType,
                           int lastPosition,
                           String rule,
                           NationalCheckStatus nationalCheck) {
    this.value          = value;
    this.error          = error;
    this.message        = message;
    this.position       = position;
    this.expectedLength = expectedLength;
    this.actualLength   = actualLength;
    this.expectedType   = expectedType;
    this.lastPosition   = lastPosition;
    this.rule           = rule;
    this.nationalCheck  = nationalCheck;
  }

  static <T, E extends Enum<E>> ValidationResult<T, E> valid(T value) {
    return new ValidationResult<>(Objects.requireNonNull(value), null, null, NONE, NONE, NONE, null, NONE, null, NationalCheckStatus.NOT_PERFORMED);
  }

  static <T, E extends Enum<E>> ValidationResult<T, E> invalid(E error,
                                                               String message) {
    return new ValidationResult<>(null, Objects.requireNonNull(error), Objects.requireNonNull(message), NONE, NONE, NONE, null, NONE, null, NationalCheckStatus.NOT_PERFORMED);
  }

  /**
   * Recreates a result from its parts; used by the GWT-RPC serializer of pololei-gwt. Absent
   * positions and lengths are {@link #NONE}.
   */
  static <T, E extends Enum<E>> ValidationResult<T, E> restore(T value,
                                                               E error,
                                                               String message,
                                                               int position,
                                                               int expectedLength,
                                                               int actualLength,
                                                               CharacterType expectedType,
                                                               int lastPosition,
                                                               String rule,
                                                               NationalCheckStatus nationalCheck) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns a copy with the 1-based position of the offending character. */
  ValidationResult<T, E> atPosition(int position) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns a copy with the expected and the actual length. */
  ValidationResult<T, E> withLength(int expectedLength,
                                    int actualLength) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns a copy with the character type expected at {@link #position()}. */
  ValidationResult<T, E> expecting(CharacterType expectedType) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns an invalid result with the same details for another value type. */
  <U> ValidationResult<U, E> retype() {
    if (isValid()) {
      throw new IllegalStateException("only an invalid result can be retyped");
    }
    return new ValidationResult<>(null, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns a copy with the national check status. */
  ValidationResult<T, E> withNationalCheck(NationalCheckStatus nationalCheck) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /** Returns a copy with the failed rule and the 1-based range of the affected characters. */
  ValidationResult<T, E> withRule(String rule,
                                  int position,
                                  int lastPosition) {
    return new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
  }

  /**
   * Checks whether the input is valid. A valid result has a {@link #value()}, an invalid one an
   * {@link #error()} and a {@link #message()}.
   *
   * @return {@code true} if the input is valid
   */
  public boolean isValid() {
    return error == null;
  }

  /**
   * Returns the value parsed from the input.
   *
   * @return the parsed value, empty if the input is not valid
   */
  public Optional<T> value() {
    return Optional.ofNullable(value);
  }

  /**
   * Returns the reason why the input is not valid; use it as key for localized messages.
   *
   * @return the error, empty if the input is valid
   */
  public Optional<E> error() {
    return Optional.ofNullable(error);
  }

  /**
   * Returns an English description of the error for diagnostics. Use {@link #error()} and the
   * structured details for application logic and localization; the message text is not a stable
   * API. Messages may include a character, country code, check digits or a custom rule identifier.
   *
   * @return a human readable (English) description of the error, empty if the input is valid
   */
  public Optional<String> message() {
    return Optional.ofNullable(message);
  }

  /**
   * Returns the 1-based position of the offending character or the start of an affected range.
   * Positions count normalized characters: IBAN validation uses the complete IBAN, BBAN validation
   * uses the BBAN, and field validation uses the field. Thus IBAN position 5 is BBAN position 1.
   * Builder errors use IBAN positions when available. If {@link #lastPosition()} is present,
   * both endpoints of the range are inclusive.
   *
   * @return the first affected position, empty if the error has no position
   */
  public OptionalInt position() {
    return position == NONE ? OptionalInt.empty() : OptionalInt.of(position);
  }

  /**
   * Returns the length the input, or the part named in the message, must have. Whitespace is not
   * counted.
   *
   * @return the expected length, present for length errors
   * @see #actualLength()
   */
  public OptionalInt expectedLength() {
    return expectedLength == NONE ? OptionalInt.empty() : OptionalInt.of(expectedLength);
  }

  /**
   * Returns the length the input, or the part named in the message, has. Whitespace is not
   * counted.
   *
   * @return the actual length, present for length errors
   * @see #expectedLength()
   */
  public OptionalInt actualLength() {
    return actualLength == NONE ? OptionalInt.empty() : OptionalInt.of(actualLength);
  }

  /**
   * Returns the character type expected at {@link #position()}, for example to tell the user
   * that a digit is required there.
   *
   * @return the expected character type, present for character and structure errors
   */
  public Optional<CharacterType> expectedType() {
    return Optional.ofNullable(expectedType);
  }

  /**
   * Returns the 1-based position of the last character the error refers to, counted like
   * {@link #position()}. Set when an error covers several characters, for example national check
   * digits.
   *
   * @return the last position, if the error refers to a range of characters
   */
  public OptionalInt lastPosition() {
    return lastPosition == NONE ? OptionalInt.empty() : OptionalInt.of(lastPosition);
  }

  /**
   * Returns the national rule that failed or could not be applied, see
   * {@link io.github.nalukit.pololei.national.NationalCheck#id()}.
   *
   * @return identifier of the rule, for example {@code FR_RIB_KEY}; present for national check
   *         errors when a rule exists for the country
   */
  public Optional<String> rule() {
    return Optional.ofNullable(rule);
  }

  /**
   * Returns what the national check found. It is also set on valid results, so
   * {@link NationalCheckStatus#NOT_SUPPORTED} tells that an IBAN validated with
   * {@link NationalCheckMode#IF_SUPPORTED} was not checked nationally.
   *
   * @return the outcome of the national check, never {@code null};
   *         {@link NationalCheckStatus#NOT_PERFORMED} unless the IBAN was validated by an
   *         {@link IbanValidator} with national checks
   */
  public NationalCheckStatus nationalCheck() {
    return nationalCheck == null ? NationalCheckStatus.NOT_PERFORMED : nationalCheck;
  }

  /**
   * Returns a diagnostic description: {@code valid: <value>} or
   * {@code invalid (<error>): <message>}. A valid result includes the complete value, including
   * account data for IBAN and BBAN results.
   *
   * @return a description of the result
   */
  @Override
  public String toString() {
    return isValid() ? "valid: " + value : "invalid (" + error + "): " + message;
  }
}

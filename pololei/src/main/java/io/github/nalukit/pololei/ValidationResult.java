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
  /** The validated value, {@code null} if invalid. */
  @SuppressWarnings("serial") // T is Iban, Bic or String, all serializable
  private final T                   value;
  /** The error, {@code null} if valid. */
  private final E                   error;
  /** The message, {@code null} if valid. */
  private final String              message;
  /** 1-based position of the error in what was validated, or -1. */
  private final int                 position;
  /** Expected length, or -1. */
  private final int                 expectedLength;
  /** Actual length, or -1. */
  private final int                 actualLength;
  /** Expected character type, or {@code null}. */
  private final CharacterType       expectedType;
  /** Last position of the affected range, or -1. */
  private final int                 lastPosition;
  /** Id of the national rule, or {@code null}. */
  private final String              rule;
  /** The national check status, {@code null} if not performed. */
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

  /**
   * Creates an invalid result. {@link IbanError#INVALID_NATIONAL_CHECK_DIGITS} always comes with
   * {@link NationalCheckStatus#FAILED}, whether the validator found wrong check digits or the
   * builder found no valid ones; every other error starts with
   * {@link NationalCheckStatus#NOT_PERFORMED}. This keeps every result created by pololei
   * consistent with {@link #checkConsistency()}.
   */
  static <T, E extends Enum<E>> ValidationResult<T, E> invalid(E error,
                                                               String message) {
    NationalCheckStatus nationalCheck = Objects.requireNonNull(error) == IbanError.INVALID_NATIONAL_CHECK_DIGITS ? NationalCheckStatus.FAILED : NationalCheckStatus.NOT_PERFORMED;
    return new ValidationResult<>(null, error, Objects.requireNonNull(message), NONE, NONE, NONE, null, NONE, null, nationalCheck);
  }

  /**
   * Recreates a result from its parts; used by the GWT-RPC serializer of pololei-gwt. Absent
   * positions and lengths are {@link #NONE}.
   *
   * @throws IllegalArgumentException if the parts do not form a valid or an invalid result
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
    ValidationResult<T, E> result = new ValidationResult<>(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
    result.checkConsistency();
    return result;
  }

  /**
   * Rejects a result whose fields contradict each other, for example one that is valid and has no
   * value, or has a value and an error. Used for results created from outside, that is by the
   * Java deserialization and the GWT-RPC serializer.
   *
   * @throws IllegalArgumentException if the fields do not form a valid or an invalid result
   */
  private void checkConsistency() {
    if ((value == null) == (error == null)) {
      throw new IllegalArgumentException("invalid ValidationResult: either a value or an error is required");
    }
    if (error == null) {
      if (message != null || position != NONE || expectedLength != NONE || actualLength != NONE || expectedType != null || lastPosition != NONE || rule != null) {
        throw new IllegalArgumentException("invalid ValidationResult: a valid result has no error details");
      }
      if (!(value instanceof Iban || value instanceof Bic || value instanceof String)) {
        throw new IllegalArgumentException("invalid ValidationResult: unsupported value type");
      }
      if (nationalCheck == NationalCheckStatus.FAILED) {
        throw new IllegalArgumentException("invalid ValidationResult: a valid result has no failed national check");
      }
    } else {
      if (!(error instanceof IbanError || error instanceof BicError)) {
        throw new IllegalArgumentException("invalid ValidationResult: unsupported error type");
      }
      if (message == null || message.isEmpty()) {
        throw new IllegalArgumentException("invalid ValidationResult: an error needs a message");
      }
      if (nationalCheck == NationalCheckStatus.PASSED) {
        throw new IllegalArgumentException("invalid ValidationResult: an error has no passed national check");
      }
      if ((nationalCheck == NationalCheckStatus.FAILED) != (error == IbanError.INVALID_NATIONAL_CHECK_DIGITS)) {
        throw new IllegalArgumentException("invalid ValidationResult: national check status does not match the error");
      }
    }
    if (nationalCheck == null) {
      throw new IllegalArgumentException("invalid ValidationResult: national check status is missing");
    }
    if (position != NONE && position < 1 || lastPosition != NONE && lastPosition < 1) {
      throw new IllegalArgumentException("invalid ValidationResult: positions are 1-based");
    }
    if (lastPosition != NONE && (position == NONE || lastPosition < position)) {
      throw new IllegalArgumentException("invalid ValidationResult: invalid position range");
    }
    if (expectedLength < NONE || actualLength < NONE) {
      throw new IllegalArgumentException("invalid ValidationResult: lengths must not be negative");
    }
    if (rule != null && (rule.isEmpty() || error == null)) {
      throw new IllegalArgumentException("invalid ValidationResult: invalid rule");
    }
  }

  /**
   * Rejects inconsistent results read from a Java serialization stream.
   *
   * @return this result, if its fields are consistent
   * @throws IllegalArgumentException if the fields do not form a valid or an invalid result
   */
  @Serial
  private Object readResolve() {
    checkConsistency();
    return this;
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

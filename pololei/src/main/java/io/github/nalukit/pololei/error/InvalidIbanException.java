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
package io.github.nalukit.pololei.error;

import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.ValidationResult;

import java.io.Serial;

/**
 * Thrown when an input is not a valid IBAN. The underlying {@link ValidationResult} carries
 * the error and structured details; its English diagnostic message becomes the exception message.
 */
public class InvalidIbanException
    extends IllegalArgumentException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final ValidationResult<Iban, IbanError> result;

  /**
   * Creates the exception for an invalid validation result; the exception message is the
   * {@link ValidationResult#message() message} of the result. Usually thrown by the parse
   * methods, but can be used to turn a result of {@link Iban#validate(CharSequence)} into an
   * exception.
   *
   * @param result an invalid validation result
   * @throws IllegalArgumentException if the result is valid
   * @throws NullPointerException     if {@code result} is {@code null}
   */
  public InvalidIbanException(ValidationResult<Iban, IbanError> result) {
    super(requireInvalid(result).message()
                .orElse("invalid IBAN"));
    this.result = result;
  }

  private static ValidationResult<Iban, IbanError> requireInvalid(ValidationResult<Iban, IbanError> result) {
    if (result.isValid()) {
      throw new IllegalArgumentException("result is valid");
    }
    return result;
  }

  /**
   * Returns the reason why the input is not valid; the same as {@code result().error().orElseThrow()}.
   *
   * @return the error, never {@code null}
   */
  public IbanError error() {
    return result.error()
                 .orElseThrow();
  }

  /**
   * Returns the validation result that caused this exception.
   *
   * @return the invalid result with all error details (position, lengths, expected type)
   */
  public ValidationResult<Iban, IbanError> result() {
    return result;
  }
}

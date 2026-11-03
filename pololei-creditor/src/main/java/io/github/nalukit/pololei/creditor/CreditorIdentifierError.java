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

/**
 * Why an input is not a valid SEPA Creditor Identifier.
 */
public enum CreditorIdentifierError {

  /** The input is {@code null} or contains only whitespace. */
  EMPTY,
  /** The input contains a character other than a letter or a digit (whitespace is ignored). */
  INVALID_CHARACTER,
  /**
   * The identifier is shorter than 8 or longer than 35 characters, or does not have the length
   * of its country (for Germany 18).
   */
  INVALID_LENGTH,
  /** Characters 1 and 2 are not two letters. */
  INVALID_COUNTRY_CODE,
  /** The country does not take part in SEPA. */
  UNSUPPORTED_COUNTRY,
  /** Characters 3 and 4 are not digits, or not the check digits of the identifier. */
  INVALID_CHECK_DIGITS
}

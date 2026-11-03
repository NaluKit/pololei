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

import io.github.nalukit.pololei.IbanFormat;

/**
 * Reasons why an input is not a valid IBAN, in the order they are checked.
 */
public enum IbanError {

  /** The input is {@code null} or contains only whitespace. */
  EMPTY,
  /** The input does not follow the requested {@link IbanFormat} (spaces, grouping, case). */
  INVALID_FORMAT,
  /** The input contains a character other than A-Z and 0-9. */
  INVALID_CHARACTER,
  /** The input does not start with two letters. */
  INVALID_COUNTRY_CODE,
  /** The country does not use IBAN according to the registry. */
  UNSUPPORTED_COUNTRY,
  /** The length does not match the length defined for the country. */
  INVALID_LENGTH,
  /** The BBAN does not match the structure defined for the country. */
  INVALID_BBAN,
  /** The check digits are not digits, are 00, 01 or 99, or the mod 97 check fails. */
  INVALID_CHECK_DIGITS,
  /**
   * The national check digits inside the BBAN are wrong. Only reported when national checks are
   * enabled, see {@code IbanValidator}.
   */
  INVALID_NATIONAL_CHECK_DIGITS,
  /**
   * National checks are required, but pololei has no rule for the country or the rule cannot judge
   * the BBAN (for example letters where the algorithm needs digits).
   */
  NATIONAL_CHECK_NOT_SUPPORTED,
  /** The BBAN of the country has no such field; reported by field validation and the builder. */
  UNDEFINED_FIELD
}

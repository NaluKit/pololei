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

/**
 * Reasons why an input is not a valid BIC (ISO 9362), in the order they are checked.
 */
public enum BicError {

  /** The input is {@code null} or contains only whitespace. */
  EMPTY,
  /** The BIC does not have 8 or 11 characters. */
  INVALID_LENGTH,
  /** The input contains a character that is not allowed at its position. */
  INVALID_CHARACTER,
  /** Characters 5 and 6 are not an ISO 3166-1 alpha-2 country code. */
  UNKNOWN_COUNTRY
}

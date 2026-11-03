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

/**
 * What the national check of a {@link ValidationResult} found.
 */
public enum NationalCheckStatus {

  /** No national check was made: national checks are off, the input failed earlier, or it is a BIC. */
  NOT_PERFORMED,
  /** The national check digits are correct. */
  PASSED,
  /** The national check digits are wrong ({@code IbanError.INVALID_NATIONAL_CHECK_DIGITS}). */
  FAILED,
  /** pololei has no rule for the country, or the rule cannot judge this BBAN. */
  NOT_SUPPORTED
}

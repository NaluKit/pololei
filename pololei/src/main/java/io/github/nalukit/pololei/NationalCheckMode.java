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
 * Whether and how {@link IbanValidator} applies national check digit rules.
 */
public enum NationalCheckMode {

  /** Only the international checks (structure and IBAN check digits); the default. */
  OFF,
  /**
   * Also check national check digits where pololei has a rule for the country; IBANs of other
   * countries are valid with {@link NationalCheckStatus#NOT_SUPPORTED}.
   */
  IF_SUPPORTED,
  /**
   * Also check national check digits; IBANs of countries without a rule are invalid with
   * {@code IbanError.NATIONAL_CHECK_NOT_SUPPORTED}.
   */
  REQUIRED
}

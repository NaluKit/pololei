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
package io.github.nalukit.pololei.national;

import io.github.nalukit.pololei.registry.BbanRange;

import java.util.Optional;

/**
 * A national check digit algorithm of one country, applied to a BBAN that already matches the
 * structure of the SWIFT IBAN registry.
 * <p>
 * Use {@link NationalChecks#find(String)} to get the rule of a country. Algorithms and sources are
 * documented in {@code docs/national-checks.md}.
 * <p>
 * The rules of pololei are stateless and thread-safe.
 */
public interface NationalCheck {

  /**
   * Returns the identifier of the rule, as reported by
   * {@link io.github.nalukit.pololei.ValidationResult#rule()}.
   *
   * @return stable identifier of the rule, for example {@code FR_RIB_KEY}
   */
  String id();

  /**
   * Returns the country the rule belongs to.
   *
   * @return ISO 3166-1 alpha-2 country code, upper case
   */
  String countryCode();

  /**
   * Returns a short description of the algorithm, for documentation and logs.
   *
   * @return short English description of the algorithm
   */
  String description();

  /**
   * Returns the BBAN positions the rule reports in errors: the national check digits or, for some
   * rules, all positions they protect. Add 4 to get IBAN positions.
   *
   * @return the range, 1-based and inclusive
   */
  BbanRange checkRange();

  /**
   * Checks the national check digits of a BBAN.
   *
   * <pre>{@code
   * NationalChecks.find("FR")
   *               .orElseThrow()
   *               .check("20041010050500013M02606");   // VALID
   * }</pre>
   *
   * Input must already be normalized and structurally valid; implementations need not validate
   * those preconditions. Custom implementations must return a non-null outcome.
   *
   * @param bban a non-null BBAN of the rule's country that matches the registry structure, upper case
   * @return the outcome of the check; {@link NationalCheckOutcome#NOT_APPLICABLE} if the rule
   *         cannot judge the BBAN, for example letters where the algorithm needs digits
   */
  NationalCheckOutcome check(String bban);

  /**
   * Calculates the national check digits of a BBAN and returns it with the check digits in place.
   * {@link io.github.nalukit.pololei.IbanBuilder#nationalCheckDigits(boolean)} uses this method.
   *
   * Input must already be normalized and structurally valid. Only check digit positions are
   * replaced; all other characters are preserved. Custom implementations must return a non-null
   * optional.
   *
   * @param bban a non-null BBAN of the rule's country that matches the registry structure; the current
   *             check digits are ignored
   * @return the BBAN with valid national check digits, empty if no check digits make it valid
   *         (for example a Norwegian account whose mod 11 remainder is 1) or the rule cannot
   *         judge the BBAN
   */
  Optional<String> complete(String bban);

  /**
   * Tells whether the rule needs digits only. For such a rule a BBAN with a letter at an
   * alphanumeric position is {@link NationalCheckOutcome#NOT_APPLICABLE}.
   *
   * @return {@code true} if the rule can only check BBANs whose alphanumeric positions contain digits
   */
  boolean requiresDigits();
}

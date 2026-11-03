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

import io.github.nalukit.pololei.internal.Text;
import io.github.nalukit.pololei.registry.BbanRange;

import java.util.Optional;

/**
 * A national check whose check digits sit at fixed BBAN positions and can be calculated from the
 * other characters.
 */
final class CheckDigitRule
    implements NationalCheck {

  /** Calculates the check digits for the positions of the rule. */
  interface Calculator {

    /**
     * @param bban the BBAN; the characters at the check positions must be ignored
     * @return the check digits in the order of the check positions, {@code null} if none exist
     */
    String calculate(String bban);
  }

  private final String     id;
  private final String     countryCode;
  private final String     description;
  private final BbanRange  checkRange;
  private final int[]      checkPositions;
  private final boolean    requiresDigits;
  private final Calculator calculator;

  CheckDigitRule(String id,
                 String countryCode,
                 String description,
                 BbanRange checkRange,
                 int[] checkPositions,
                 boolean requiresDigits,
                 Calculator calculator) {
    this.id             = id;
    this.countryCode    = countryCode;
    this.description    = description;
    this.checkRange     = checkRange;
    this.checkPositions = checkPositions;
    this.requiresDigits = requiresDigits;
    this.calculator     = calculator;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String countryCode() {
    return countryCode;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public BbanRange checkRange() {
    return checkRange;
  }

  @Override
  public boolean requiresDigits() {
    return requiresDigits;
  }

  @Override
  public NationalCheckOutcome check(String bban) {
    if (!applicable(bban)) {
      return NationalCheckOutcome.NOT_APPLICABLE;
    }
    String expected = calculator.calculate(bban);
    if (expected == null) {
      return NationalCheckOutcome.INVALID;
    }
    for (int i = 0; i < checkPositions.length; i++) {
      if (bban.charAt(checkPositions[i]) != expected.charAt(i)) {
        return NationalCheckOutcome.INVALID;
      }
    }
    return NationalCheckOutcome.VALID;
  }

  @Override
  public Optional<String> complete(String bban) {
    if (!applicable(bban)) {
      return Optional.empty();
    }
    String expected = calculator.calculate(bban);
    if (expected == null) {
      return Optional.empty();
    }
    char[] chars = bban.toCharArray();
    for (int i = 0; i < checkPositions.length; i++) {
      chars[checkPositions[i]] = expected.charAt(i);
    }
    return Optional.of(new String(chars));
  }

  private boolean applicable(String bban) {
    if (bban == null || bban.length() <= checkPositions[checkPositions.length - 1]) {
      return false;
    }
    if (requiresDigits) {
      for (int i = 0; i < bban.length(); i++) {
        if (!Text.isDigit(bban.charAt(i))) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return id;
  }
}

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
package io.github.nalukit.pololei.registry;

import java.util.Objects;
import java.util.Optional;

/**
 * IBAN definition of one country as published in the SWIFT IBAN registry. Instances are immutable
 * and thread-safe.
 */
public final class IbanCountry {

  private final String        countryCode;
  private final String        countryName;
  private final BbanStructure bbanStructure;
  private final BbanRange     bankIdentifier;
  private final BbanRange     branchIdentifier;
  private final boolean       sepa;
  private final String        ibanExample;
  private final BbanLayout    bbanLayout;

  /**
   * Creates a country definition; only used by the generated registry data.
   *
   * @param countryCode      ISO 3166-1 alpha-2 country code used as IBAN prefix
   * @param countryName      English country name
   * @param bbanStructure    BBAN structure in registry notation, for example {@code 8!n10!n}
   * @param bankIdentifier   position of the bank identifier, for example {@code 1-8}, or {@code null}
   * @param branchIdentifier position of the branch identifier, or {@code null}
   * @param sepa             {@code true} if the country is part of the SEPA scheme
   * @param ibanExample      example IBAN in electronic format
   */
  IbanCountry(String countryCode,
                     String countryName,
                     String bbanStructure,
                     String bankIdentifier,
                     String branchIdentifier,
                     boolean sepa,
                     String ibanExample) {
    this.countryCode      = Objects.requireNonNull(countryCode, "countryCode");
    this.countryName      = Objects.requireNonNull(countryName, "countryName");
    this.bbanStructure    = BbanStructure.parse(bbanStructure);
    this.bankIdentifier   = parseRange(bankIdentifier);
    this.branchIdentifier = parseRange(branchIdentifier);
    this.sepa             = sepa;
    this.ibanExample      = Objects.requireNonNull(ibanExample, "ibanExample");
    if (countryCode.length() != 2) {
      throw new IllegalArgumentException("country code must have two letters: " + countryCode);
    }
    checkRange(this.bankIdentifier);
    checkRange(this.branchIdentifier);
    this.bbanLayout = BbanLayoutData.layout(countryCode, this.bbanStructure.length(), this.bankIdentifier, this.branchIdentifier);
  }

  private static BbanRange parseRange(String range) {
    if (range == null || range.isEmpty()) {
      return null;
    }
    int dash = range.indexOf('-');
    if (dash < 0) {
      throw new IllegalArgumentException("invalid BBAN range: " + range);
    }
    return new BbanRange(Integer.parseInt(range.substring(0, dash)
                                               .trim()),
                         Integer.parseInt(range.substring(dash + 1)
                                               .trim()));
  }

  private void checkRange(BbanRange range) {
    if (range != null && range.end() > bbanStructure.length()) {
      throw new IllegalArgumentException("range " + range + " exceeds BBAN length of " + countryCode);
    }
  }

  /**
   * Returns the country code, the first two characters of every IBAN of the country.
   *
   * @return ISO 3166-1 alpha-2 country code used as IBAN prefix, for example {@code DE}
   */
  public String countryCode() {
    return countryCode;
  }

  /**
   * Returns the name of the country as published in the registry.
   *
   * @return English country name, for example {@code Germany}
   */
  public String countryName() {
    return countryName;
  }

  /**
   * Returns the structure every BBAN of the country must match.
   *
   * @return the BBAN structure, for example {@code 8!n10!n} for Germany
   */
  public BbanStructure bbanStructure() {
    return bbanStructure;
  }

  /**
   * Returns the length of every BBAN of the country.
   *
   * @return length of the BBAN, the same as {@code bbanStructure().length()}
   */
  public int bbanLength() {
    return bbanStructure.length();
  }

  /**
   * Returns the number of BBAN positions outside the bank and branch identifier of the registry.
   *
   * @return number of BBAN characters outside the bank and branch identifier, the length of
   *         {@code Iban.accountNumber()}; see {@link #bbanLayout()} for the national account number
   */
  public int accountNumberLength() {
    int length = 0;
    for (int i = 0; i < bbanStructure.length(); i++) {
      if (isAccountPosition(i)) {
        length++;
      }
    }
    return length;
  }

  /**
   * Returns the fields of the BBAN: bank and branch identifier as in the registry, and for some
   * countries national fields like check digits or account type.
   *
   * @return the BBAN fields of this country
   */
  public BbanLayout bbanLayout() {
    return bbanLayout;
  }

  /**
   * Returns the length of every IBAN of the country.
   *
   * @return length of the IBAN (country code, check digits and BBAN), the BBAN length plus 4
   */
  public int ibanLength() {
    return bbanStructure.length() + 4;
  }

  /**
   * Returns the position of the bank identifier as defined by the registry.
   *
   * @return position of the bank identifier within the BBAN, 1-based; empty if the registry
   *         defines none
   */
  public Optional<BbanRange> bankIdentifier() {
    return Optional.ofNullable(bankIdentifier);
  }

  /**
   * Returns the position of the branch identifier as defined by the registry.
   *
   * @return position of the branch identifier within the BBAN, 1-based; empty if the registry
   *         defines none
   */
  public Optional<BbanRange> branchIdentifier() {
    return Optional.ofNullable(branchIdentifier);
  }

  /**
   * Checks whether the country is part of the SEPA scheme, as published in the registry.
   *
   * @return {@code true} if the country is part of the SEPA scheme
   */
  public boolean isSepa() {
    return sepa;
  }

  /**
   * Returns the example IBAN the registry publishes for the country.
   *
   * @return example IBAN in electronic format, for example {@code DE89370400440532013000}
   */
  public String ibanExample() {
    return ibanExample;
  }

  /**
   * Checks whether a BBAN position is part of {@code Iban.accountNumber()}, that is, outside the
   * bank and branch identifier of the registry.
   *
   * No bounds check is performed: an index outside the BBAN also returns {@code true}, because
   * it belongs to neither identifier range. Callers must supply an index within the BBAN.
   *
   * @param index 0-based position within the BBAN
   * @return {@code true} if the position belongs neither to the bank nor to the branch identifier
   */
  public boolean isAccountPosition(int index) {
    return !(bankIdentifier != null && bankIdentifier.contains(index)) && !(branchIdentifier != null && branchIdentifier.contains(index));
  }

  /**
   * Two country definitions are equal if they have the same country code.
   *
   * @param o the object to compare with
   * @return {@code true} if {@code o} is a country definition with the same country code
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof IbanCountry other && countryCode.equals(other.countryCode);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this value
   */
  @Override
  public int hashCode() {
    return countryCode.hashCode();
  }

  /**
   * Returns the country code, English name and BBAN structure in registry notation.
   *
   * @return the country description
   */
  @Override
  public String toString() {
    return countryCode + " (" + countryName + "): " + bbanStructure;
  }
}

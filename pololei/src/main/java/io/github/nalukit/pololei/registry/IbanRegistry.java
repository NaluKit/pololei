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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup of the countries supporting IBAN, generated from the SWIFT IBAN registry.
 * <p>
 * The data lives in Java code (not in a resource file), so the registry works on the JVM and
 * in GWT alike. Use {@code scripts/registry/generate-registry.sh} to refresh it.
 * <p>
 * The registry is loaded once when the class is initialized and never changes afterwards; all
 * methods are thread-safe, and {@link IbanCountry} and its parts are immutable.
 */
public final class IbanRegistry {

  private static final List<IbanCountry>        COUNTRIES = IbanRegistryData.countries();
  private static final Map<String, IbanCountry> BY_CODE   = new HashMap<>();

  static {
    for (IbanCountry country : COUNTRIES) {
      BY_CODE.put(country.countryCode(), country);
    }
  }

  private IbanRegistry() {
  }

  /**
   * Looks up the IBAN definition of a country.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code, upper case (the lookup is case sensitive);
   *                    may be {@code null}
   * @return the country definition, empty if the country does not use IBAN
   */
  public static Optional<IbanCountry> find(String countryCode) {
    return Optional.ofNullable(countryCode == null ? null : BY_CODE.get(countryCode));
  }

  /**
   * Checks whether a country uses IBAN.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code, upper case (the lookup is case sensitive);
   *                    may be {@code null}
   * @return {@code true} if the country uses IBAN
   */
  public static boolean isSupported(String countryCode) {
    return find(countryCode).isPresent();
  }

  /**
   * Returns all countries that use IBAN.
   *
   * @return an unmodifiable list of all countries, ordered by country code
   */
  public static List<IbanCountry> countries() {
    return COUNTRIES;
  }

  /**
   * Returns the release of the SWIFT IBAN registry the data was generated from.
   *
   * @return the registry release, for example {@code SWIFT IBAN Registry Release 100}
   */
  public static String source() {
    return IbanRegistryData.SOURCE;
  }
}

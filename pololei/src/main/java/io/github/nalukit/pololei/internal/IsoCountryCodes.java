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
package io.github.nalukit.pololei.internal;

/**
 * ISO 3166-1 alpha-2 country codes, plus XK (Kosovo) which SWIFT uses for BIC and IBAN.
 * <p>
 * Kept as a constant because {@code Locale.getISOCountries()} is not available in GWT.
 * <p>
 * Internal to pololei, not part of the API: may change in any release.
 */
public final class IsoCountryCodes {

  private static final String CODES = "ADAEAFAGAIALAMAOAQARASATAUAWAXAZBABBBDBEBFBGBHBIBJBLBMBNBOBQBRBSBTBVBWBYBZCACCCDCFCGCHCICKCLCMCNCOCRCUCVCWCXCYCZ" +
                                      "DEDJDKDMDODZECEEEGEHERESETFIFJFKFMFOFRGAGBGDGEGFGGGHGIGLGMGNGPGQGRGSGTGUGWGYHKHMHNHRHTHUIDIEILIMINIOIQIRISIT" +
                                      "JEJMJOJPKEKGKHKIKMKNKPKRKWKYKZLALBLCLILKLRLSLTLULVLYMAMCMDMEMFMGMHMKMLMMMNMOMPMQMRMSMTMUMVMWMXMYMZNANCNENFNGNI" +
                                      "NLNONPNRNUNZOMPAPEPFPGPHPKPLPMPNPRPSPTPWPYQARERORSRURWSASBSCSDSESGSHSISJSKSLSMSNSOSRSSSTSVSXSYSZTCTDTFTGTHTJTK" +
                                      "TLTMTNTOTRTTTVTWTZUAUGUMUSUYUZVAVCVEVGVIVNVUWFWSXKYEYTZAZMZW";

  private IsoCountryCodes() {
  }

  /**
   * Looks up an exact upper case country code in the bundled list, including XK.
   *
   * @param code the country code, without normalization
   * @return {@code true} if the code is in the list; {@code false} for any length other than 2
   * @throws NullPointerException if {@code code} is {@code null}
   */
  public static boolean isKnown(String code) {
    if (code.length() != 2) {
      return false;
    }
    for (int i = 0; i < CODES.length(); i += 2) {
      if (CODES.charAt(i) == code.charAt(0) && CODES.charAt(i + 1) == code.charAt(1)) {
        return true;
      }
    }
    return false;
  }
}

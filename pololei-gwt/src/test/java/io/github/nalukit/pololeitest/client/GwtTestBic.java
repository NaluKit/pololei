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
package io.github.nalukit.pololeitest.client;

import com.google.gwt.junit.client.GWTTestCase;
import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.error.BicError;

/**
 * Runs the BIC API compiled to JavaScript.
 */
public class GwtTestBic
    extends GWTTestCase {

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  public void testOfComponents() {
    assertEquals("DEUTDEFF500", Bic.of("DEUT", "DE", "FF", "500").toString());
    assertEquals("DEUTDEFF", Bic.of("DEUT", "DE", "FF").toString());
    assertEquals("A1B2DEFF", Bic.of("A1B2", "DE", "FF").toString());
    try {
      Bic.of("DEU", "DE", "FF");
      fail();
    } catch (io.github.nalukit.pololei.error.InvalidBicException e) {
      assertEquals(BicError.INVALID_LENGTH, e.result().error().orElseThrow());
    }
    try {
      Bic.of("DEUT", "ZZ", "FF", "500");
      fail();
    } catch (io.github.nalukit.pololei.error.InvalidBicException e) {
      assertEquals(BicError.UNKNOWN_COUNTRY, e.result().error().orElseThrow());
    }
  }

  public void testRandom() {
    assertEquals(Bic.random(new java.util.Random(42)), Bic.random(new java.util.Random(42)));
    java.util.Random random = new java.util.Random(1);
    for (int i = 0; i < 100; i++) {
      assertTrue(Bic.isValid(Bic.random(random, 8).toString()));
      Bic bic = Bic.random("de", random, 11);
      assertEquals("DE", bic.countryCode());
      assertEquals(11, bic.toString().length());
      assertTrue(Bic.isValid(bic.toString()));
    }
    try {
      Bic.random("ZZ", random);
      fail();
    } catch (io.github.nalukit.pololei.error.InvalidBicException e) {
      assertEquals(BicError.UNKNOWN_COUNTRY, e.result().error().orElseThrow());
    }
    try {
      Bic.random(random, 9);
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testParse() {
    Bic bic = Bic.parse("deut de ff 500");
    assertEquals("DEUTDEFF500", bic.toString());
    assertEquals("DEUT", bic.institutionCode());
    assertEquals("DE", bic.countryCode());
    assertEquals("FF", bic.locationCode());
    assertEquals("500", bic.branchCode()
                           .orElseThrow());
    assertFalse(bic.isPrimaryOffice());
  }

  public void testValidationErrors() {
    assertEquals(BicError.EMPTY, Bic.validate("")
                                    .error()
                                    .orElseThrow());
    assertEquals(BicError.INVALID_LENGTH, Bic.validate("DEUTDEF")
                                             .error()
                                             .orElseThrow());
    assertEquals(BicError.INVALID_CHARACTER, Bic.validate("DEUT1EFF")
                                                .error()
                                                .orElseThrow());
    assertEquals(BicError.UNKNOWN_COUNTRY, Bic.validate("DEUTAAFF")
                                              .error()
                                              .orElseThrow());
  }
}

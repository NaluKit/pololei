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
import io.github.nalukit.pololei.creditor.CreditorIdentifier;
import io.github.nalukit.pololei.creditor.CreditorIdentifierError;
import io.github.nalukit.pololei.creditor.InvalidCreditorIdentifierException;

/**
 * Runs the SEPA Creditor Identifier compiled to JavaScript; the same cases as CreditorIdentifierTest.
 */
public class GwtTestCreditor
    extends GWTTestCase {

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  public void testPublishedIdentifiers() {
    CreditorIdentifier ci = CreditorIdentifier.parse(" de98 zzz 09999999999 ");
    assertEquals("DE98ZZZ09999999999", ci.toString());
    assertEquals("ZZZ", ci.businessCode());
    assertEquals("09999999999", ci.nationalIdentifier());
    assertTrue(CreditorIdentifier.isValid("ES23ZZZ47690558N"));
    assertTrue(CreditorIdentifier.isValid("MT50ZZZ670169305T"));
    assertEquals(ci, CreditorIdentifier.of("DE", null, "09999999999"));
    assertTrue(ci.isSameCreditor(ci.withBusinessCode("A1B")));
    assertEquals("DE98A1B09999999999", ci.withBusinessCode("A1B")
                                         .toString());
  }

  public void testInvalidIdentifiers() {
    assertEquals(CreditorIdentifierError.INVALID_CHECK_DIGITS, CreditorIdentifier.validate("DE97ZZZ09999999999")
                                                                                 .orElseThrow());
    assertEquals(CreditorIdentifierError.INVALID_LENGTH, CreditorIdentifier.validate("DE98ZZZ0999999999")
                                                                           .orElseThrow());
    assertEquals(CreditorIdentifierError.UNSUPPORTED_COUNTRY, CreditorIdentifier.validate("US98ZZZ09999999999")
                                                                                .orElseThrow());
    assertEquals(CreditorIdentifierError.EMPTY, CreditorIdentifier.validate(null)
                                                                  .orElseThrow());
    try {
      CreditorIdentifier.parse("DE98ZZZ0999999999-");
      fail("exception expected");
    } catch (InvalidCreditorIdentifierException expected) {
      assertEquals(CreditorIdentifierError.INVALID_CHARACTER, expected.error());
      assertEquals(18, expected.position()
                               .getAsInt());
    }
  }
}

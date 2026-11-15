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

import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidIbanException;
import io.github.nalukit.pololei.registry.BbanField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Building Hungarian IBANs from the fields bank, branch, routing check digit and account number. */
class HungarianBuilderTest {

  private static IbanBuilder hu() {
    return Iban.builder()
               .countryCode("HU");
  }

  @Test
  void buildsFromFields() {
    Iban iban = hu().field(BbanField.BANK_IDENTIFIER, "117")
                    .field(BbanField.BRANCH_IDENTIFIER, "7301")
                    .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                    .field(BbanField.ACCOUNT_NUMBER, "1111101800000000")
                    .build();
    assertEquals("HU42117730161111101800000000", iban.toString());
  }

  @Test
  void parseFieldsBuildKeepsTheIban() {
    for (String value : new String[] { "HU42117730161111101800000000", "HU74116000060000000034953226" }) {
      Iban iban = Iban.parse(value);
      Iban built = hu().field(BbanField.BANK_IDENTIFIER, iban.field(BbanField.BANK_IDENTIFIER).orElseThrow())
                       .field(BbanField.BRANCH_IDENTIFIER, iban.field(BbanField.BRANCH_IDENTIFIER).orElseThrow())
                       .field(BbanField.ROUTING_CHECK_DIGIT, iban.field(BbanField.ROUTING_CHECK_DIGIT).orElseThrow())
                       .field(BbanField.ACCOUNT_NUMBER, iban.field(BbanField.ACCOUNT_NUMBER).orElseThrow())
                       .build();
      assertEquals(iban, built, value);
    }
  }

  @Test
  void missingRoutingCheckDigitIsRejected() {
    assertThrows(InvalidIbanException.class, () -> hu().field(BbanField.BANK_IDENTIFIER, "117")
                                                       .field(BbanField.BRANCH_IDENTIFIER, "7301")
                                                       .field(BbanField.ACCOUNT_NUMBER, "1111101800000000")
                                                       .build());
  }

  @Test
  void contradictoryFieldsAreRejected() {
    // the former account field and a national check digit field no longer exist
    InvalidIbanException e = assertThrows(InvalidIbanException.class, () -> hu().field(BbanField.BANK_IDENTIFIER, "117")
                                                                              .field(BbanField.BRANCH_IDENTIFIER, "7301")
                                                                              .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                                                                              .field(BbanField.ACCOUNT_NUMBER, "1111101800000000")
                                                                              .field(BbanField.NATIONAL_CHECK_DIGITS, "0")
                                                                              .build());
    assertEquals(IbanError.UNDEFINED_FIELD, e.result().error().orElseThrow());
    // the old 15 digit account number is too short
    assertThrows(InvalidIbanException.class, () -> hu().field(BbanField.BANK_IDENTIFIER, "117")
                                                       .field(BbanField.BRANCH_IDENTIFIER, "7301")
                                                       .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                                                       .field(BbanField.ACCOUNT_NUMBER, "111110180000000")
                                                       .build());
    // a complete BBAN and parts
    assertThrows(IllegalStateException.class, () -> hu().bban("117730161111101800000000")
                                                        .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                                                        .build());
    // accountNumber() cannot be combined with the routing check digit
    assertThrows(IllegalStateException.class, () -> hu().accountNumber("1111101800000000")
                                                        .field(BbanField.ROUTING_CHECK_DIGIT, "6")
                                                        .build());
  }
}

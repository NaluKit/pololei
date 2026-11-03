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
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.epc.EpcQrPayload;

import java.math.BigDecimal;

/**
 * Runs the EPC QR code payloads compiled to JavaScript; the same cases as EpcQrPayloadTest for
 * the character sets supported in GWT (UTF-8 and ISO 8859-1).
 */
public class GwtTestEpc
    extends GWTTestCase {

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  private static EpcQrPayload.Builder belgian() {
    return EpcQrPayload.builder()
                       .beneficiaryName("Red Cross of Belgium")
                       .iban(Iban.parse("BE72000000001616"))
                       .bic(Bic.parse("BPOTBEB1"));
  }

  public void testEpcExample() {
    assertEquals("BCD\n002\n1\nSCT\nBPOTBEB1\nRed Cross of Belgium\nBE72000000001616\nEUR1\n\n\nUrgency fund", belgian().amount(new BigDecimal("1.00"))
                                                                                                                         .remittanceText("Urgency fund")
                                                                                                                         .build()
                                                                                                                         .text());
  }

  public void testAllFieldsInVersion001() {
    assertEquals("BCD\n001\n2\nSCT\nBPOTBEB1\nRed Cross of Belgium\nBE72000000001616\nEUR12.3\nCHAR\nRF18539007547034\n\nThank you",
                 belgian().version(EpcQrPayload.Version.V001)
                          .characterSet(EpcQrPayload.CharacterSet.ISO_8859_1)
                          .amount(new BigDecimal("12.30"))
                          .purpose("CHAR")
                          .remittanceReference("RF18539007547034")
                          .information("Thank you")
                          .build()
                          .text());
  }

  public void testCharacterSets() {
    EpcQrPayload.CharacterSet[] sets    = { EpcQrPayload.CharacterSet.ISO_8859_2, EpcQrPayload.CharacterSet.ISO_8859_4, EpcQrPayload.CharacterSet.ISO_8859_5, EpcQrPayload.CharacterSet.ISO_8859_7,
                                            EpcQrPayload.CharacterSet.ISO_8859_10, EpcQrPayload.CharacterSet.ISO_8859_15 };
    String[]                    letters = { "ł", "ķ", "ж", "λ", "ŋ", "œ" };
    String[]                    missing = { "€", "€", "é", "é", "€", "¤" };
    for (int i = 0; i < sets.length; i++) {
      EpcQrPayload payload = belgian().characterSet(sets[i])
                                      .beneficiaryName(letters[i].repeat(70))
                                      .remittanceText(letters[i].repeat(140))
                                      .information(letters[i].repeat(70))
                                      .build();
      assertEquals(sets[i].code(), payload.lines()
                                          .get(2));
      assertEquals(325, payload.byteLength());
      try {
        belgian().characterSet(sets[i])
                 .remittanceText("x" + missing[i])
                 .build();
        fail("exception expected for " + sets[i]);
      } catch (IllegalArgumentException expected) {
        // the character set lacks the character
      }
    }
  }

  public void testBytes() {
    assertEquals(EpcQrPayload.MAX_BYTES, belgian().beneficiaryName("ü".repeat(70))
                                                  .remittanceText("ü".repeat(7) + "x".repeat(133))
                                                  .build()
                                                  .byteLength());
    try {
      belgian().beneficiaryName("ü".repeat(70))
               .remittanceText("ü".repeat(8) + "x".repeat(132))
               .build();
      fail("exception expected");
    } catch (IllegalArgumentException expected) {
      assertEquals("the payload must not exceed 331 bytes, but has 332", expected.getMessage());
    }
    assertEquals(11, belgian().remittanceText("€😀")
                              .build()
                              .byteLength() - belgian().build()
                                                       .byteLength());
  }

  public void testRules() {
    try {
      belgian().remittanceReference("RF18539007547034")
               .remittanceText("text")
               .build();
      fail("exception expected");
    } catch (IllegalArgumentException expected) {
      assertEquals("remittanceReference and remittanceText exclude each other", expected.getMessage());
    }
    try {
      belgian().amount(new BigDecimal("1.234"))
               .build();
      fail("exception expected");
    } catch (IllegalArgumentException expected) {
      assertEquals("amount must have at most two decimals", expected.getMessage());
    }
    assertEquals("EUR100", belgian().amount(new BigDecimal("100.00"))
                                    .build()
                                    .lines()
                                    .get(7));
  }
}

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
package io.github.nalukit.pololei.epc;

import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EpcQrPayloadTest {

  private static final Iban BELGIUM = Iban.parse("BE72000000001616");
  private static final Bic  BIC     = Bic.parse("BPOTBEB1");

  private static String golden(String name) throws IOException {
    try (InputStream in = EpcQrPayloadTest.class.getResourceAsStream(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static EpcQrPayload.Builder belgian() {
    return EpcQrPayload.builder()
                       .beneficiaryName("Red Cross of Belgium")
                       .iban(BELGIUM)
                       .bic(BIC);
  }

  /** The example of the EPC guidelines. */
  @Test
  void epcExample() throws IOException {
    EpcQrPayload payload = belgian().amount(new BigDecimal("1.00"))
                                    .remittanceText("Urgency fund")
                                    .build();
    assertEquals(golden("epc-example-v002.txt"), payload.text());
    assertEquals(List.of("BCD", "002", "1", "SCT", "BPOTBEB1", "Red Cross of Belgium", "BE72000000001616", "EUR1", "", "", "Urgency fund"), payload.lines());
    assertEquals(EpcQrPayload.Version.V002, payload.version());
    assertEquals(EpcQrPayload.CharacterSet.UTF_8, payload.characterSet());
  }

  @Test
  void allFieldsInVersion001() throws IOException {
    EpcQrPayload payload = belgian().version(EpcQrPayload.Version.V001)
                                    .characterSet(EpcQrPayload.CharacterSet.ISO_8859_1)
                                    .amount(new BigDecimal("12.30"))
                                    .purpose("CHAR")
                                    .remittanceReference("RF18539007547034")
                                    .information("Thank you")
                                    .build();
    assertEquals(golden("full-v001.txt"), payload.text());
    assertEquals(12, payload.lines()
                            .size());
  }

  @Test
  void minimalVersion002WithoutBic() throws IOException {
    EpcQrPayload payload = EpcQrPayload.builder()
                                       .beneficiaryName("Franz Müller")
                                       .iban(Iban.parse("DE89370400440532013000"))
                                       .build();
    assertEquals(golden("minimal-v002.txt"), payload.text());
    // trailing empty lines are left out, the empty BIC line stays
    assertEquals(7, payload.lines()
                           .size());
  }

  @Test
  void bicRules() {
    assertThrows(IllegalArgumentException.class, () -> EpcQrPayload.builder()
                                                                   .version(EpcQrPayload.Version.V001)
                                                                   .beneficiaryName("A")
                                                                   .iban(BELGIUM)
                                                                   .build());
    // Switzerland, Monaco, San Marino, ... are SEPA but not EEA
    IllegalArgumentException outsideEea = assertThrows(IllegalArgumentException.class, () -> EpcQrPayload.builder()
                                                                                                          .beneficiaryName("A")
                                                                                                          .iban(Iban.parse("CH9300762011623852957"))
                                                                                                          .build());
    assertEquals("bic is mandatory for a beneficiary outside the EEA", outsideEea.getMessage());
    EpcQrPayload.builder()
                .beneficiaryName("A")
                .iban(Iban.parse("CH9300762011623852957"))
                .bic(Bic.parse("UBSWCHZH80A"))
                .build();
    // a credit transfer needs a SEPA account
    assertThrows(IllegalArgumentException.class, () -> EpcQrPayload.builder()
                                                                   .beneficiaryName("A")
                                                                   .iban(Iban.parse("AE070331234567890123456"))
                                                                   .bic(Bic.parse("BPOTBEB1"))
                                                                   .build());
  }

  @ParameterizedTest
  @ValueSource(strings = { "0.01", "999999999.99", "1", "1.5", "1.50", "100" })
  void validAmounts(String amount) {
    String line = belgian().amount(new BigDecimal(amount))
                           .build()
                           .lines()
                           .get(7);
    assertEquals("EUR" + new BigDecimal(amount).stripTrailingZeros()
                                                .toPlainString(), line);
  }

  @ParameterizedTest
  @ValueSource(strings = { "0", "0.001", "-1", "1000000000", "1.234" })
  void invalidAmounts(String amount) {
    assertThrows(IllegalArgumentException.class, () -> belgian().amount(new BigDecimal(amount))
                                                                .build());
  }

  @Test
  void amountFormat() {
    assertEquals("EUR100", belgian().amount(new BigDecimal("100.00"))
                                    .build()
                                    .lines()
                                    .get(7));
  }

  @Test
  void mandatoryFieldsAndLimits() {
    assertThrows(IllegalArgumentException.class, () -> EpcQrPayload.builder()
                                                                   .beneficiaryName("A")
                                                                   .build());
    assertThrows(IllegalArgumentException.class, () -> EpcQrPayload.builder()
                                                                   .iban(BELGIUM)
                                                                   .bic(BIC)
                                                                   .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().beneficiaryName("x".repeat(71))
                                                                .build());
    belgian().beneficiaryName("x".repeat(70))
             .build();
    assertThrows(IllegalArgumentException.class, () -> belgian().remittanceReference("x".repeat(36))
                                                                .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().remittanceText("x".repeat(141))
                                                                .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().information("x".repeat(71))
                                                                .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().purpose("CHARI")
                                                                .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().purpose("CH-R")
                                                                .build());
  }

  @Test
  void lineBreaksAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> belgian().beneficiaryName("Red\nCross")
                                                                .build());
    assertThrows(IllegalArgumentException.class, () -> belgian().remittanceText("a\r\nb")
                                                                .build());
  }

  @Test
  void referenceAndTextExcludeEachOther() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> belgian().remittanceReference("RF18539007547034")
                                                                                                       .remittanceText("text")
                                                                                                       .build());
    assertEquals("remittanceReference and remittanceText exclude each other", exception.getMessage());
  }

  @Test
  void byteLimitCountsBytesNotCharacters() {
    // 70 + 140 + 70 characters of 'é' fit the character limits, but not the 331 bytes in UTF-8
    EpcQrPayload.Builder builder = belgian().beneficiaryName("é".repeat(70))
                                            .remittanceText("é".repeat(140))
                                            .information("é".repeat(70));
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
    // 34 characters of fixed values, 11 line feeds, 280 two-byte characters
    assertEquals("the payload must not exceed 331 bytes, but has 605", exception.getMessage());
    // in ISO 8859-1 one character is one byte
    EpcQrPayload latin = builder.characterSet(EpcQrPayload.CharacterSet.ISO_8859_1)
                                .build();
    assertEquals(325, latin.byteLength());
    // exactly at the limit and one byte above: 44 bytes of fixed lines, 140 bytes of name, 147 bytes of text
    EpcQrPayload limit = belgian().beneficiaryName("ü".repeat(70))
                                  .remittanceText("ü".repeat(7) + "x".repeat(133))
                                  .build();
    assertEquals(EpcQrPayload.MAX_BYTES, limit.byteLength());
    assertEquals(140, limit.lines()
                           .get(10)
                           .length());
    assertThrows(IllegalArgumentException.class, () -> belgian().beneficiaryName("ü".repeat(70))
                                                                .remittanceText("ü".repeat(8) + "x".repeat(132))
                                                                .build());
  }

  @Test
  void characterSetCoverage() {
    assertThrows(IllegalArgumentException.class, () -> belgian().characterSet(EpcQrPayload.CharacterSet.ISO_8859_1)
                                                                .remittanceText("€ 10")
                                                                .build());
    // four line feeds, 3 bytes for the euro sign and 4 for the emoji (a surrogate pair)
    assertEquals(4 + 3 + 4, belgian().remittanceText("€😀")
                                     .build()
                                     .byteLength() - belgian().build()
                                                              .byteLength());
    // a lone surrogate cannot be encoded
    assertThrows(IllegalArgumentException.class, () -> belgian().remittanceText("\uD83D")
                                                                .build());
  }

  /** One payload per character set: national letters, the code in line 3, a character the set lacks. */
  @ParameterizedTest
  @CsvSource({ "UTF_8, 1, UTF-8, Łódź Москва Αθήνα €, ",
               "ISO_8859_1, 2, ISO-8859-1, Société Générale, €",
               "ISO_8859_2, 3, ISO-8859-2, Łódź Żółć Řeka, €",
               "ISO_8859_4, 4, ISO-8859-4, Rīga Ķemeri Šiauliai, €",
               "ISO_8859_5, 5, ISO-8859-5, Москва Київ, é",
               "ISO_8859_7, 6, ISO-8859-7, Αθήνα €, é",
               "ISO_8859_10, 7, ISO-8859-10, Þórshöfn Ŋ Ķ, €",
               "ISO_8859_15, 8, ISO-8859-15, Œuvre Šárka €, ¤" })
  void characterSets(EpcQrPayload.CharacterSet set,
                     String code,
                     String charset,
                     String name,
                     String missing) {
    EpcQrPayload payload = belgian().characterSet(set)
                                    .beneficiaryName(name)
                                    .amount(new BigDecimal("12.30"))
                                    .remittanceText(name)
                                    .build();
    assertEquals(code, set.code());
    assertEquals(List.of("BCD", "002", code, "SCT", "BPOTBEB1", name, "BE72000000001616", "EUR12.3", "", "", name), payload.lines());
    // the JDK has no ISO 8859-10, where every character is one byte
    assertEquals(Charset.isSupported(charset) ? payload.text()
                                                       .getBytes(Charset.forName(charset)).length : payload.text()
                                                                                                           .length(), payload.byteLength());
    if (missing != null) {
      assertThrows(IllegalArgumentException.class, () -> belgian().characterSet(set)
                                                                  .remittanceText("x" + missing)
                                                                  .build());
    }
  }

  @Test
  void singleByteSetsAgreeWithTheJvm() {
    for (EpcQrPayload.CharacterSet set : EpcQrPayload.CharacterSet.values()) {
      if (set == EpcQrPayload.CharacterSet.UTF_8) {
        continue;
      }
      String name = set.name()
                       .replace("ISO_8859_", "ISO-8859-");
      if (!Charset.isSupported(name)) {
        continue;
      }
      CharsetEncoder encoder = Charset.forName(name)
                                      .newEncoder();
      for (char c = 0; c < 0xFFFF; c++) {
        if (!Character.isSurrogate(c)) {
          assertEquals(encoder.canEncode(c), set.bytes(String.valueOf(c)) == 1, set + " U+" + Integer.toHexString(c));
        }
      }
    }
  }

  @Test
  void iso885910() {
    // not in the JDK; spot checks against the Unicode mapping table 8859-10.TXT
    EpcQrPayload.CharacterSet set = EpcQrPayload.CharacterSet.ISO_8859_10;
    for (char c : "\u00A0\u0104\u0112\u2015\u016B\u00D0\u00FE\u0138".toCharArray()) {
      assertEquals(1, set.bytes(String.valueOf(c)), Integer.toHexString(c));
    }
    // in ISO 8859-4, but not in ISO 8859-10
    for (char c : "\u0156\u00A4\u02C7\u00AF".toCharArray()) {
      assertEquals(-1, set.bytes(String.valueOf(c)), Integer.toHexString(c));
    }
    assertEquals(96, (int) IntStream.rangeClosed(0xA0, 0xFFFF)
                                      .filter(c -> !Character.isSurrogate((char) c) && set.bytes(String.valueOf((char) c)) == 1)
                                      .count());
  }

  @Test
  void byteLimitOfSingleByteSets() {
    // 70 + 140 + 70 national letters are 280 bytes in every single byte set, 325 bytes in all
    String[] letters = { "é", "ł", "ķ", "ж", "λ", "ŋ", "œ" };
    EpcQrPayload.CharacterSet[] sets = { EpcQrPayload.CharacterSet.ISO_8859_1, EpcQrPayload.CharacterSet.ISO_8859_2, EpcQrPayload.CharacterSet.ISO_8859_4, EpcQrPayload.CharacterSet.ISO_8859_5,
                                         EpcQrPayload.CharacterSet.ISO_8859_7, EpcQrPayload.CharacterSet.ISO_8859_10, EpcQrPayload.CharacterSet.ISO_8859_15 };
    for (int i = 0; i < sets.length; i++) {
      EpcQrPayload.Builder builder = belgian().characterSet(sets[i])
                                              .beneficiaryName(letters[i].repeat(70))
                                              .remittanceText(letters[i].repeat(140))
                                              .information(letters[i].repeat(70));
      assertEquals(325, builder.build()
                               .byteLength(), sets[i].toString());
      // in UTF-8 the same text is too long
      assertThrows(IllegalArgumentException.class, () -> builder.characterSet(EpcQrPayload.CharacterSet.UTF_8)
                                                                .build());
    }
  }
}

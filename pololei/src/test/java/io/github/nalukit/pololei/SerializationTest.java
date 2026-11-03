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

import io.github.nalukit.pololei.error.BicError;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.error.InvalidBicException;
import io.github.nalukit.pololei.error.InvalidIbanException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerializationTest {

  @Test
  void ibanRoundTrip() throws Exception {
    Iban iban = Iban.parse("FR1420041010050500013M02606");
    Iban copy = (Iban) deserialize(serialize(iban));
    assertEquals(iban, copy);
    assertEquals(iban.branchIdentifier(), copy.branchIdentifier());
  }

  @Test
  void bicRoundTrip() throws Exception {
    Bic bic = Bic.parse("DEUTDEFF500");
    assertEquals(bic, deserialize(serialize(bic)));
  }

  @Test
  void validationResultRoundTrip() throws Exception {
    @SuppressWarnings("unchecked")
    ValidationResult<Iban, IbanError> copy = (ValidationResult<Iban, IbanError>) deserialize(serialize(Iban.validate("DE89370400440532013X00")));
    assertEquals(IbanError.INVALID_BBAN, copy.error()
                                             .orElseThrow());
    assertEquals(OptionalInt.of(20), copy.position());
  }

  @Test
  void manipulatedIbanIsRejected() throws Exception {
    byte[] bytes = replace(serialize(Iban.parse("DE89370400440532013000")), "DE89370400440532013000", "DE88370400440532013000");
    InvalidIbanException exception = assertThrows(InvalidIbanException.class, () -> deserialize(bytes));
    assertEquals(IbanError.INVALID_CHECK_DIGITS, exception.error());
  }

  @Test
  void manipulatedBicIsRejected() throws Exception {
    byte[] bytes = replace(serialize(Bic.parse("DEUTDEFF")), "DEUTDEFF", "DEUTAAFF");
    assertEquals(BicError.UNKNOWN_COUNTRY, assertThrows(InvalidBicException.class, () -> deserialize(bytes)).error());
  }

  private static byte[] serialize(Object value) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    return bytes.toByteArray();
  }

  private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return in.readObject();
    }
  }

  /** Replaces an ASCII string of the same length inside the serialized bytes. */
  private static byte[] replace(byte[] bytes,
                                String from,
                                String to) {
    byte[] search      = from.getBytes(StandardCharsets.US_ASCII);
    byte[] replacement = to.getBytes(StandardCharsets.US_ASCII);
    outer:
    for (int i = 0; i <= bytes.length - search.length; i++) {
      for (int j = 0; j < search.length; j++) {
        if (bytes[i + j] != search[j]) {
          continue outer;
        }
      }
      System.arraycopy(replacement, 0, bytes, i, replacement.length);
      return bytes;
    }
    throw new IllegalStateException("not found: " + from);
  }
}

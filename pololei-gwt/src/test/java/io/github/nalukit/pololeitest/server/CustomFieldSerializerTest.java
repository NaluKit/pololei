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
package io.github.nalukit.pololeitest.server;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamReader;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Bic_CustomFieldSerializer;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.IbanValidator;
import io.github.nalukit.pololei.Iban_CustomFieldSerializer;
import io.github.nalukit.pololei.NationalCheckMode;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.ValidationResult_CustomFieldSerializer;
import io.github.nalukit.pololei.error.IbanError;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks the server side of the GWT-RPC serializers: what a (manipulated) client sends is
 * validated before an instance is created.
 */
public class CustomFieldSerializerTest {

  @Test
  public void ibanIsWrittenInElectronicFormat() throws SerializationException {
    List<String> written = new ArrayList<>();
    Iban_CustomFieldSerializer.serialize(writer(written), Iban.parse("DE89 3704 0044 0532 0130 00"));
    assertEquals(List.of("DE89370400440532013000"), written);
  }

  @Test
  public void validIbanIsRead() throws SerializationException {
    assertEquals(Iban.parse("DE89370400440532013000"), Iban_CustomFieldSerializer.instantiate(reader("DE89370400440532013000")));
  }

  @Test
  public void manipulatedIbanIsRejected() {
    SerializationException exception = assertThrows(SerializationException.class, () -> Iban_CustomFieldSerializer.instantiate(reader("DE88370400440532013000")));
    assertTrue(exception.getMessage(), exception.getMessage()
                                                .contains("INVALID_CHECK_DIGITS"));
    assertThrows(SerializationException.class, () -> Iban_CustomFieldSerializer.instantiate(reader("DE89 3704 0044 0532 0130 00")));
    assertThrows(SerializationException.class, () -> Iban_CustomFieldSerializer.instantiate(reader(null)));
  }

  @Test
  public void bicRoundTrip() throws SerializationException {
    List<String> written = new ArrayList<>();
    Bic_CustomFieldSerializer.serialize(writer(written), Bic.parse("DEUTDEFF500"));
    assertEquals(Bic.parse("DEUTDEFF500"), Bic_CustomFieldSerializer.instantiate(reader(written.getFirst())));
  }

  @Test
  public void manipulatedBicIsRejected() {
    assertThrows(SerializationException.class, () -> Bic_CustomFieldSerializer.instantiate(reader("DEUTAAFF")));
    assertThrows(SerializationException.class, () -> Bic_CustomFieldSerializer.instantiate(reader(null)));
  }

  @Test
  public void forgedInstancesCannotBeSentEither() {
    // the server writes whatever the instance holds; the client rejects it (see GwtTestRpc)
    List<String> written = new ArrayList<>();
    Iban forged = EchoServiceImpl.forge(Iban.class, "DE88370400440532013000");
    assertThrows(SerializationException.class, () -> {
      Iban_CustomFieldSerializer.serialize(writer(written), forged);
      Iban_CustomFieldSerializer.instantiate(reader(written.getFirst()));
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  public void validationResultRoundTrip() throws SerializationException {
    ValidationResult<Iban, IbanError> original = IbanValidator.standard()
                                                              .nationalCheck(NationalCheckMode.REQUIRED)
                                                              .validate("FR8420041010050500013M02607");
    List<Object> written = new ArrayList<>();
    ValidationResult_CustomFieldSerializer.serialize(objectWriter(written), original);
    ValidationResult<Iban, IbanError> copy = ValidationResult_CustomFieldSerializer.instantiate(objectReader(written));
    assertEquals(original.toString(), copy.toString());
    assertEquals(original.position(), copy.position());
    assertEquals(original.lastPosition(), copy.lastPosition());
    assertEquals(original.rule(), copy.rule());
    assertEquals(original.nationalCheck(), copy.nationalCheck());

    written.clear();
    ValidationResult<Iban, IbanError> valid = Iban.validate("DE89370400440532013000");
    ValidationResult_CustomFieldSerializer.serialize(objectWriter(written), valid);
    assertEquals(valid.value(), ValidationResult_CustomFieldSerializer.instantiate(objectReader(written))
                                                                      .value());
  }

  @Test
  public void bbanValidationResultRoundTrip() throws SerializationException {
    List<Object> written = new ArrayList<>();
    ValidationResult_CustomFieldSerializer.serialize(objectWriter(written), io.github.nalukit.pololei.Bban.validate("GB", "nwbk60161331926819"));
    assertEquals("valid: NWBK60161331926819", ValidationResult_CustomFieldSerializer.instantiate(objectReader(written))
                                                                                    .toString());
  }

  @Test
  public void inconsistentValidationResultIsRejected() {
    // neither value nor error
    assertThrows(SerializationException.class, () -> instantiate(null, null, null, null, null));
    // value and error
    assertThrows(SerializationException.class, () -> instantiate("IBAN", "DE89370400440532013000", "IbanError", "EMPTY", "x"));
    // error without message
    assertThrows(SerializationException.class, () -> instantiate(null, null, "IbanError", "EMPTY", null));
    // manipulated IBAN, unknown types and constants
    assertThrows(SerializationException.class, () -> instantiate("IBAN", "DE88370400440532013000", null, null, null));
    assertThrows(SerializationException.class, () -> instantiate("OTHER", "x", null, null, null));
    assertThrows(SerializationException.class, () -> instantiate(null, null, "IbanError", "NO_SUCH_ERROR", "x"));
    assertThrows(SerializationException.class, () -> instantiate(null, null, "OtherError", "EMPTY", "x"));
  }

  private static ValidationResult<?, ?> instantiate(Object... values) throws SerializationException {
    List<Object> all = new ArrayList<>(java.util.Arrays.asList(values));
    all.addAll(java.util.Arrays.asList(-1, -1, -1, null, -1, null, "NOT_PERFORMED"));
    return ValidationResult_CustomFieldSerializer.instantiate(objectReader(all));
  }

  /** A reader that returns the given values in order, whatever type is requested. */
  private static SerializationStreamReader objectReader(List<Object> values) {
    Deque<Object> queue = new ArrayDeque<>();
    values.forEach(value -> queue.add(value == null ? NULL : value));
    return (SerializationStreamReader) Proxy.newProxyInstance(CustomFieldSerializerTest.class.getClassLoader(),
                                                              new Class<?>[] { SerializationStreamReader.class },
                                                              (proxy, method, args) -> {
                                                                Object value = queue.poll();
                                                                return value == NULL ? null : value;
                                                              });
  }

  private static final Object NULL = new Object();

  private static SerializationStreamWriter objectWriter(List<Object> written) {
    return (SerializationStreamWriter) Proxy.newProxyInstance(CustomFieldSerializerTest.class.getClassLoader(),
                                                              new Class<?>[] { SerializationStreamWriter.class },
                                                              (proxy, method, args) -> {
                                                                written.add(args[0]);
                                                                return null;
                                                              });
  }

  private static SerializationStreamReader reader(String value) {
    return (SerializationStreamReader) Proxy.newProxyInstance(CustomFieldSerializerTest.class.getClassLoader(),
                                                              new Class<?>[] { SerializationStreamReader.class },
                                                              (proxy, method, args) -> {
                                                                if (method.getName()
                                                                          .equals("readString")) {
                                                                  return value;
                                                                }
                                                                throw new UnsupportedOperationException(method.getName());
                                                              });
  }

  private static SerializationStreamWriter writer(List<String> written) {
    return (SerializationStreamWriter) Proxy.newProxyInstance(CustomFieldSerializerTest.class.getClassLoader(),
                                                              new Class<?>[] { SerializationStreamWriter.class },
                                                              (proxy, method, args) -> {
                                                                if (method.getName()
                                                                          .equals("writeString")) {
                                                                  written.add((String) args[0]);
                                                                  return null;
                                                                }
                                                                throw new UnsupportedOperationException(method.getName());
                                                              });
  }
}

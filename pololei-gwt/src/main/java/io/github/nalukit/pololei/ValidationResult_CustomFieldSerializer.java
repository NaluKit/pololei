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

import com.google.gwt.user.client.rpc.CustomFieldSerializer;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamReader;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import io.github.nalukit.pololei.error.BicError;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololei.registry.CharacterType;

/**
 * GWT-RPC serializer for {@link ValidationResult}. The fields of a result are final, so that it is
 * safely shared between threads; GWT-RPC cannot write final fields, so this serializer transfers
 * them and creates the instance when reading.
 * <p>
 * Everything is transferred as strings and numbers, so the serialization policy of an application
 * needs no entry for the error enums or the value types. A transferred {@link Iban} or {@link Bic}
 * is validated again, and fields that do not form a valid or an invalid result are rejected with a
 * {@link SerializationException}.
 */
@SuppressWarnings("rawtypes")
public final class ValidationResult_CustomFieldSerializer
    extends CustomFieldSerializer<ValidationResult> {

  private static final String IBAN       = "IBAN";
  private static final String BIC        = "BIC";
  private static final String TEXT       = "TEXT";
  private static final String IBAN_ERROR = "IbanError";
  private static final String BIC_ERROR  = "BicError";

  /**
   * Writes all fields of the result. Called by generated GWT-RPC code, not meant to be called
   * directly.
   *
   * @param writer   the stream to write to
   * @param instance the result
   * @throws SerializationException if writing fails or the result holds a type this serializer
   *                                does not know
   */
  public static void serialize(SerializationStreamWriter writer,
                               ValidationResult instance)
      throws SerializationException {
    Object value = instance.value()
                           .orElse(null);
    writer.writeString(value == null ? null : value instanceof Iban ? IBAN : value instanceof Bic ? BIC : value instanceof String ? TEXT : unknown(value));
    writer.writeString(value == null ? null : value.toString());
    Object error = instance.error()
                           .orElse(null);
    writer.writeString(error == null ? null : error instanceof IbanError ? IBAN_ERROR : error instanceof BicError ? BIC_ERROR : unknown(error));
    writer.writeString(error == null ? null : ((Enum<?>) error).name());
    writer.writeString((String) instance.message()
                                        .orElse(null));
    writer.writeInt(instance.position()
                            .orElse(ValidationResult.NONE));
    writer.writeInt(instance.expectedLength()
                            .orElse(ValidationResult.NONE));
    writer.writeInt(instance.actualLength()
                            .orElse(ValidationResult.NONE));
    CharacterType expectedType = (CharacterType) instance.expectedType()
                                                         .orElse(null);
    writer.writeString(expectedType == null ? null : expectedType.name());
    writer.writeInt(instance.lastPosition()
                            .orElse(ValidationResult.NONE));
    writer.writeString((String) instance.rule()
                                        .orElse(null));
    writer.writeString(instance.nationalCheck()
                               .name());
  }

  /**
   * Reads the fields and creates the result. Called by generated GWT-RPC code, not meant to be
   * called directly.
   *
   * @param reader the stream to read from
   * @return the result
   * @throws SerializationException if reading fails, a transferred IBAN or BIC is not valid, or the
   *                                fields do not form a valid or an invalid result
   */
  @SuppressWarnings("unchecked")
  public static ValidationResult instantiate(SerializationStreamReader reader)
      throws SerializationException {
    Object value   = value(reader.readString(), reader.readString());
    Enum   error   = error(reader.readString(), reader.readString());
    String message = reader.readString();
    if (value == null == (error == null) || error == null != (message == null)) {
      throw new SerializationException("invalid ValidationResult: either a value or an error with a message is required");
    }
    int           position       = reader.readInt();
    int           expectedLength = reader.readInt();
    int           actualLength   = reader.readInt();
    String        typeName       = reader.readString();
    int           lastPosition   = reader.readInt();
    String        rule           = reader.readString();
    String        statusName     = reader.readString();
    try {
      CharacterType       expectedType  = typeName == null ? null : CharacterType.valueOf(typeName);
      NationalCheckStatus nationalCheck = NationalCheckStatus.valueOf(statusName);
      return ValidationResult.restore(value, error, message, position, expectedLength, actualLength, expectedType, lastPosition, rule, nationalCheck);
    } catch (RuntimeException e) {
      throw new SerializationException("invalid ValidationResult: unknown constant");
    }
  }

  private static Object value(String kind,
                              String value)
      throws SerializationException {
    if (kind == null) {
      return null;
    }
    if (value == null) {
      throw new SerializationException("invalid ValidationResult: value is missing");
    }
    switch (kind) {
      case IBAN:
        return Iban_CustomFieldSerializer.validated(value);
      case BIC:
        return Bic_CustomFieldSerializer.validated(value);
      case TEXT:
        return value;
      default:
        throw new SerializationException("invalid ValidationResult: unknown value type");
    }
  }

  private static Enum error(String kind,
                            String name)
      throws SerializationException {
    if (kind == null) {
      return null;
    }
    try {
      switch (kind) {
        case IBAN_ERROR:
          return IbanError.valueOf(name);
        case BIC_ERROR:
          return BicError.valueOf(name);
        default:
          throw new SerializationException("invalid ValidationResult: unknown error type");
      }
    } catch (RuntimeException e) {
      throw new SerializationException("invalid ValidationResult: unknown error");
    }
  }

  private static String unknown(Object object)
      throws SerializationException {
    throw new SerializationException("ValidationResult holds an unsupported type: " + object.getClass()
                                                                                          .getName());
  }

  /**
   * Does nothing, the result is completely read by
   * {@link #instantiate(SerializationStreamReader)}.
   *
   * @param reader   the stream to read from
   * @param instance the result
   */
  public static void deserialize(SerializationStreamReader reader,
                                 ValidationResult instance) {
    // everything is read in instantiate
  }

  /**
   * Indicates that deserialization creates the immutable value in the custom instantiation step.
   *
   * @return {@code true}
   */
  @Override
  public boolean hasCustomInstantiateInstance() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link #instantiate(SerializationStreamReader)}.
   */
  @Override
  public ValidationResult instantiateInstance(SerializationStreamReader reader)
      throws SerializationException {
    return instantiate(reader);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link #serialize(SerializationStreamWriter, ValidationResult)}.
   */
  @Override
  public void serializeInstance(SerializationStreamWriter writer,
                                ValidationResult instance)
      throws SerializationException {
    serialize(writer, instance);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link #deserialize(SerializationStreamReader, ValidationResult)}.
   */
  @Override
  public void deserializeInstance(SerializationStreamReader reader,
                                  ValidationResult instance) {
    deserialize(reader, instance);
  }
}

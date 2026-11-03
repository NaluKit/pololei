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

/**
 * GWT-RPC serializer for {@link Bic}: transfers the BIC and validates it again
 * when reading, on the client and on the server. A manipulated request cannot create an
 * invalid BIC; it fails with a {@link SerializationException} instead.
 */
public final class Bic_CustomFieldSerializer
    extends CustomFieldSerializer<Bic> {

  /**
   * Writes the BIC as string. Called by generated GWT-RPC code, not meant to be called directly.
   *
   * @param writer   the stream to write to
   * @param instance the BIC
   * @throws SerializationException if writing fails
   */
  public static void serialize(SerializationStreamWriter writer,
                               Bic instance)
      throws SerializationException {
    writer.writeString(instance.toString());
  }

  /**
   * Reads and validates the BIC. Called by generated GWT-RPC code, not meant to be called
   * directly.
   *
   * @param reader the stream to read from
   * @return the BIC
   * @throws SerializationException if reading fails or the transferred value is not a valid BIC
   */
  public static Bic instantiate(SerializationStreamReader reader)
      throws SerializationException {
    return validated(reader.readString());
  }

  /** Validates a transferred BIC; also used by ValidationResult_CustomFieldSerializer. */
  static Bic validated(String value)
      throws SerializationException {
    ValidationResult<Bic, BicError> result = Bic.validate(value);
    if (!result.isValid()) {
      throw new SerializationException("invalid BIC (" + result.error()
                                                               .orElseThrow() + "): " + result.message()
                                                                                      .orElseThrow());
    }
    return result.value()
                 .orElseThrow();
  }

  /**
   * Does nothing, the BIC is completely read by {@link #instantiate(SerializationStreamReader)}.
   *
   * @param reader   the stream to read from
   * @param instance the BIC
   */
  public static void deserialize(SerializationStreamReader reader,
                                 Bic instance) {
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
  public Bic instantiateInstance(SerializationStreamReader reader)
      throws SerializationException {
    return instantiate(reader);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link #serialize(SerializationStreamWriter, Bic)}.
   */
  @Override
  public void serializeInstance(SerializationStreamWriter writer,
                                Bic instance)
      throws SerializationException {
    serialize(writer, instance);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link #deserialize(SerializationStreamReader, Bic)}.
   */
  @Override
  public void deserializeInstance(SerializationStreamReader reader,
                                  Bic instance) {
    deserialize(reader, instance);
  }
}

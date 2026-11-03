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

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.error.IbanError;
import io.github.nalukit.pololeitest.client.EchoService;

import java.io.Serial;
import java.lang.reflect.Constructor;

public class EchoServiceImpl
    extends RemoteServiceServlet
    implements EchoService {

  @Serial
  private static final long serialVersionUID = 1L;

  @Override
  public Iban echoIban(Iban iban) {
    return iban;
  }

  @Override
  public Bic echoBic(Bic bic) {
    return bic;
  }

  @Override
  public ValidationResult<Iban, IbanError> validate(String input) {
    return Iban.validate(input);
  }

  @Override
  public Iban manipulatedIban() {
    return forge(Iban.class, "DE88370400440532013000");
  }

  @Override
  public Bic manipulatedBic() {
    return forge(Bic.class, "DEUTAAFF");
  }

  /** Bypasses validation through the private constructor, like a manipulated stream would. */
  static <T> T forge(Class<T> type,
                     String value) {
    try {
      Constructor<T> constructor = type.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}

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

import com.google.gwt.user.client.rpc.AsyncCallback;
import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.error.IbanError;

public interface EchoServiceAsync {

  void echoIban(Iban iban,
                AsyncCallback<Iban> callback);

  void echoBic(Bic bic,
               AsyncCallback<Bic> callback);

  void validate(String input,
                AsyncCallback<ValidationResult<Iban, IbanError>> callback);

  void manipulatedIban(AsyncCallback<Iban> callback);

  void manipulatedBic(AsyncCallback<Bic> callback);
}

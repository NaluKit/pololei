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

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.IncompatibleRemoteServiceException;
import com.google.gwt.user.client.rpc.SerializationException;
import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.ValidationResult;
import io.github.nalukit.pololei.error.IbanError;

/**
 * Sends pololei types through a real GWT-RPC call to {@code EchoServiceImpl}.
 */
public class GwtTestRpc
    extends GWTTestCase {

  private static final int TIMEOUT = 20_000;

  @Override
  public String getModuleName() {
    return "io.github.nalukit.pololeitest.PololeiTest";
  }

  private EchoServiceAsync service() {
    return GWT.create(EchoService.class);
  }

  public void testIbanRoundTrip() {
    delayTestFinish(TIMEOUT);
    Iban iban = Iban.parse("GB29NWBK60161331926819");
    service().echoIban(iban, new Expect<Iban>() {
      @Override
      public void onSuccess(Iban result) {
        assertEquals(iban, result);
        assertEquals("601613", result.branchIdentifier()
                                    .orElseThrow());
        finishTest();
      }
    });
  }

  public void testBicRoundTrip() {
    delayTestFinish(TIMEOUT);
    Bic bic = Bic.parse("DEUTDEFF500");
    service().echoBic(bic, new Expect<Bic>() {
      @Override
      public void onSuccess(Bic result) {
        assertEquals(bic, result);
        finishTest();
      }
    });
  }

  public void testValidationResultFromServer() {
    delayTestFinish(TIMEOUT);
    service().validate("DE89 3704 0044 0532 013X 00", new Expect<ValidationResult<Iban, IbanError>>() {
      @Override
      public void onSuccess(ValidationResult<Iban, IbanError> result) {
        assertEquals(IbanError.INVALID_BBAN, result.error()
                                                   .orElseThrow());
        assertEquals(20, result.position()
                               .getAsInt());
        finishTest();
      }
    });
  }

  public void testValidValidationResultFromServer() {
    delayTestFinish(TIMEOUT);
    service().validate("DE89370400440532013000", new Expect<ValidationResult<Iban, IbanError>>() {
      @Override
      public void onSuccess(ValidationResult<Iban, IbanError> result) {
        assertEquals("DE89370400440532013000", result.value()
                                                     .orElseThrow()
                                                     .toString());
        finishTest();
      }
    });
  }

  public void testManipulatedIbanIsRejectedOnClient() {
    delayTestFinish(TIMEOUT);
    service().manipulatedIban(new ExpectFailure<Iban>());
  }

  public void testManipulatedBicIsRejectedOnClient() {
    delayTestFinish(TIMEOUT);
    service().manipulatedBic(new ExpectFailure<Bic>());
  }

  private abstract class Expect<T>
      implements AsyncCallback<T> {

    @Override
    public void onFailure(Throwable caught) {
      fail("unexpected failure: " + caught);
    }
  }

  private class ExpectFailure<T>
      implements AsyncCallback<T> {

    @Override
    public void onFailure(Throwable caught) {
      // the response arrives, but the client serializer refuses to create the instance;
      // GWT reports that as an incompatible response caused by our SerializationException
      assertTrue("unexpected failure: " + caught, caught instanceof IncompatibleRemoteServiceException);
      Throwable cause = caught.getCause();
      assertTrue("unexpected cause: " + cause, cause instanceof SerializationException);
      assertTrue(cause.getMessage(), cause.getMessage()
                                          .startsWith("invalid "));
      finishTest();
    }

    @Override
    public void onSuccess(T result) {
      fail("an invalid value must not be deserialized: " + result);
    }
  }
}

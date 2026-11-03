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
import io.github.nalukit.pololei.registry.BbanField;
import io.github.nalukit.pololei.registry.IbanCountry;
import io.github.nalukit.pololei.registry.IbanRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the shared API from many threads at once and compares every result with the result of a
 * single thread. Such a test cannot prove thread safety, but it catches shared mutable state.
 */
class ConcurrencyTest {

  private static final int THREADS    = 16;
  private static final int ITERATIONS = 200;

  private static final IbanValidator VALIDATOR = IbanValidator.standard()
                                                              .nationalCheck(NationalCheckMode.IF_SUPPORTED);

  @Test
  void sharedApiGivesTheSameResultsInEveryThread() throws Exception {
    List<String> inputs = inputs();
    List<String> expected = new ArrayList<>();
    for (String input : inputs) {
      expected.add(describe(input));
    }
    List<String> results = runConcurrently(() -> {
      List<String> actual = new ArrayList<>();
      for (int i = 0; i < ITERATIONS; i++) {
        actual.clear();
        for (String input : inputs) {
          actual.add(describe(input));
        }
        assertEquals(expected, actual);
      }
      return "ok";
    });
    results.forEach(result -> assertEquals("ok", result));
  }

  @Test
  void oneBuilderPerThreadGivesTheSameIbans() throws Exception {
    List<String> results = runConcurrently(() -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < ITERATIONS; i++) {
        sb.setLength(0);
        sb.append(Iban.builder()
                      .countryCode("FR")
                      .bankIdentifier("20041")
                      .branchIdentifier("01005")
                      .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
                      .nationalCheckDigits(true)
                      .build())
          .append(' ')
          .append(Iban.builder()
                      .countryCode("IT")
                      .bankIdentifier("05428")
                      .nationalCheckDigits(true)
                      .buildRandom(new Random(i)));
      }
      return sb.toString();
    });
    String first = results.get(0);
    assertTrue(first.startsWith("FR1420041010050500013M02606 IT"));
    results.forEach(result -> assertEquals(first, result));
  }

  /** Runs the task in {@value #THREADS} threads that start at the same time. */
  private static <T> List<T> runConcurrently(Callable<T> task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      CountDownLatch  start   = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        futures.add(executor.submit(() -> {
          start.await();
          return task.call();
        }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private static List<String> inputs() {
    List<String> inputs = new ArrayList<>();
    for (IbanCountry country : IbanRegistry.countries()) {
      String example = country.ibanExample();
      inputs.add(example);
      inputs.add(IbanFormat.PRINT.format(example)
                                 .toLowerCase());
      // wrong IBAN check digits
      inputs.add(example.substring(0, 2) + (example.charAt(2) == '9' ? '0' : '9') + example.substring(3));
    }
    inputs.add("FR8420041010050500013M02607");
    inputs.add("DE89 3704 0044 0532 013X 00");
    inputs.add("");
    inputs.add(null);
    return inputs;
  }

  private static String describe(String input) {
    ValidationResult<Iban, IbanError> result = VALIDATOR.validate(input);
    return result + " " + result.position() + " " + result.nationalCheck() + " " + result.value()
                                                                                      .map(iban -> iban.fields() + " " + iban.accountNumber() + " " + iban.toPrintFormat())
                                                                                      .orElse("") + " " + Bic.validate(input == null || input.length() < 8 ? input : input.substring(0, 8));
  }
}

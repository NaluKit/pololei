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
package io.github.nalukit.pololeitest;

import com.google.gwt.junit.tools.GWTTestSuite;
import io.github.nalukit.pololeitest.client.GwtTestBic;
import io.github.nalukit.pololeitest.client.GwtTestCreditor;
import io.github.nalukit.pololeitest.client.GwtTestEpc;
import io.github.nalukit.pololeitest.client.GwtTestIban;
import io.github.nalukit.pololeitest.client.GwtTestNational;
import io.github.nalukit.pololeitest.client.GwtTestRpc;
import junit.framework.Test;

/**
 * Runs all GWT tests in one compilation; without the suite only the first test class is compiled.
 */
public class PololeiGwtSuite
    extends GWTTestSuite {

  public static Test suite() {
    GWTTestSuite suite = new GWTTestSuite("pololei GWT tests");
    suite.addTestSuite(GwtTestIban.class);
    suite.addTestSuite(GwtTestBic.class);
    suite.addTestSuite(GwtTestNational.class);
    suite.addTestSuite(GwtTestEpc.class);
    suite.addTestSuite(GwtTestCreditor.class);
    suite.addTestSuite(GwtTestRpc.class);
    return suite;
  }
}

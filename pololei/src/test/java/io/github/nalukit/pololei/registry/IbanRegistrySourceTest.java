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
package io.github.nalukit.pololei.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The country data is generated from exactly one SWIFT IBAN registry file. Its release and SHA-256 are
 * pinned in {@code scripts/registry/registry-source.properties}, written to the generated class and
 * documented in {@code docs/registry-audit.md}; this test keeps the three in line.
 */
class IbanRegistrySourceTest {

  private static final Path ROOT = Path.of("..");

  private static Properties pins() throws IOException {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(ROOT.resolve("scripts/registry/registry-source.properties"),
                                                 StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

  @Test
  void generatedDataNamesThePinnedRegistryFile() throws IOException {
    Properties pins = pins();
    assertEquals(pins.getProperty("release"), IbanRegistryData.SOURCE);
    assertEquals(pins.getProperty("sha256"), IbanRegistryData.SOURCE_SHA256);
    assertEquals(IbanRegistryData.SOURCE, IbanRegistry.source());
  }

  @Test
  void pinnedHashIsASha256() throws IOException {
    assertTrue(pins().getProperty("sha256")
                     .matches("[0-9a-f]{64}"));
  }

  @Test
  void auditDocumentsThePinnedRegistryFile() throws IOException {
    String audit = Files.readString(ROOT.resolve("docs/registry-audit.md"), StandardCharsets.UTF_8);
    assertTrue(audit.contains("| SHA-256 of the TXT file used | `" + IbanRegistryData.SOURCE_SHA256 + "` |"),
               "docs/registry-audit.md does not name SHA-256 " + IbanRegistryData.SOURCE_SHA256);
    String release = IbanRegistryData.SOURCE.replaceAll(".*Release ", "");
    assertTrue(audit.contains("| Source of truth | SWIFT IBAN Registry, release " + release + ", *IBAN Registry (TXT)* |"),
               "docs/registry-audit.md does not name registry release " + release);
  }
}

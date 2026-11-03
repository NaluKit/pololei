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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbanLayoutTest {

  /** Countries with a national layout; each one is documented with its source in docs/bban-fields.md. */
  private static final Set<String> SUPPLEMENTED = Set.of("AL", "BA", "BE", "BG", "BR", "DJ", "EE", "ES", "FI", "FO", "FR", "HU", "IS", "IT", "MC", "ME", "MK", "MR", "NO", "PL", "PT", "RS", "SC", "SE", "SI", "SK", "SM", "TL", "TN", "TR", "XK");

  @Test
  void supplementsAgreeWithTheRegistry() {
    Set<String> supplemented = new TreeSet<>();
    for (IbanCountry country : IbanRegistry.countries()) {
      assertNull(BbanLayoutData.problem(country.countryCode(), country.bbanLength(), country.bankIdentifier()
                                                                                          .orElse(null), country.branchIdentifier()
                                                                                                                .orElse(null)), country.countryCode());
      BbanLayout layout = country.bbanLayout();
      if (layout.isSupplemented()) {
        supplemented.add(country.countryCode());
      } else {
        assertEquals(BbanLayoutData.REGISTRY_SOURCE, layout.source());
      }
      assertFalse(layout.source()
                        .isEmpty());
      // bank identifier and, where the registry has one, branch identifier follow the registry
      assertEquals(country.bankIdentifier(), layout.find(BbanField.BANK_IDENTIFIER)
                                                   .map(BbanFieldDefinition::range));
      if (country.branchIdentifier()
                 .isPresent()) {
        assertEquals(country.branchIdentifier(), layout.find(BbanField.BRANCH_IDENTIFIER)
                                                       .map(BbanFieldDefinition::range));
      }
      // every position belongs to a field
      for (int i = 0; i < country.bbanLength(); i++) {
        assertTrue(layout.fieldAt(i)
                         .isPresent(), country.countryCode() + " position " + (i + 1));
      }
    }
    assertEquals(new TreeSet<>(SUPPLEMENTED), supplemented);
  }

  @Test
  void registryLayoutUsesTheRemainingPositionsAsAccountNumber() {
    BbanLayout germany = IbanRegistry.find("DE")
                                     .orElseThrow()
                                     .bbanLayout();
    assertFalse(germany.isSupplemented());
    assertEquals(List.of(new BbanFieldDefinition(BbanField.BANK_IDENTIFIER, new BbanRange(1, 8)),
                         new BbanFieldDefinition(BbanField.ACCOUNT_NUMBER, new BbanRange(9, 18))), germany.fields());
    assertFalse(germany.contains(BbanField.NATIONAL_CHECK_DIGITS));
    assertEquals(Optional.empty(), germany.find(BbanField.BRANCH_IDENTIFIER));
  }

  @Test
  void frenchLayoutAddsTheBranch() {
    IbanCountry france = IbanRegistry.find("FR")
                                     .orElseThrow();
    // the registry has no branch identifier for France, the RIB has one
    assertEquals(Optional.empty(), france.branchIdentifier());
    assertEquals(List.of(new BbanFieldDefinition(BbanField.BANK_IDENTIFIER, new BbanRange(1, 5)),
                         new BbanFieldDefinition(BbanField.BRANCH_IDENTIFIER, new BbanRange(6, 10)),
                         new BbanFieldDefinition(BbanField.ACCOUNT_NUMBER, new BbanRange(11, 21)),
                         new BbanFieldDefinition(BbanField.NATIONAL_CHECK_DIGITS, new BbanRange(22, 23))), france.bbanLayout()
                                                                                                                 .fields());
    assertTrue(france.bbanLayout()
                     .source()
                     .contains("CFONB"));
  }

  @Test
  void seychellesBankIdentifierExtension() {
    BbanLayout layout = IbanRegistry.find("SC")
                                    .orElseThrow()
                                    .bbanLayout();
    assertEquals(Optional.of(new BbanRange(1, 6)), layout.find(BbanField.BANK_IDENTIFIER)
                                                         .map(BbanFieldDefinition::range));
    assertEquals(Optional.of(new BbanRange(5, 6)), layout.find(BbanField.BANK_IDENTIFIER_EXTENSION)
                                                         .map(BbanFieldDefinition::range));
    // positions of the extension belong to the bank identifier
    assertEquals(Optional.of(BbanField.BANK_IDENTIFIER), layout.fieldAt(5));
    assertEquals(Optional.of(BbanField.CURRENCY), layout.fieldAt(26));
  }

  @Test
  void invalidLayoutsAreRejected() {
    BbanFieldDefinition bank = new BbanFieldDefinition(BbanField.BANK_IDENTIFIER, new BbanRange(1, 4));
    // overlapping fields
    assertThrows(IllegalArgumentException.class, () -> new BbanLayout(10, List.of(bank, new BbanFieldDefinition(BbanField.ACCOUNT_NUMBER, new BbanRange(4, 10))), "test", true));
    // field beyond the BBAN
    assertThrows(IllegalArgumentException.class, () -> new BbanLayout(10, List.of(bank, new BbanFieldDefinition(BbanField.ACCOUNT_NUMBER, new BbanRange(5, 11))), "test", true));
    // duplicate field
    assertThrows(IllegalArgumentException.class, () -> new BbanLayout(10, List.of(bank, new BbanFieldDefinition(BbanField.BANK_IDENTIFIER, new BbanRange(5, 6))), "test", true));
    // extension outside the bank identifier
    assertThrows(IllegalArgumentException.class, () -> new BbanLayout(10, List.of(bank, new BbanFieldDefinition(BbanField.BANK_IDENTIFIER_EXTENSION, new BbanRange(4, 5))), "test", true));
    assertThrows(IllegalArgumentException.class, () -> new BbanLayout(10, List.of(new BbanFieldDefinition(BbanField.BANK_IDENTIFIER_EXTENSION, new BbanRange(4, 5))), "test", true));
    assertThrows(IllegalArgumentException.class, () -> new BbanFieldDefinition(null, new BbanRange(1, 2)));
    assertThrows(IllegalArgumentException.class, () -> new BbanRange(3, 2));
  }

  @Test
  void supplementThatDisagreesWithTheRegistryIsIgnored() {
    // the French supplement does not fit a registry entry with another bank identifier
    assertTrue(BbanLayoutData.problem("FR", 23, new BbanRange(1, 4), null)
                             .contains("bank identifier"));
    assertTrue(BbanLayoutData.problem("FR", 24, new BbanRange(1, 5), null)
                             .contains("24"));
    assertTrue(BbanLayoutData.problem("MC", 23, new BbanRange(1, 5), new BbanRange(6, 9))
                             .contains("branch identifier"));
    BbanLayout fallback = BbanLayoutData.layout("FR", 23, new BbanRange(1, 4), null);
    assertFalse(fallback.isSupplemented());
    assertNull(BbanLayoutData.problem("DE", 18, new BbanRange(1, 8), null));
  }
}

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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanRegistryTest {

  @Test
  void countriesAreSortedAndUnique() {
    List<IbanCountry> countries = IbanRegistry.countries();
    assertFalse(countries.isEmpty());
    for (int i = 1; i < countries.size(); i++) {
      assertTrue(countries.get(i - 1)
                          .countryCode()
                          .compareTo(countries.get(i)
                                              .countryCode()) < 0);
    }
    assertThrows(UnsupportedOperationException.class, () -> countries.add(countries.getFirst()));
  }

  @Test
  void findCountry() {
    IbanCountry germany = IbanRegistry.find("DE")
                                      .orElseThrow();
    assertEquals("Germany", germany.countryName());
    assertEquals(22, germany.ibanLength());
    assertEquals(18, germany.bbanLength());
    assertEquals("8!n10!n", germany.bbanStructure()
                                   .toString());
    assertEquals(Optional.of(new BbanRange(1, 8)), germany.bankIdentifier());
    assertEquals(Optional.empty(), germany.branchIdentifier());
    assertTrue(germany.isSepa());
    assertTrue(IbanRegistry.isSupported("DE"));
    assertFalse(IbanRegistry.isSupported("US"));
    assertFalse(IbanRegistry.isSupported("de"));
    assertFalse(IbanRegistry.isSupported(null));
    assertFalse(IbanRegistry.source()
                            .isEmpty());
  }

  @Test
  void parseStructure() {
    BbanStructure structure = BbanStructure.parse("1!a5!n5!n12!c");
    assertEquals(23, structure.length());
    assertEquals(List.of(new BbanSegment(1, CharacterType.UPPER_CASE_LETTERS),
                         new BbanSegment(5, CharacterType.DIGITS),
                         new BbanSegment(5, CharacterType.DIGITS),
                         new BbanSegment(12, CharacterType.ALPHANUMERIC)), structure.segments());
    assertEquals("1!a5!n5!n12!c", structure.toString());
    assertEquals(structure, BbanStructure.parse("1!a5!n5!n12!c"));
  }

  @Test
  void invalidStructures() {
    assertThrows(IllegalArgumentException.class, () -> BbanStructure.parse(""));
    assertThrows(IllegalArgumentException.class, () -> BbanStructure.parse("8n"));
    assertThrows(IllegalArgumentException.class, () -> BbanStructure.parse("8!"));
    assertThrows(IllegalArgumentException.class, () -> BbanStructure.parse("8!x"));
    assertThrows(IllegalArgumentException.class, () -> BbanStructure.parse("0!n"));
  }

  @Test
  void matchStructure() {
    BbanStructure structure = BbanStructure.parse("2!a3!n");
    assertTrue(structure.matches("AB123"));
    assertEquals(0, structure.indexOfMismatch("1B123"));
    assertEquals(2, structure.indexOfMismatch("ABC23"));
    assertEquals(4, structure.indexOfMismatch("AB12"));
    assertEquals(5, structure.indexOfMismatch("AB1234"));
    assertTrue(structure.matches(structure.random(new Random(3))));
  }

  @Test
  void invalidRanges() {
    assertThrows(IllegalArgumentException.class, () -> new BbanRange(0, 3));
    assertThrows(IllegalArgumentException.class, () -> new BbanRange(4, 3));
    assertThrows(IllegalArgumentException.class, () -> new IbanCountry("XX", "Test", "4!n", "1-5", null, false, "XX00"));
  }

  @Test
  void accountPositions() {
    IbanCountry italy = IbanRegistry.find("IT")
                                    .orElseThrow();
    assertTrue(italy.isAccountPosition(0));
    assertFalse(italy.isAccountPosition(1));
    assertFalse(italy.isAccountPosition(10));
    assertTrue(italy.isAccountPosition(11));
    assertEquals(13, italy.accountNumberLength());
  }
}

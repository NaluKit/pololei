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

/**
 * The parts a BBAN can consist of. Which fields a country has and where they are is described by
 * its {@link BbanLayout}.
 */
public enum BbanField {

  /** Bank identifier (bank code), position as defined by the SWIFT IBAN registry. */
  BANK_IDENTIFIER("bank identifier"),
  /** Trailing part of the bank identifier that some countries use as an extension (Seychelles). */
  BANK_IDENTIFIER_EXTENSION("bank identifier extension"),
  /** Branch identifier (branch or sort code). */
  BRANCH_IDENTIFIER("branch identifier"),
  /**
   * The account number in the national sense. Where the country layout defines separate fields for national
   * check digits, types or codes, these are not part of it. The scope depends on the layout: the Hungarian
   * account number is the complete 16 character account part of BBAN positions 9-24, including a check or
   * padding position inside it, while the check digit over bank and branch is {@link #ROUTING_CHECK_DIGIT}.
   * See {@code docs/bban-fields.md} for the layout of each country.
   */
  ACCOUNT_NUMBER("account number"),
  /**
   * National check digits (for example the French RIB key or the Italian CIN). The Hungarian check digit over
   * bank and branch is not one of them, see {@link #ROUTING_CHECK_DIGIT}.
   */
  NATIONAL_CHECK_DIGITS("national check digits"),
  /**
   * Check digit over bank and branch identifier that is a position of its own between branch
   * identifier and account number (Hungary).
   */
  ROUTING_CHECK_DIGIT("routing check digit"),
  /** Type of the account (Bulgaria, Brazil, Iceland). */
  ACCOUNT_TYPE("account type"),
  /** Position of the account holder, for example primary or secondary holder (Brazil). */
  OWNER_ACCOUNT_TYPE("owner account type"),
  /** Identification number of the account holder (Icelandic kennitala). */
  IDENTIFICATION_NUMBER("identification number"),
  /** ISO 4217 currency code of the account (Seychelles). */
  CURRENCY("currency"),
  /** Position reserved for future use, currently {@code 0} (Turkiye). */
  RESERVED("reserved");

  private final String label;

  BbanField(String label) {
    this.label = label;
  }

  /**
   * Returns the English name of the field as used in error messages.
   *
   * @return the label, for example {@code account number}
   */
  public String label() {
    return label;
  }
}

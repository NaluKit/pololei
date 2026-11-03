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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates {@code IbanRegistryData.java} from the SWIFT IBAN registry (TXT).
 * <p>
 * The TXT file is a tab separated table with one row per data element and one column per country.
 * Cells may be quoted and contain line breaks. Older releases are encoded in Windows-1252, newer
 * ones in UTF-8.
 * <p>
 * Run with a single-file launch (Java 21+):
 * {@code java IbanRegistryGenerator.java <registry.txt> "<release description>" <output.java>}
 */
public class IbanRegistryGenerator {

  private static final Pattern SEGMENT = Pattern.compile("(\\d+)!([nac])");
  private static final Pattern RANGE   = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");

  record Country(String code,
                 String name,
                 String bbanStructure,
                 String bankIdentifier,
                 String branchIdentifier,
                 boolean sepa,
                 String ibanExample) {
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("usage: java IbanRegistryGenerator.java <registry.txt> \"<release description>\" <output.java>");
      System.exit(2);
    }
    Path          input     = Path.of(args[0]);
    String        source    = args[1].trim();
    Path          output    = Path.of(args[2]);
    List<Country> countries = new ArrayList<>();
    List<String>  errors    = new ArrayList<>();
    try {
      countries = parse(read(input), errors);
    } catch (IllegalArgumentException e) {
      errors.add(e.getMessage());
    }
    if (!errors.isEmpty()) {
      System.err.println("registry not generated, " + errors.size() + " error(s):");
      errors.forEach(error -> System.err.println("  " + error));
      System.exit(1);
    }
    Files.writeString(output, render(source, countries), StandardCharsets.UTF_8);
    System.out.println("wrote " + countries.size() + " countries to " + output);
  }

  private static String read(Path input) throws IOException {
    byte[] bytes = Files.readAllBytes(input);
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder()
                                   .onMalformedInput(CodingErrorAction.REPORT)
                                   .onUnmappableCharacter(CodingErrorAction.REPORT)
                                   .decode(ByteBuffer.wrap(bytes))
                                   .toString();
    } catch (CharacterCodingException e) {
      text = new String(bytes, Charset.forName("windows-1252"));
    }
    return text.startsWith("﻿") ? text.substring(1) : text;
  }

  /**
   * Splits tab separated text into rows of cells; quoted cells may contain tabs, line breaks and
   * doubled quotes.
   */
  static List<List<String>> table(String text) {
    List<List<String>> rows   = new ArrayList<>();
    List<String>       row    = new ArrayList<>();
    StringBuilder      cell   = new StringBuilder();
    boolean            quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          cell.append(c);
        }
      } else if (c == '"' && cell.isEmpty()) {
        quoted = true;
      } else if (c == '\t') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (c == '\r' || c == '\n') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
      } else {
        cell.append(c);
      }
    }
    if (!cell.isEmpty() || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  static List<Country> parse(String text,
                             List<String> errors) {
    List<List<String>> rows = table(text);
    List<String> codes     = row(rows, "iban prefix country code");
    List<String> names     = row(rows, "name of country");
    List<String> sepa      = row(rows, "sepa country");
    List<String> structure = row(rows, "bban structure");
    List<String> bank      = row(rows, "bank identifier position");
    List<String> branch    = row(rows, "branch identifier position");
    List<String> example   = row(rows, "iban electronic format example");
    List<String> length    = row(rows, "iban length");

    List<Country> countries = new ArrayList<>();
    Set<String>   seen      = new HashSet<>();
    for (int column = 1; column < codes.size(); column++) {
      String code = clean(codes.get(column));
      if (!code.matches("[A-Z]{2}")) {
        // label or example columns, empty trailing cells
        continue;
      }
      try {
        Country country = new Country(code,
                                      clean(cell(names, column)).replaceAll("(?i)\\s*\\(the\\)$", ""),
                                      clean(cell(structure, column)).replace(" ", ""),
                                      range(cell(bank, column)),
                                      range(cell(branch, column)),
                                      clean(cell(sepa, column)).toLowerCase(Locale.ROOT)
                                                               .startsWith("yes"),
                                      clean(cell(example, column)).replace(" ", ""));
        check(country, clean(cell(length, column)));
        if (!seen.add(code)) {
          throw new IllegalArgumentException("duplicate country code");
        }
        countries.add(country);
      } catch (IllegalArgumentException e) {
        errors.add(code + ": " + e.getMessage());
      }
    }
    if (countries.isEmpty() && errors.isEmpty()) {
      errors.add("no countries found - is this the SWIFT IBAN registry (TXT)?");
    }
    countries.sort(Comparator.comparing(Country::code));
    return countries;
  }

  /**
   * Finds the row whose label (first cell) starts with the given text, ignoring case and spacing.
   */
  private static List<String> row(List<List<String>> rows,
                                  String label) {
    for (List<String> row : rows) {
      if (!row.isEmpty() && clean(row.getFirst()).toLowerCase(Locale.ROOT)
                                             .startsWith(label)) {
        return row;
      }
    }
    throw new IllegalArgumentException("row '" + label + "' not found - is this the SWIFT IBAN registry (TXT)?");
  }

  private static String cell(List<String> row,
                             int column) {
    return column < row.size() ? row.get(column) : "";
  }

  private static String clean(String value) {
    return value.replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
  }

  /**
   * @return the range as {@code start-end}, or {@code null} if the registry defines none
   */
  private static String range(String value) {
    String text = clean(value);
    if (text.isEmpty() || text.equalsIgnoreCase("N/A")) {
      return null;
    }
    Matcher matcher = RANGE.matcher(text);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("invalid identifier position '" + text + "'");
    }
    return Integer.parseInt(matcher.group(1)) + "-" + Integer.parseInt(matcher.group(2));
  }

  /**
   * Checks structure, identifier positions, length and the example IBAN of a country.
   */
  private static void check(Country country,
                            String ibanLength) {
    if (country.name()
               .isEmpty()) {
      throw new IllegalArgumentException("country name missing");
    }
    StringBuilder pattern    = new StringBuilder();
    int           bbanLength = 0;
    Matcher       matcher    = SEGMENT.matcher(country.bbanStructure());
    int           end        = 0;
    while (matcher.find() && matcher.start() == end) {
      int length = Integer.parseInt(matcher.group(1));
      if (length < 1) {
        throw new IllegalArgumentException("invalid BBAN structure '" + country.bbanStructure() + "'");
      }
      pattern.append(switch (matcher.group(2)) {
        case "n" -> "[0-9]";
        case "a" -> "[A-Z]";
        default -> "[0-9A-Z]";
      })
             .append('{')
             .append(length)
             .append('}');
      bbanLength += length;
      end = matcher.end();
    }
    if (end == 0 || end != country.bbanStructure()
                                  .length()) {
      throw new IllegalArgumentException("invalid BBAN structure '" + country.bbanStructure() + "'");
    }
    if (ibanLength.matches("\\d+") && Integer.parseInt(ibanLength) != bbanLength + 4) {
      throw new IllegalArgumentException("IBAN length " + ibanLength + " does not match BBAN structure " + country.bbanStructure());
    }
    for (String range : new String[] { country.bankIdentifier(), country.branchIdentifier() }) {
      if (range != null) {
        String[] parts = range.split("-");
        int      start = Integer.parseInt(parts[0]);
        int      stop  = Integer.parseInt(parts[1]);
        if (start < 1 || stop < start || stop > bbanLength) {
          throw new IllegalArgumentException("identifier position " + range + " outside BBAN of length " + bbanLength);
        }
      }
    }
    String iban = country.ibanExample();
    if (!iban.startsWith(country.code())) {
      throw new IllegalArgumentException("example IBAN '" + iban + "' does not start with the country code");
    }
    if (!iban.matches("[A-Z]{2}[0-9]{2}" + pattern)) {
      throw new IllegalArgumentException("example IBAN '" + iban + "' does not match BBAN structure " + country.bbanStructure());
    }
    if (!checkDigitsValid(iban)) {
      throw new IllegalArgumentException("example IBAN '" + iban + "' has invalid check digits");
    }
  }

  private static boolean checkDigitsValid(String iban) {
    String        rearranged = iban.substring(4) + iban.substring(0, 4);
    StringBuilder digits     = new StringBuilder();
    for (char c : rearranged.toCharArray()) {
      digits.append(Character.getNumericValue(c));
    }
    return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97))
                                            .intValue() == 1;
  }

  private static String render(String source,
                               List<Country> countries) {
    StringBuilder sb = new StringBuilder();
    sb.append("""
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

                  import java.util.List;

                  /**
                   * GENERATED by scripts/registry/IbanRegistryGenerator.java - do not edit.
                   */
                  final class IbanRegistryData {

                  """);
    sb.append("  static final String SOURCE = ")
      .append(literal(source))
      .append(";\n\n");
    sb.append("""
                    private IbanRegistryData() {
                    }

                    static List<IbanCountry> countries() {
                      return List.of(
                  """);
    for (int i = 0; i < countries.size(); i++) {
      Country c = countries.get(i);
      sb.append("                   new IbanCountry(")
        .append(literal(c.code()))
        .append(", ")
        .append(literal(c.name()))
        .append(", ")
        .append(literal(c.bbanStructure()))
        .append(", ")
        .append(literal(c.bankIdentifier()))
        .append(", ")
        .append(literal(c.branchIdentifier()))
        .append(", ")
        .append(c.sepa())
        .append(", ")
        .append(literal(c.ibanExample()))
        .append(')')
        .append(i + 1 < countries.size() ? ",\n" : ");\n");
    }
    sb.append("  }\n}\n");
    return sb.toString();
  }

  /**
   * @return a Java string literal; non-ASCII characters are escaped so the source compiles with any encoding
   */
  private static String literal(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      if (c == '"' || c == '\\') {
        sb.append('\\')
          .append(c);
      } else if (c < 0x20 || c > 0x7E) {
        sb.append(String.format("\\u%04X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.append('"')
             .toString();
  }
}

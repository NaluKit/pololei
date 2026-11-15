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
package io.github.nalukit.pololeitest.corpus;

import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;
import io.github.nalukit.pololei.epc.EpcQrPayload;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The EPC QR code cases of the corpus ({@code epc-cases.tsv}): one row per upstream test case with its
 * line range, the literal fragments that prove the inputs, the inputs, what the upstream test states
 * and the reviewed pololei result.
 * <p>
 * The upstream statement is one of
 * <ul>
 * <li>{@code payload}: the complete payload, read from the file ({@code ruby:<line>} for a Ruby string
 * on one line, {@code template:<first>-<last>} for a Go or JavaScript template literal);</li>
 * <li>{@code asserts}: single assertions, {@code len=<bytes>}, {@code lines=<count>} and
 * {@code <index>=<line>} separated by {@code |};</li>
 * <li>{@code rejected}: the upstream implementation rejects the input.</li>
 * </ul>
 */
final class EpcCases {

  static final String REJECTED = "REJECTED: ";

  private static final Pattern RUBY_STRING = Pattern.compile("spec = \"(.*)\"\\s*$");

  private static final Pattern RUBY_ESCAPE = Pattern.compile("\\\\(x[0-9A-Fa-f]{2}|n|\"|\\\\)");

  private EpcCases() {
  }

  /** One row of {@code epc-cases.tsv}; field values are unescaped, an empty value is not set. */
  record Case(String id,
              String file,
              int firstLine,
              int lastLine,
              String description,
              List<String> evidence,
              String version,
              String charset,
              String bic,
              String name,
              String iban,
              String amount,
              String purpose,
              String reference,
              String text,
              String information,
              String upstream,
              String upstreamDetail,
              String result,
              String note) {

    String location() {
      return id + " (" + file + ":" + firstLine + "-" + lastLine + ")";
    }
  }

  /** The outcome of pololei for a case: the payload and its size, or the reason of the rejection. */
  record Outcome(String text,
                 int bytes,
                 String rejection) {

    boolean rejected() {
      return rejection != null;
    }

    /** The outcome as written in the column {@code result}. */
    String describe() {
      return rejected() ? REJECTED + rejection : escape(text);
    }
  }

  static List<Case> cases() {
    List<Case> cases = new ArrayList<>();
    for (String[] row : Corpus.table("epc-cases.tsv")) {
      String[] v = Arrays.stream(row)
                         .map(EpcCases::unescape)
                         .toArray(String[]::new);
      cases.add(new Case(v[0],
                         v[1],
                         Integer.parseInt(v[2]),
                         Integer.parseInt(v[3]),
                         v[4],
                         Arrays.asList(v[5].split(" \\|\\| ")),
                         v[6],
                         v[7],
                         v[8],
                         v[9],
                         v[10],
                         v[11],
                         v[12],
                         v[13],
                         v[14],
                         v[15],
                         v[16],
                         v[17],
                         row[18],
                         v[19]));
    }
    return cases;
  }

  /** Builds the payload of a case with pololei. */
  static Outcome build(Case c) {
    try {
      EpcQrPayload.Builder builder = EpcQrPayload.builder();
      if (!c.version()
            .isEmpty()) {
        builder.version(EpcQrPayload.Version.valueOf("V" + c.version()));
      }
      if (!c.charset()
            .isEmpty()) {
        builder.characterSet(Arrays.stream(EpcQrPayload.CharacterSet.values())
                                   .filter(set -> set.code()
                                                     .equals(c.charset()))
                                   .findFirst()
                                   .orElseThrow());
      }
      if (!c.bic()
            .isEmpty()) {
        builder.bic(Bic.parse(c.bic()));
      }
      if (!c.iban()
            .isEmpty()) {
        builder.iban(Iban.parse(c.iban()));
      }
      if (!c.amount()
            .isEmpty()) {
        builder.amount(new BigDecimal(c.amount()));
      }
      set(c.name(), builder::beneficiaryName);
      set(c.purpose(), builder::purpose);
      set(c.reference(), builder::remittanceReference);
      set(c.text(), builder::remittanceText);
      set(c.information(), builder::information);
      EpcQrPayload payload = builder.build();
      return new Outcome(payload.text(), payload.byteLength(), null);
    } catch (IllegalArgumentException e) {
      return new Outcome(null, -1, e.getMessage());
    }
  }

  private static void set(String value,
                          java.util.function.Consumer<String> setter) {
    if (!value.isEmpty()) {
      setter.accept(value);
    }
  }

  /** The payload the upstream test states, read from the retained file. */
  static String upstreamPayload(Case c) {
    String detail = c.upstreamDetail();
    List<String> lines = Corpus.lines(c.file());
    if (detail.startsWith("ruby:")) {
      Matcher matcher = RUBY_STRING.matcher(lines.get(Integer.parseInt(detail.substring(5)) - 1));
      if (!matcher.find()) {
        throw new IllegalStateException(c.location() + ": no Ruby string at " + detail);
      }
      return rubyString(matcher.group(1));
    }
    if (detail.startsWith("template:")) {
      String[] range = detail.substring(9)
                             .split("-");
      String text = String.join("\n", lines.subList(Integer.parseInt(range[0]) - 1, Integer.parseInt(range[1])));
      String literal = text.substring(text.indexOf('`') + 1, text.lastIndexOf('`'));
      // a backslash before a line break continues the line in a JavaScript template literal
      return literal.startsWith("\\\n") ? literal.substring(2) : literal;
    }
    throw new IllegalStateException(c.location() + ": unknown payload source " + detail);
  }

  private static String rubyString(String literal) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Matcher matcher = RUBY_ESCAPE.matcher(literal);
    int position = 0;
    while (matcher.find()) {
      bytes.writeBytes(literal.substring(position, matcher.start())
                              .getBytes(StandardCharsets.UTF_8));
      String escape = matcher.group(1);
      switch (escape) {
        case "n" -> bytes.write('\n');
        case "\"" -> bytes.write('"');
        case "\\" -> bytes.write('\\');
        default -> bytes.write(Integer.parseInt(escape.substring(1), 16));
      }
      position = matcher.end();
    }
    bytes.writeBytes(literal.substring(position)
                            .getBytes(StandardCharsets.UTF_8));
    return bytes.toString(StandardCharsets.UTF_8);
  }

  /** Whether the pololei outcome is what the upstream test states. */
  static boolean agrees(Case c,
                        Outcome outcome) {
    return switch (c.upstream()) {
      case "rejected" -> outcome.rejected();
      case "payload" -> !outcome.rejected() && outcome.text()
                                                      .equals(upstreamPayload(c));
      case "asserts" -> !outcome.rejected() && assertionsHold(c.upstreamDetail(), outcome);
      default -> throw new IllegalArgumentException(c.location() + ": unknown upstream statement " + c.upstream());
    };
  }

  private static boolean assertionsHold(String assertions,
                                        Outcome outcome) {
    List<String> lines = Arrays.asList(outcome.text()
                                              .split("\n", -1));
    for (String assertion : assertions.split("\\|", -1)) {
      int separator = assertion.indexOf('=');
      String key = assertion.substring(0, separator);
      String expected = assertion.substring(separator + 1);
      boolean holds = switch (key) {
        case "len" -> outcome.bytes() == Integer.parseInt(expected);
        case "lines" -> lines.size() == Integer.parseInt(expected);
        default -> {
          int index = Integer.parseInt(key);
          yield index < lines.size() ? lines.get(index)
                                            .equals(expected) : expected.isEmpty();
        }
      };
      if (!holds) {
        return false;
      }
    }
    return true;
  }

  static String escape(String value) {
    return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
  }

  static String unescape(String value) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 1 < value.length()) {
        char next = value.charAt(++i);
        sb.append(switch (next) {
          case 'n' -> '\n';
          case 't' -> '\t';
          default -> next;
        });
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

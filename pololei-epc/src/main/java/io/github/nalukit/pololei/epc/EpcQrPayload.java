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
package io.github.nalukit.pololei.epc;

import io.github.nalukit.pololei.Bic;
import io.github.nalukit.pololei.Iban;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The text of an EPC QR code (EPC069-12, also known as GiroCode) for a SEPA credit transfer.
 * <p>
 * pololei creates the text only; render it with any QR code library, using error correction
 * level M and the {@link CharacterSet} of the payload.
 *
 * <pre>{@code
 * EpcQrPayload payload = EpcQrPayload.builder()
 *                                    .beneficiaryName("Red Cross of Belgium")
 *                                    .iban(Iban.parse("BE72000000001616"))
 *                                    .bic(Bic.parse("BPOTBEB1"))
 *                                    .amount(new BigDecimal("1.00"))
 *                                    .remittanceText("Urgency fund")
 *                                    .build();
 * payload.text();   // "BCD\n002\n1\nSCT\nBPOTBEB1\nRed Cross of Belgium\nBE72000000001616\nEUR1\n\n\nUrgency fund"
 * }</pre>
 * <p>
 * Rules (EPC069-12): lines are separated by a line feed, trailing empty lines are left out, the
 * payload has at most 331 bytes in its character set. Version 001 needs a BIC, version 002 only
 * for a beneficiary outside the EEA. The amount is in euro, from 0.01 to 999999999.99 with at most
 * two decimals. A structured reference (35 characters) and an unstructured text (140 characters)
 * exclude each other.
 * <p>
 * A payload is immutable and thread-safe. Its {@link Builder} is mutable and not thread-safe; create
 * one per payload.
 */
public final class EpcQrPayload {

  /** Maximum size of the payload in bytes. */
  public static final int MAX_BYTES = 331;

  /** Payload versions of EPC069-12. */
  public enum Version {
    /** Version 001: the BIC is mandatory. */
    V001("001"),
    /** Version 002: the BIC is optional within the EEA. */
    V002("002");

    private final String code;

    Version(String code) {
      this.code = code;
    }

    /**
     * Returns the code of the version as written in the second line of the payload.
     *
     * @return the code in the payload, for example {@code 002}
     */
    public String code() {
      return code;
    }
  }

  /**
   * The eight character sets of EPC069-12, on the JVM and in GWT. UTF-8 is the default and can
   * represent every character; the ISO 8859 sets exist for existing payloads and scanners that
   * expect a single byte set. Characters a set cannot represent are rejected when building.
   */
  public enum CharacterSet {
    /** Code 1: UTF-8. */
    UTF_8("1", null),
    /** Code 2: ISO 8859-1 (Latin-1). */
    ISO_8859_1("2", Iso8859.LATIN_1),
    /** Code 3: ISO 8859-2 (Latin-2, Central European). */
    ISO_8859_2("3", Iso8859.ISO_8859_2),
    /** Code 4: ISO 8859-4 (Latin-4, Baltic). */
    ISO_8859_4("4", Iso8859.ISO_8859_4),
    /** Code 5: ISO 8859-5 (Cyrillic). */
    ISO_8859_5("5", Iso8859.ISO_8859_5),
    /** Code 6: ISO 8859-7 (Greek). */
    ISO_8859_7("6", Iso8859.ISO_8859_7),
    /** Code 7: ISO 8859-10 (Latin-6, Nordic). */
    ISO_8859_10("7", Iso8859.ISO_8859_10),
    /** Code 8: ISO 8859-15 (Latin-9). */
    ISO_8859_15("8", Iso8859.ISO_8859_15);

    private final String code;
    // characters of the bytes 0xA0 to 0xFF, \uFFFF where the set has no character; null for UTF-8
    private final String upperHalf;

    CharacterSet(String code,
                 String upperHalf) {
      this.code      = code;
      this.upperHalf = upperHalf;
    }

    /**
     * Returns the code of the character set as written in the third line of the payload.
     *
     * @return the code in the payload, for example {@code 1}
     */
    public String code() {
      return code;
    }

    /** @return the number of bytes of the text in this character set, -1 if it cannot be represented */
    int bytes(String text) {
      if (upperHalf != null) {
        for (int i = 0; i < text.length(); i++) {
          char c = text.charAt(i);
          // 0x00 to 0x9F are the same in all ISO 8859 sets and in Unicode
          if (c >= 0xA0 && (c == '\uFFFF' || upperHalf.indexOf(c) < 0)) {
            return -1;
          }
        }
        return text.length();
      }
      int bytes = 0;
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c < 0x80) {
          bytes++;
        } else if (c < 0x800) {
          bytes += 2;
        } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
          bytes += 4;
          i++;
        } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
          return -1;
        } else {
          bytes += 3;
        }
      }
      return bytes;
    }
  }


  /** Upper halves (bytes 0xA0 to 0xFF) of the ISO 8859 sets, generated from the Unicode mapping tables. */
  private static final class Iso8859 {

    static final String LATIN_1 = latin1();

    static final String ISO_8859_2 = "\u00A0\u0104\u02D8\u0141\u00A4\u013D\u015A\u00A7\u00A8\u0160\u015E\u0164\u0179\u00AD\u017D\u017B" +
                                                 "\u00B0\u0105\u02DB\u0142\u00B4\u013E\u015B\u02C7\u00B8\u0161\u015F\u0165\u017A\u02DD\u017E\u017C" +
                                                 "\u0154\u00C1\u00C2\u0102\u00C4\u0139\u0106\u00C7\u010C\u00C9\u0118\u00CB\u011A\u00CD\u00CE\u010E" +
                                                 "\u0110\u0143\u0147\u00D3\u00D4\u0150\u00D6\u00D7\u0158\u016E\u00DA\u0170\u00DC\u00DD\u0162\u00DF" +
                                                 "\u0155\u00E1\u00E2\u0103\u00E4\u013A\u0107\u00E7\u010D\u00E9\u0119\u00EB\u011B\u00ED\u00EE\u010F" +
                                                 "\u0111\u0144\u0148\u00F3\u00F4\u0151\u00F6\u00F7\u0159\u016F\u00FA\u0171\u00FC\u00FD\u0163\u02D9";
    static final String ISO_8859_4 = "\u00A0\u0104\u0138\u0156\u00A4\u0128\u013B\u00A7\u00A8\u0160\u0112\u0122\u0166\u00AD\u017D\u00AF" +
                                                 "\u00B0\u0105\u02DB\u0157\u00B4\u0129\u013C\u02C7\u00B8\u0161\u0113\u0123\u0167\u014A\u017E\u014B" +
                                                 "\u0100\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u012E\u010C\u00C9\u0118\u00CB\u0116\u00CD\u00CE\u012A" +
                                                 "\u0110\u0145\u014C\u0136\u00D4\u00D5\u00D6\u00D7\u00D8\u0172\u00DA\u00DB\u00DC\u0168\u016A\u00DF" +
                                                 "\u0101\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u012F\u010D\u00E9\u0119\u00EB\u0117\u00ED\u00EE\u012B" +
                                                 "\u0111\u0146\u014D\u0137\u00F4\u00F5\u00F6\u00F7\u00F8\u0173\u00FA\u00FB\u00FC\u0169\u016B\u02D9";
    static final String ISO_8859_5 = "\u00A0\u0401\u0402\u0403\u0404\u0405\u0406\u0407\u0408\u0409\u040A\u040B\u040C\u00AD\u040E\u040F" +
                                                 "\u0410\u0411\u0412\u0413\u0414\u0415\u0416\u0417\u0418\u0419\u041A\u041B\u041C\u041D\u041E\u041F" +
                                                 "\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042A\u042B\u042C\u042D\u042E\u042F" +
                                                 "\u0430\u0431\u0432\u0433\u0434\u0435\u0436\u0437\u0438\u0439\u043A\u043B\u043C\u043D\u043E\u043F" +
                                                 "\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044A\u044B\u044C\u044D\u044E\u044F" +
                                                 "\u2116\u0451\u0452\u0453\u0454\u0455\u0456\u0457\u0458\u0459\u045A\u045B\u045C\u00A7\u045E\u045F";
    static final String ISO_8859_7 = "\u00A0\u2018\u2019\u00A3\u20AC\u20AF\u00A6\u00A7\u00A8\u00A9\u037A\u00AB\u00AC\u00AD\uFFFF\u2015" +
                                                 "\u00B0\u00B1\u00B2\u00B3\u0384\u0385\u0386\u00B7\u0388\u0389\u038A\u00BB\u038C\u00BD\u038E\u038F" +
                                                 "\u0390\u0391\u0392\u0393\u0394\u0395\u0396\u0397\u0398\u0399\u039A\u039B\u039C\u039D\u039E\u039F" +
                                                 "\u03A0\u03A1\uFFFF\u03A3\u03A4\u03A5\u03A6\u03A7\u03A8\u03A9\u03AA\u03AB\u03AC\u03AD\u03AE\u03AF" +
                                                 "\u03B0\u03B1\u03B2\u03B3\u03B4\u03B5\u03B6\u03B7\u03B8\u03B9\u03BA\u03BB\u03BC\u03BD\u03BE\u03BF" +
                                                 "\u03C0\u03C1\u03C2\u03C3\u03C4\u03C5\u03C6\u03C7\u03C8\u03C9\u03CA\u03CB\u03CC\u03CD\u03CE\uFFFF";
    static final String ISO_8859_10 = "\u00A0\u0104\u0112\u0122\u012A\u0128\u0136\u00A7\u013B\u0110\u0160\u0166\u017D\u00AD\u016A\u014A" +
                                                  "\u00B0\u0105\u0113\u0123\u012B\u0129\u0137\u00B7\u013C\u0111\u0161\u0167\u017E\u2015\u016B\u014B" +
                                                  "\u0100\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u012E\u010C\u00C9\u0118\u00CB\u0116\u00CD\u00CE\u00CF" +
                                                  "\u00D0\u0145\u014C\u00D3\u00D4\u00D5\u00D6\u0168\u00D8\u0172\u00DA\u00DB\u00DC\u00DD\u00DE\u00DF" +
                                                  "\u0101\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u012F\u010D\u00E9\u0119\u00EB\u0117\u00ED\u00EE\u00EF" +
                                                  "\u00F0\u0146\u014D\u00F3\u00F4\u00F5\u00F6\u0169\u00F8\u0173\u00FA\u00FB\u00FC\u00FD\u00FE\u0138";
    static final String ISO_8859_15 = "\u00A0\u00A1\u00A2\u00A3\u20AC\u00A5\u0160\u00A7\u0161\u00A9\u00AA\u00AB\u00AC\u00AD\u00AE\u00AF" +
                                                  "\u00B0\u00B1\u00B2\u00B3\u017D\u00B5\u00B6\u00B7\u017E\u00B9\u00BA\u00BB\u0152\u0153\u0178\u00BF" +
                                                  "\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF" +
                                                  "\u00D0\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D7\u00D8\u00D9\u00DA\u00DB\u00DC\u00DD\u00DE\u00DF" +
                                                  "\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF" +
                                                  "\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6\u00F7\u00F8\u00F9\u00FA\u00FB\u00FC\u00FD\u00FE\u00FF";

    private static String latin1() {
      StringBuilder sb = new StringBuilder(96);
      for (char c = 0xA0; c <= 0xFF; c++) {
        sb.append(c);
      }
      return sb.toString();
    }

    private Iso8859() {
    }
  }

  // EU member states and the other EEA countries (Iceland, Liechtenstein, Norway)
  private static final Set<String> EEA = new HashSet<>(Arrays.asList("AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO",
                                                                    "SE", "SI", "SK"));

  private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

  private final String       text;
  private final Version      version;
  private final CharacterSet characterSet;
  private final int          bytes;

  private EpcQrPayload(String text,
                       Version version,
                       CharacterSet characterSet,
                       int bytes) {
    this.text         = text;
    this.version      = version;
    this.characterSet = characterSet;
    this.bytes        = bytes;
  }

  /**
   * Returns a new builder for a payload.
   *
   * @return a new builder, version 002 and UTF-8 by default
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the text to encode in the QR code, the lines joined by a line feed ({@code \n}).
   *
   * @return the text to encode in the QR code
   */
  public String text() {
    return text;
  }

  /**
   * Returns the lines of the payload, for example to show or test single fields. Empty lines
   * inside the payload stand for fields that are not set.
   *
   * @return a new fixed-size list without trailing empty lines; entries can be replaced without
   *         changing this payload, but cannot be added or removed
   */
  public List<String> lines() {
    return Arrays.asList(text.split("\n", -1));
  }

  /**
   * Returns the version the payload was built with.
   *
   * @return the payload version
   */
  public Version version() {
    return version;
  }

  /**
   * Returns the character set the payload was built with and declares in its third line. The QR
   * code must encode the text in this character set.
   *
   * @return the character set the QR code must encode the text with
   */
  public CharacterSet characterSet() {
    return characterSet;
  }

  /**
   * Returns the size of the text encoded in its character set.
   *
   * @return size of the text in bytes, at most {@value #MAX_BYTES}
   */
  public int byteLength() {
    return bytes;
  }

  /**
   * Two payloads are equal if their text is equal.
   *
   * @param o the object to compare with
   * @return {@code true} if {@code o} is a payload with the same text
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof EpcQrPayload other && text.equals(other.text);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this value
   */
  @Override
  public int hashCode() {
    return text.hashCode();
  }

  /**
   * Returns the text of the payload, the same as {@link #text()}.
   *
   * @return the text to encode in the QR code
   */
  @Override
  public String toString() {
    return text;
  }

  /**
   * Collects the data of a credit transfer; {@link #build()} checks it. A builder is mutable and
   * <b>not thread-safe</b>: create one per payload instead of sharing it.
   */
  public static final class Builder {

    private Version      version      = Version.V002;
    private CharacterSet characterSet = CharacterSet.UTF_8;
    private Bic          bic;
    private String       beneficiaryName;
    private Iban         iban;
    private BigDecimal   amount;
    private String       purpose;
    private String       reference;
    private String       text;
    private String       information;

    private Builder() {
    }

    /**
     * Sets the payload version.
     *
     * @param version payload version, {@link Version#V002} by default
     * @return this builder
     * @throws NullPointerException if {@code version} is {@code null}
     */
    public Builder version(Version version) {
      this.version = Objects.requireNonNull(version, "version");
      return this;
    }

    /**
     * Sets the character set of the payload. All texts must be encodable in it.
     *
     * @param characterSet character set, {@link CharacterSet#UTF_8} by default
     * @return this builder
     * @throws NullPointerException if {@code characterSet} is {@code null}
     */
    public Builder characterSet(CharacterSet characterSet) {
      this.characterSet = Objects.requireNonNull(characterSet, "characterSet");
      return this;
    }

    /**
     * Sets the BIC of the beneficiary's bank.
     *
     * @param bic BIC of the beneficiary's bank; mandatory for version 001 and outside the EEA,
     *            {@code null} to leave it out
     * @return this builder
     */
    public Builder bic(Bic bic) {
      this.bic = bic;
      return this;
    }

    /**
     * Sets the name of the beneficiary; mandatory and checked by {@link #build()}.
     * Text is preserved exactly: whitespace is not trimmed and character limits count Unicode
     * code points. A {@code null} or empty name fails when building.
     *
     * @param beneficiaryName name of the beneficiary, at most 70 characters, without line breaks
     * @return this builder
     */
    public Builder beneficiaryName(String beneficiaryName) {
      this.beneficiaryName = beneficiaryName;
      return this;
    }

    /**
     * Sets the account of the beneficiary; mandatory. A {@code null} value clears the account
     * and fails when {@link #build()} is called.
     *
     * @param iban account of the beneficiary, of a SEPA country
     * @return this builder
     */
    public Builder iban(Iban iban) {
      this.iban = iban;
      return this;
    }

    /**
     * Sets the amount to transfer. Trailing zeros are removed before checking decimal places and
     * formatting, so {@code 1.000} is accepted and becomes {@code EUR1}. Validation is deferred to
     * {@link #build()}.
     *
     * @param amount amount in euro, 0.01 to 999999999.99 with at most two decimals, or {@code null}
     *               to let the payer enter it
     * @return this builder
     */
    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    /**
     * Sets the purpose of the credit transfer; optional.
     *
     * @param purpose purpose code (ISO 20022 ExternalPurpose1Code), 1 to 4 letters or digits, for
     *                example {@code CHAR} for a charity payment; {@code null} to leave it out
     * @return this builder
     */
    public Builder purpose(String purpose) {
      this.purpose = purpose;
      return this;
    }

    /**
     * Sets a structured remittance reference; optional. Length and absence of line breaks are
     * checked by {@link #build()}; reference syntax and reference check digits are not validated.
     *
     * @param reference structured creditor reference (for example ISO 11649 RF reference), at most
     *                  35 characters; excludes {@link #remittanceText(String)}; {@code null} to
     *                  leave it out
     * @return this builder
     */
    public Builder remittanceReference(String reference) {
      this.reference = reference;
      return this;
    }

    /**
     * Sets an unstructured remittance text; optional.
     *
     * @param text unstructured remittance information, at most 140 characters, without line breaks;
     *             excludes {@link #remittanceReference(String)}; {@code null} to leave it out
     * @return this builder
     */
    public Builder remittanceText(String text) {
      this.text = text;
      return this;
    }

    /**
     * Sets a hint for the payer that banking apps may show; optional and not transferred with the
     * payment.
     *
     * @param information hint for the payer (beneficiary to originator information), at most 70
     *                    characters, without line breaks; {@code null} to leave it out
     * @return this builder
     */
    public Builder information(String information) {
      this.information = information;
      return this;
    }

    /**
     * Checks the data against EPC069-12 and builds the payload. The builder is not changed and can
     * be used again. Text fields are preserved without trimming; optional {@code null} and empty
     * strings are omitted. Length limits count Unicode code points, while the total payload limit
     * counts encoded bytes. This checks the supported payload constraints; it does not verify
     * account existence, BIC-to-IBAN correspondence or structured reference check digits.
     *
     * @return the payload
     * @throws IllegalArgumentException if the data violates EPC069-12, for example a missing
     *                                  mandatory field, a value that is too long or contains a line
     *                                  break, an amount out of range, both remittance fields set,
     *                                  a missing BIC, characters not representable in the selected
     *                                  character set (including unpaired UTF-16 surrogates for UTF-8),
     *                                  or a payload over {@value EpcQrPayload#MAX_BYTES} bytes
     */
    public EpcQrPayload build() {
      if (iban == null) {
        throw new IllegalArgumentException("iban is mandatory");
      }
      if (!iban.isSepa()) {
        throw new IllegalArgumentException("iban must belong to a SEPA country, " + iban.countryCode() + " is not one");
      }
      check("beneficiaryName", beneficiaryName, 70, true);
      check("purpose", purpose, 4, false);
      check("remittanceReference", reference, 35, false);
      check("remittanceText", text, 140, false);
      check("information", information, 70, false);
      if (present(purpose) && !purpose.matches("[A-Za-z0-9]{1,4}")) {
        throw new IllegalArgumentException("purpose must have 1 to 4 letters or digits");
      }
      if (present(reference) && present(text)) {
        throw new IllegalArgumentException("remittanceReference and remittanceText exclude each other");
      }
      if (bic == null && version == Version.V001) {
        throw new IllegalArgumentException("bic is mandatory in version 001");
      }
      if (bic == null && !EEA.contains(iban.countryCode())) {
        throw new IllegalArgumentException("bic is mandatory for a beneficiary outside the EEA");
      }
      String[] lines = { "BCD", version.code(), characterSet.code(), "SCT", bic == null ? "" : bic.toString(), beneficiaryName, iban.toString(), formatAmount(), value(purpose),
                         value(reference), value(text), value(information) };
      int last = lines.length - 1;
      while (lines[last].isEmpty()) {
        last--;
      }
      String payload = String.join("\n", Arrays.copyOf(lines, last + 1));
      int    bytes   = characterSet.bytes(payload);
      if (bytes < 0) {
        throw new IllegalArgumentException("the payload contains characters that " + characterSet + " cannot encode");
      }
      if (bytes > MAX_BYTES) {
        throw new IllegalArgumentException("the payload must not exceed " + MAX_BYTES + " bytes, but has " + bytes);
      }
      return new EpcQrPayload(payload, version, characterSet, bytes);
    }

    private String formatAmount() {
      if (amount == null) {
        return "";
      }
      if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
        throw new IllegalArgumentException("amount must be between 0.01 and 999999999.99 EUR");
      }
      BigDecimal normalized = amount.stripTrailingZeros();
      if (normalized.scale() > 2) {
        throw new IllegalArgumentException("amount must have at most two decimals");
      }
      return "EUR" + normalized.toPlainString();
    }

    private static void check(String name,
                              String value,
                              int maxLength,
                              boolean mandatory) {
      if (!present(value)) {
        if (mandatory) {
          throw new IllegalArgumentException(name + " is mandatory");
        }
        return;
      }
      if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
        throw new IllegalArgumentException(name + " must not contain line breaks");
      }
      if (value.codePointCount(0, value.length()) > maxLength) {
        throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
      }
    }

    private static boolean present(String value) {
      return value != null && !value.isEmpty();
    }

    private static String value(String value) {
      return value == null ? "" : value;
    }
  }
}

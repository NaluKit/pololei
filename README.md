<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/pololei-banner-dark.svg">
    <img src="docs/branding/pololei-banner.svg" alt="Pololei" width="480">
  </picture>
</p>

# pololei

[![License](https://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Join the chat at https://gitter.im/Nalukit42/Lobby](https://badges.gitter.im/Nalukit42/Lobby.svg)](https://gitter.im/Nalukit42/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nalukit/pololei.svg?colorB=44cc11)](https://central.sonatype.com/artifact/io.github.nalukit/pololei)
[![Build](https://github.com/NaluKit/pololei/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/NaluKit/pololei/actions/workflows/build.yaml)

IBAN and BIC validation for Java and GWT: parsing, check digits, national check digits, BBAN fields,
formatting, IBAN creation and EPC QR code (GiroCode) payloads.

- [Versioned API reference and reproducible quickstart](docs/api/1.0.0-rc01.md)

pololei is the successor of [iban4g](https://github.com/nalukit/iban4g): a new, smaller API, country data
generated from the SWIFT IBAN registry, and one code base for the JVM and GWT.

## The name

*Pololei* (pronounced *poh-loh-LAY*) is Hawaiian and means *correct*, *right* or *accurate*,
literally *straight*. It fits a library whose job is to tell whether an IBAN or a BIC is correct. Like
[Nalu](https://github.com/nalukit/nalu) (Hawaiian for *wave*) and the other NaluKit projects, pololei takes its
name from the Hawaiian language.

## Modules

| Module        | Use it for                                                                   |
|---------------|------------------------------------------------------------------------------|
| `pololei`     | Java applications: IBAN, BBAN, BIC, check digits, country data               |
| `pololei-gwt` | GWT applications: the pololei sources, the GWT module and GWT-RPC serializers |
| `pololei-epc` | EPC QR code (GiroCode) payloads for SEPA credit transfers, Java and GWT       |
| `pololei-creditor` | SEPA Creditor Identifier for direct debits and mandates, Java and GWT   |

pololei has no runtime dependencies. `pololei-gwt` needs `gwt-user` (provided by every GWT application).

## Requirements

- Java 21 or newer (the build is tested with Java 21 and 25).
- For GWT: GWT 2.13 (pololei is built and tested with GWT 2.13.1).

## Getting started

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>pololei</artifactId>
  <version>${pololei.version}</version>
</dependency>

<!-- optional: EPC QR code payloads -->
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>pololei-epc</artifactId>
  <version>${pololei.version}</version>
</dependency>

<!-- optional: SEPA Creditor Identifier -->
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>pololei-creditor</artifactId>
  <version>${pololei.version}</version>
</dependency>
```

For GWT, use `pololei-gwt` instead of `pololei` and inherit the module; for EPC QR codes or Creditor Identifiers
add `pololei-epc` or `pololei-creditor` and inherit its module as well:

```xml
<inherits name="io.github.nalukit.pololei.Pololei"/>
<inherits name="io.github.nalukit.pololei.epc.PololeiEpc"/>
<inherits name="io.github.nalukit.pololei.creditor.PololeiCreditor"/>
```

On the module path, `pololei` is the automatic module `io.github.nalukit.pololei`, `pololei-epc` is
`io.github.nalukit.pololei.epc` and `pololei-creditor` is `io.github.nalukit.pololei.creditor` (both
`requires io.github.nalukit.pololei;`). `pololei-gwt` is meant for the classpath.

## Packages

| Package                                | Content                                                                                          |
|----------------------------------------|--------------------------------------------------------------------------------------------------|
| `io.github.nalukit.pololei`            | the API: `Iban`, `IbanBuilder`, `IbanValidator`, `Bban`, `IbanFormat`, `Bic`, `ValidationResult` |
| `io.github.nalukit.pololei.error`      | `IbanError`, `BicError`, `InvalidIbanException`, `InvalidBicException`                           |
| `io.github.nalukit.pololei.checkdigit` | `IbanCheckDigits` (ISO 7064 MOD 97-10)                                                           |
| `io.github.nalukit.pololei.national`   | national check digit rules: `NationalChecks`, `NationalCheck`                                    |
| `io.github.nalukit.pololei.registry`   | country data from the SWIFT IBAN registry: `IbanRegistry`, `IbanCountry`, `BbanLayout`, ...      |
| `io.github.nalukit.pololei.epc`        | `EpcQrPayload` (module `pololei-epc`)                                                            |
| `io.github.nalukit.pololei.creditor`   | `CreditorIdentifier`, `CreditorIdentifierError` (module `pololei-creditor`)                      |
| `io.github.nalukit.pololei.internal`   | helpers, not part of the API                                                                     |

## IBAN

```java
// parse: whitespace and lower case letters are accepted
Iban iban = Iban.parse("DE89 3704 0044 0532 0130 00");

iban.toString();            // DE89370400440532013000 (electronic format)
iban.toPrintFormat();       // DE89 3704 0044 0532 0130 00
iban.countryCode();         // DE
iban.checkDigits();         // 89
iban.bban();                // 370400440532013000
iban.bankIdentifier();      // Optional[37040044]
iban.branchIdentifier();    // Optional.empty
iban.accountNumber();       // 0532013000 (all BBAN characters outside bank and branch identifier)
iban.field(BbanField.ACCOUNT_NUMBER);   // Optional[0532013000] (the national account number)
iban.isSepa();              // true
iban.country();             // the registry definition of DE

// validate without exceptions, e.g. for form validation
ValidationResult<Iban, IbanError> result = Iban.validate(input);
if (!result.isValid()) {
  IbanError error   = result.error().orElseThrow();    // e.g. INVALID_CHECK_DIGITS
  String    message = result.message().orElseThrow();  // e.g. "check digits 88 are not valid"
}

Iban.isValid(input);                                  // boolean
Iban.tryParse(input);                                 // Optional<Iban>
Iban.parse(input, IbanFormat.PRINT);                  // input must be exactly in print format
```

`Iban.parse` throws an `InvalidIbanException` (an `IllegalArgumentException`) for invalid input; `validate`,
`isValid` and `tryParse` never throw and accept `null`.

Besides the error and the English message, an invalid result carries details for localized messages or
for highlighting the input field:

```java
ValidationResult<Iban, IbanError> result = Iban.validate("DE89 3704 0044 0532 013X 00");
result.error();             // Optional[INVALID_BBAN]
result.position();          // OptionalInt[20]  (1-based, counted without whitespace)
result.expectedType();      // Optional[DIGITS]
Iban.validate("DE8937040044053201300").expectedLength();   // OptionalInt[22], actualLength() 21
```

`InvalidIbanException.result()` and `InvalidBicException.result()` give access to the same details.

Errors are reported in this order: `EMPTY`, `INVALID_FORMAT` (only with an explicit format),
`INVALID_CHARACTER`, `INVALID_COUNTRY_CODE`, `UNSUPPORTED_COUNTRY`, `INVALID_LENGTH`, `INVALID_BBAN`,
`INVALID_CHECK_DIGITS`, and with national checks `INVALID_NATIONAL_CHECK_DIGITS` or `NATIONAL_CHECK_NOT_SUPPORTED`.
`UNDEFINED_FIELD` is only reported by field validation and the builder. Messages never contain the complete
input, at most a single character, the country code or the check digits, so they can be logged without leaking
account numbers.

### National check digits

Many countries protect the BBAN with national check digits, for example the French RIB key. An `IbanValidator`
checks them in addition to the IBAN check digits:

```java
IbanValidator validator = IbanValidator.standard()
                                       .nationalCheck(NationalCheckMode.IF_SUPPORTED);
validator.isValid("FR1420041010050500013M02606");      // true
validator.isValid("FR8420041010050500013M02607");      // false: wrong RIB key, correct IBAN check digits
Iban.isValid("FR8420041010050500013M02607");           // true: Iban checks the international rules only
validator.validate(input).nationalCheck();             // PASSED, FAILED, NOT_SUPPORTED or NOT_PERFORMED
```

| Mode           | Countries with a rule      | Countries without a rule                               |
|----------------|----------------------------|--------------------------------------------------------|
| `OFF`          | not checked (the default)  | not checked                                            |
| `IF_SUPPORTED` | checked                    | valid, `nationalCheck()` is `NOT_SUPPORTED`            |
| `REQUIRED`     | checked                    | invalid with `NATIONAL_CHECK_NOT_SUPPORTED`            |

Rules exist for BA, BE, ES, FI, FR, IT, MC, ME, MK, NO, PT, RS, SI, SK, SM and TN (`NationalChecks.all()`); see
[docs/national-checks.md](docs/national-checks.md) for algorithms, sources and deliberate gaps (for example the
Netherlands). A rule that cannot judge a BBAN, for example letters where the algorithm needs digits, is treated
like a missing rule.

Own rules are added per validator. A rule replaces the built-in rule of its country or adds one, only for that
validator; there is no global registration, so other validators and GWT code are not affected:

```java
IbanValidator validator = IbanValidator.standard()
                                       .nationalCheck(NationalCheckMode.REQUIRED)
                                       .nationalRule(new MyRule());     // implements NationalCheck
validator.findNationalRule("NL");                                       // the rule this validator applies
```

The rule must be immutable and thread-safe, and translatable by GWT if it is used there. `OFF` ignores it;
`IF_SUPPORTED` and `REQUIRED` report it like a built-in rule (`rule()` is its `id()`). `IbanBuilder.nationalCheckDigits(true)`
keeps using the built-in rules.

An `IbanValidator` can also require an exact format; it is immutable, so configure it once and share it:

```java
IbanValidator strict = IbanValidator.standard()
                                    .format(IbanFormat.ELECTRONIC)
                                    .nationalCheck(NationalCheckMode.REQUIRED);
strict.parse(input);        // throws InvalidIbanException
```

### BBAN fields

`accountNumber()` returns everything outside the registry's bank and branch identifier. The national parts of
the BBAN are available as fields:

```java
Iban iban = Iban.parse("IT60X0542811101000000123456");
iban.accountNumber();                          // X000000123456
iban.field(BbanField.ACCOUNT_NUMBER);          // Optional[000000123456]
iban.field(BbanField.NATIONAL_CHECK_DIGITS);   // Optional[X] (the CIN)
iban.fields();                                 // all fields in BBAN order, the CIN first
```

See [docs/bban-fields.md](docs/bban-fields.md) for the fields of each country and their sources.

### Validating a BBAN or a single field

`Bban` validates a BBAN or one of its fields without building an IBAN, for example to check form fields one by
one. Positions count the characters of what was validated; add 4 to a BBAN position to get the IBAN position.

```java
Bban.validate("GB", "NWBK60161331926819");                          // valid: NWBK60161331926819
Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "37040044");    // valid
Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "3704004X");    // INVALID_BBAN at position 8
Bban.validateField("DE", BbanField.BRANCH_IDENTIFIER, "1");         // UNDEFINED_FIELD
```

National check digits are only checked for their structure here; use an `IbanValidator` to verify them.

### Creating IBANs

```java
Iban.of("DE", "370400440532013000");                  // DE89370400440532013000, calculates the check digits

Iban.builder()
    .countryCode("GB")
    .bankIdentifier("NWBK")
    .branchIdentifier("601613")
    .accountNumber("31926819")                         // fills all remaining BBAN positions
    .build();                                          // GB29NWBK60161331926819

Iban.builder()
    .countryCode("AT")
    .bankIdentifier("1904")
    .padBankIdentifier(true)                           // 01904; also padBranchIdentifier, padAccountNumber
    .accountNumber("234573201")
    .padAccountNumber(true)                            // 00234573201
    .build();                                          // AT850190400234573201

Iban.builder()
    .countryCode("FR")
    .bankIdentifier("20041")
    .branchIdentifier("01005")
    .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
    .nationalCheckDigits(true)                         // calculates the RIB key
    .build();                                          // FR1420041010050500013M02606

Iban.random(new Random(42));                           // test data, random country
Iban.random("AT", new Random(42));                     // test data, given country
Iban.builder()
    .countryCode("FR")
    .bankIdentifier("20041")                           // kept
    .nationalCheckDigits(true)                         // valid RIB key
    .buildRandom(new Random(42));                      // the other fields at random

IbanCheckDigits.calculate("DE", "370400440532013000"); // "89"
```

The BBAN is described in one of three ways, which cannot be mixed: the complete `bban(...)`, the registry parts
(`bankIdentifier`, `branchIdentifier` and `accountNumber` for all remaining positions), or the national
`field(...)`s. `build()` throws an `InvalidIbanException` if a part is missing or does not fit, and an
`IllegalStateException` if the BBAN is described in more than one way. `Iban.random(...)` does not calculate
national check digits; use `buildRandom` with `nationalCheckDigits(true)` for that.

## BIC

```java
Bic bic = Bic.parse("DEUTDEFF500");
bic.institutionCode();      // DEUT
bic.countryCode();          // DE
bic.locationCode();         // FF
bic.branchCode();           // Optional[500]
bic.isPrimaryOffice();      // false (true without branch code or with XXX)
bic.primaryOffice();        // DEUTDEFF
bic.isTestBic();            // false (true if the location code ends with 0)

Bic.validate(input);        // ValidationResult<Bic, BicError>
Bic.isValid(input);         // boolean
Bic.tryParse(input);        // Optional<Bic>
```

A BIC has 8 or 11 letters or digits, and characters 5-6 must be a known ISO 3166-1 country code (including XK
for Kosovo). Following ISO 9362:2022, the first four characters may contain digits. Whether a BIC is actually
registered with SWIFT cannot be checked offline and is not checked.

## EPC QR code

`pololei-epc` creates the text of an EPC QR code (EPC069-12, GiroCode) for a SEPA credit transfer; render it
with any QR code library, using error correction level M and the character set of the payload.

```java
EpcQrPayload payload = EpcQrPayload.builder()
                                   .beneficiaryName("Red Cross of Belgium")
                                   .iban(Iban.parse("BE72000000001616"))
                                   .bic(Bic.parse("BPOTBEB1"))
                                   .amount(new BigDecimal("1.00"))
                                   .remittanceText("Urgency fund")
                                   .build();
payload.text();          // the text to encode in the QR code
payload.lines();         // [BCD, 002, 1, SCT, BPOTBEB1, Red Cross of Belgium, BE72000000001616, EUR1, , , Urgency fund]
payload.byteLength();    // 80, at most 331
```

| Builder method                  | Rule (EPC069-12)                                                                          |
|---------------------------------|-------------------------------------------------------------------------------------------|
| `version(...)`                  | `V002` (default) or `V001`                                                                |
| `characterSet(...)`             | `UTF_8` (default) or one of the ISO 8859 sets of the guidelines: `ISO_8859_1`, `_2`, `_4`, `_5`, `_7`, `_10`, `_15` |
| `bic(...)`                      | mandatory for version 001 and for a beneficiary outside the EEA (for example CH or GB)   |
| `beneficiaryName(...)`          | mandatory, at most 70 characters                                                          |
| `iban(...)`                     | mandatory, of a SEPA country                                                              |
| `amount(...)`                   | optional, euro, 0.01 to 999999999.99, at most two decimals                                |
| `purpose(...)`                  | optional, 1 to 4 letters or digits                                                        |
| `remittanceReference(...)`      | optional, structured reference, at most 35 characters                                     |
| `remittanceText(...)`           | optional, at most 140 characters; excludes `remittanceReference`                          |
| `information(...)`              | optional, hint for the payer, at most 70 characters                                       |

Texts must not contain line breaks, and the payload must not exceed 331 bytes in its character set. `build()`
throws an `IllegalArgumentException` that names the field if a rule is violated.

## SEPA Creditor Identifier

`pololei-creditor` parses and validates SEPA Creditor Identifiers (CI, EPC262-08), the identifier of a creditor in
SEPA direct debits and mandates. It is a type of its own, not an IBAN.

```java
CreditorIdentifier ci = CreditorIdentifier.parse("DE98ZZZ09999999999");   // test CI of the Bundesbank
ci.countryCode();          // DE
ci.checkDigits();          // 98
ci.businessCode();         // ZZZ (no business code)
ci.nationalIdentifier();   // 09999999999
CreditorIdentifier.validate("DE97ZZZ09999999999");   // Optional[INVALID_CHECK_DIGITS]
CreditorIdentifier.of("DE", null, "09999999999");     // calculates the check digits: DE98ZZZ09999999999
ci.withBusinessCode("A1B").isSameCreditor(ci);        // true: the business code is not part of the check digits
```

| Rule | Checked |
|------|---------|
| structure | country code (2 letters), check digits (2 digits), business code (3 letters or digits), national identifier, 8 to 35 characters |
| country | every SEPA country (`IbanCountry.isSepa()`); others are `UNSUPPORTED_COUNTRY` |
| check digits | ISO 7064 MOD 97-10 over national identifier and country code, without the business code |
| national format | length 18 for Germany (Deutsche Bundesbank); other countries only the general rules (`hasNationalFormat(code)`) |

Input may contain whitespace and lower case letters. `parse` throws `InvalidCreditorIdentifierException` with a
`CreditorIdentifierError` and, where it applies, the position; `tryParse`, `isValid` and `validate` never throw.
A valid identifier is formally correct, not necessarily issued: Creditor Identifiers are assigned by national
authorities, and `of(...)` only calculates check digits, for example for test data.

## Country data

`IbanRegistry` provides the IBAN definition of every country that uses IBAN (89 countries in release 100): BBAN
structure, length, position of bank and branch identifier, BBAN layout, SEPA membership and an example IBAN.

```java
IbanCountry germany = IbanRegistry.find("DE").orElseThrow();   // country codes are upper case
germany.bbanStructure();    // 8!n10!n
germany.ibanLength();       // 22
germany.bbanLayout();       // [BANK_IDENTIFIER 1-8, ACCOUNT_NUMBER 9-18]
germany.isSepa();           // true
IbanRegistry.isSupported("US");   // false
IbanRegistry.countries();   // all countries, ordered by country code
IbanRegistry.source();      // SWIFT IBAN Registry Release 100
```

The data is generated Java code (`IbanRegistryData`), so it works in GWT without resources. To update it,
download the *IBAN Registry (TXT)* from
[SWIFT](https://www.swift.com/standards/data-standards/iban-international-bank-account-number) and run:

```bash
scripts/registry/generate-registry.sh ~/Downloads/iban-registry.txt "SWIFT IBAN Registry Release 100"
```

The generator checks every example IBAN against its structure and check digits before writing the file.
[docs/registry-audit.md](docs/registry-audit.md) compares the data with iban4j and records every decision.

## Serialization

`Iban`, `Bic` and `ValidationResult` are serializable with Java serialization and GWT-RPC. A deserialized IBAN
or BIC is always validated again, so a manipulated stream or request cannot create an invalid instance:

- Java serialization: `readResolve` parses the value again and throws `InvalidIbanException` / `InvalidBicException`.
- GWT-RPC: `pololei-gwt` contains `Iban_CustomFieldSerializer`, `Bic_CustomFieldSerializer` and
  `ValidationResult_CustomFieldSerializer`. They run on the client and on the server and fail with a
  `SerializationException`. On the client, GWT reports this as an `IncompatibleRemoteServiceException` whose
  cause is that `SerializationException`. `ValidationResult` is transferred as strings and numbers, so the
  serialization policy needs no entries for the error enums.
- For a GWT-RPC server, put `pololei-gwt` next to `pololei` on its classpath, so it finds the serializers.

## Thread safety

pololei keeps no mutable shared state and can be used by any number of threads and users at the same time, for
example in a servlet container or a GWT-RPC server:

- `Iban`, `Bic`, `ValidationResult`, `IbanValidator`, `EpcQrPayload` and the country data (`IbanCountry`,
  `BbanLayout`, `BbanStructure`, ...) are immutable. Keep one configured `IbanValidator` in a static field or a
  singleton and use it from every request.
- The static methods (`Iban.parse`, `Bic.validate`, `Bban.validate`, `IbanRegistry.find`, `NationalChecks.find`,
  `IbanCheckDigits.calculate`, ...) are stateless; the registry and the national rules are loaded once and never
  change.
- `IbanBuilder` and `EpcQrPayload.Builder` are mutable and not thread-safe: create a new builder for every IBAN or
  payload instead of sharing one.
- For random IBANs generated in parallel, pass `ThreadLocalRandom.current()` or one `Random` per thread.

## Building

```bash
mvn verify
```

`pololei-gwt` compiles the sources with the GWT compiler and runs `PololeiGwtSuite` as JavaScript, including real
GWT-RPC calls against a test servlet. CI builds and tests with Java 21 and 25.

The sources of the `pololei`, `pololei-epc` and `pololei-creditor` modules are shared with GWT, which supports the Java 17 language
level and its own JRE emulation: use Java 21 syntax such as pattern matching for `switch` or record patterns,
and APIs such as `List.getFirst()`, only in tests and scripts. The GWT build catches violations.

## Migrating

- from iban4g: [docs/migration-iban4g.md](docs/migration-iban4g.md)
- from iban4j: [docs/migration-iban4j.md](docs/migration-iban4j.md)

Changes between versions: [CHANGELOG.md](CHANGELOG.md). Reporting security issues: [SECURITY.md](SECURITY.md).

## License

The project source code is licensed under the [Apache License 2.0](LICENSE)
(`SPDX-License-Identifier: Apache-2.0`). The parent POM declares this license, checks Java and shell source
headers during `validate`, and configures `META-INF/LICENSE` for every module JAR. A header check verifies
license markings; it does not establish the origin or redistribution rights of third-party material.

pololei is inspired by [iban4j](https://github.com/arturmkrtchyan/iban4j) by Artur Mkrtchyan, which
[iban4g](https://github.com/NaluKit/iban4g) was ported from. Both upstream projects use Apache-2.0.
Retain applicable upstream copyright and attribution notices when copying or adapting their code, and include
any applicable upstream `NOTICE` with distributions as required by that license.

### Dependencies

| Component | Use in pololei | License information |
| --- | --- | --- |
| `pololei`, `pololei-epc`, `pololei-creditor` | JVM library modules | No third-party compile/runtime dependencies; the latter two depend on `pololei`. |
| GWT 2.13.1 (`gwt-user`) | `provided` dependency of `pololei-gwt`, including its RPC serializers | GWT code uses Apache-2.0; bundled components and transitive dependencies have their own terms. See [GWT licensing](https://www.gwtproject.org/terms.html). |
| JUnit Jupiter 5.14.4 / JUnit 4.13.2 | Tests only | EPL-2.0 / EPL-1.0. |
| GWT compiler and test dependencies (`gwt-dev`) | Build/tests only | Multiple licenses, including Apache-2.0, EPL, MPL, BSD and MIT; these are not all covered by the project license. |

**Build/test license exception:** `gwt-dev:2.13.1` transitively resolves `colt:colt:1.2.0`. Its
[`hep.aida.*` license](https://dst.lbl.gov/ACSSoftware/colt/license.html) adds a prohibition on military use to
the LGPL terms. This is a field-of-use restriction, so this dependency must not be described as unrestricted
open-source software. It is not a runtime dependency of the three JVM library modules. Review this restriction
before using or redistributing the build/test toolchain; any replacement or exclusion needs a separate GWT
compatibility check. Other GWT transitive dependencies include `javax.servlet-api` under CDDL/GPLv2 with the
Classpath Exception. Preserve the applicable licenses and notices when distributing dependencies.

### External data and specifications

- **SWIFT IBAN Registry:** `IbanRegistryData.java` contains country definitions and example IBANs generated from
  the registry labelled release 100; derived values also occur in test fixtures and the registry audit.
  The original registry download is not included. SWIFT's publications and
  [website terms](https://www.swift.com/about-us/legal/online-services/swift-website-terms-use) reserve rights;
  public download availability is not an Apache-2.0 grant. No permission establishing unrestricted redistribution
  of the extracted dataset was found in this review. Clarify the applicable permission or other legal basis for
  distributing the generated data and examples before release. Omitting the original download alone does not
  settle that question. See [registry-audit.md](docs/registry-audit.md) for provenance and reproducibility.
- **EPC and ISO specifications:** the EPC QR payload and Creditor Identifier modules implement formats described
  by EPC069-12 and EPC262-08; IBAN/BIC and check digits refer to ISO standards. These specifications are external
  references, not Apache-licensed project documents. Respect the publishers' terms when reproducing their text,
  tables or examples; see the [EPC disclaimer](https://www.europeanpaymentscouncil.eu/disclaimer).
- **Test vectors:** [national-checks.md](docs/national-checks.md) records public account examples and vectors from
  [python-stdnum 2.2](https://arthurdejong.org/python-stdnum/) (LGPL-2.1-or-later). The iban4j comparison fixture
  records its upstream commit and getter output. These are test data sources, not library dependencies.
  Their provenance does not by itself grant permission to relicense copied code or documentation as Apache-2.0;
  review any substantial reused material under its original terms.

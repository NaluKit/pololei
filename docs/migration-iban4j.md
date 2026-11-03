# Migrating from iban4j to pololei

This guide maps the public API of [iban4j](https://github.com/arturmkrtchyan/iban4j) to pololei. It was checked
against iban4j commit
[`b2f6be5`](https://github.com/arturmkrtchyan/iban4j/tree/b2f6be595cdd30cd722ef9d1706da0d100ba8eff)
(21 October 2026): every public method of `Iban`, `Iban.Builder`, `IbanUtil`, `Bic`, `BicUtil`,
`ValidationConfig`, `IbanValidator`, `BbanStructure`, `EpcQrCode` and the exceptions is listed below with its
counterpart, a replacement or the reason why it is missing. `Iban4jMigrationTest` runs the examples of this guide;
`Iban4jFieldComparisonTest` compares the national fields of both libraries for 82 of the 83 common country codes
(BR is left out because iban4j rejects its registry example, see [Fields that differ](#fields-that-differ)) and fails
on any difference, or any changed value of a difference, not listed under [Fields that differ](#fields-that-differ).

## Before you start

| Topic | iban4j | pololei |
|-------|--------|---------|
| Java | 11+ | **21+** (GWT: the `pololei-gwt` module, Java 17 language level) |
| Artifacts | `org.iban4j:iban4j` | `io.github.nalukit:pololei`, plus `pololei-epc` for EPC QR codes, `pololei-gwt` for GWT |
| JPMS module | `org.iban4j` (module-info) | `io.github.nalukit.pololei` and `io.github.nalukit.pololei.epc` (`Automatic-Module-Name`) |
| Country codes | enum `CountryCode` (ISO 3166, ~250 values) | `String`, upper case; `IbanRegistry.find(code)` for IBAN countries |
| Country data | own table, 106 prefixes | SWIFT IBAN registry release 100, 89 prefixes; see [registry-audit.md](registry-audit.md) |
| Errors | exceptions (`IbanFormatException`, `InvalidCheckDigitException`, `UnsupportedCountryException`) | `ValidationResult` without exceptions, or `InvalidIbanException` with an `IbanError` |
| Input | electronic format, upper case (`valueOf`) | `parse` accepts electronic and print format, whitespace and lower case; `parse(s, IbanFormat.ELECTRONIC/PRINT)` is strict |
| Account number | national account number (`getAccountNumber`) | `accountNumber()`: everything outside bank and branch; national account number with `field(ACCOUNT_NUMBER)`, see [bban-fields.md](bban-fields.md) |
| National check digits | `ValidationConfig`, 15 countries | `IbanValidator` with `NationalCheckMode`, 16 countries (no NL, plus MC and SM), see [national-checks.md](national-checks.md) |

## Iban

| iban4j | pololei |
|--------|---------|
| `Iban.valueOf(s)` | `Iban.parse(s)` |
| `Iban.valueOf(s, IbanFormat.Default)` | `Iban.parse(s, IbanFormat.PRINT)` |
| `Iban.valueOf(s, IbanFormat.None)` | `Iban.parse(s, IbanFormat.ELECTRONIC)` |
| `iban.toString()` | `iban.toString()` |
| `iban.toFormattedString()` | `iban.toPrintFormat()` |
| `iban.getCountryCode()` (`CountryCode`) | `iban.countryCode()` (`String`) |
| `iban.getCheckDigit()` | `iban.checkDigits()` |
| `iban.getBban()` | `iban.bban()` |
| `iban.getBankCode()` | `iban.bankIdentifier()` (`Optional`); registry positions, see [Fields that differ](#fields-that-differ) |
| `iban.getBranchCode()` | `iban.branchIdentifier()` (`Optional`, registry); for FR and PT `iban.field(BRANCH_IDENTIFIER)`; exceptions see [Fields that differ](#fields-that-differ) |
| `iban.getAccountNumber()` | `iban.field(ACCOUNT_NUMBER)` (`Optional`); exceptions BY, FI see [Fields that differ](#fields-that-differ) |
| `iban.getNationalCheckDigit()` | `iban.field(NATIONAL_CHECK_DIGITS)`; for TR `iban.field(RESERVED)` |
| `iban.getAccountType()` | `iban.field(ACCOUNT_TYPE)`; for SC `iban.field(CURRENCY)` |
| `iban.getOwnerAccountType()` | `iban.field(OWNER_ACCOUNT_TYPE)` |
| `iban.getIdentificationNumber()` | `iban.field(IDENTIFICATION_NUMBER)` |
| `IbanUtil.getBankCodeExt(s)` | `iban.field(BANK_IDENTIFIER_EXTENSION)`; in pololei the extension is part of the bank identifier |
| `Iban.random()`, `Iban.random(Random)` | `Iban.random(random)` (a `Random` is always passed, for reproducible data) |
| `Iban.random(CountryCode.AT)` | `Iban.random("AT", random)` |
| `equals`, `hashCode` | `equals`, `hashCode` |
| Java serialization | Java serialization and GWT-RPC; a deserialized IBAN is validated again |

## Iban.Builder

| iban4j | pololei |
|--------|---------|
| `new Iban.Builder()` | `Iban.builder()` |
| `new Iban.Builder(random)` | `Iban.builder()` ... `.buildRandom(random)` |
| `.countryCode(CountryCode.FR)` | `.countryCode("FR")` |
| `.bankCode(s)` | `.bankIdentifier(s)` |
| `.bankCodeExt(s)` | `.field(BANK_IDENTIFIER_EXTENSION, s)` (must match the bank identifier, if both are set) |
| `.branchCode(s)` | `.branchIdentifier(s)` |
| `.accountNumber(s)` | `.field(ACCOUNT_NUMBER, s)` — **not** `.accountNumber(s)`, which sets all positions outside bank and branch |
| `.nationalCheckDigit(s)` | `.field(NATIONAL_CHECK_DIGITS, s)`, or `.nationalCheckDigits(true)` to calculate them; for TR `.field(RESERVED, s)`; for AL and PL the check digit is part of the branch or bank identifier and a separate value must agree with it |
| `.accountType(s)`, `.ownerAccountType(s)`, `.identificationNumber(s)` | `.field(ACCOUNT_TYPE / OWNER_ACCOUNT_TYPE / IDENTIFICATION_NUMBER, s)`; for SC `.field(CURRENCY, s)` |
| `.leftPadding(true)` | `.padAccountNumber(true)`, `.padBankIdentifier(true)`, `.padBranchIdentifier(true)` (separately) |
| `.paddingCharacter(c)` | `.paddingCharacter(c)`; it must be allowed at the padded positions |
| `.build()` | `.build()`; always validates |
| `.build(false)` | no counterpart: pololei never creates an invalid `Iban` |
| `.buildRandom()` | `.buildRandom(random)`; with `.nationalCheckDigits(true)` the national check digits are valid too |

## Validation

| iban4j | pololei |
|--------|---------|
| `IbanUtil.validate(s)` (throws) | `Iban.parse(s)` (throws `InvalidIbanException`) or `Iban.validate(s)` (`ValidationResult`) |
| `IbanUtil.validate(s, IbanFormat.Default)` | `Iban.validate(s, IbanFormat.PRINT)` |
| `IbanUtil.isValid(s)` | `Iban.isValid(s)` |
| `IbanUtil.isValid(s, format)` | `Iban.validate(s, format).isValid()` |
| `IbanUtil.isValid(s, config)`, `IbanUtil.isValidWithCountryRules(s)` | `IbanValidator.standard().nationalCheck(NationalCheckMode.IF_SUPPORTED).isValid(s)` |
| `IbanUtil.validateWithCountryRules(s)` | `IbanValidator.standard().nationalCheck(...).parse(s)` |
| `ValidationConfig.builder().enableCountryRules(true).build()` | `NationalCheckMode.IF_SUPPORTED` (countries without rule pass) or `REQUIRED` (they fail) |
| `ValidationConfig.Builder.enableNationalCheckDigitValidation(true)` | same as above |
| `IbanValidator.builder().enableCountryRules().build()` | `IbanValidator.standard().nationalCheck(NationalCheckMode.IF_SUPPORTED)` |
| `CountryRulesRegistry.register(...)` | `IbanValidator.standard().nationalCheck(...).nationalRule(rule)`; per validator instead of global, the rule implements `NationalCheck` |
| `CountryRulesRegistry.clear()` | not needed: start again from `IbanValidator.standard()`; built-in rules: `NationalChecks.find(code)`, rule of a validator: `validator.findNationalRule(code)` |
| `IbanUtil.validateBban(country, bban)` | `Bban.validate("XX", bban)` |
| `BbanStructure.validateBbanEntry(country, type, value)` | `Bban.validateField("XX", BbanField.X, value)` |
| `IbanUtil.calculateCheckDigit(s)` | `IbanCheckDigits.calculate(countryCode, bban)` |
| `IbanUtil.isSupportedCountry(cc)` | `IbanRegistry.isSupported("XX")` |
| `IbanUtil.getIbanLength(cc)` | `IbanRegistry.find("XX").orElseThrow().ibanLength()` |
| `IbanUtil.getCountryCode(s)`, `getCheckDigit`, `getBban`, `getBankCode`, `getAccountNumber`, `getCountryCodeAndCheckDigit` | parse once, then use the `Iban` getters |
| `IbanUtil.extractBbanEntry(country, bban, type)` | `Iban.of("XX", bban).field(BbanField.X)` |
| `IbanUtil.padLeft(...)` | no counterpart (helper) |
| `BbanStructure.forCountry(cc)` | `IbanRegistry.find("XX")`: `bbanStructure()` (registry notation) and `bbanLayout()` (fields) |
| `BbanStructure.supportedCountries()` | `IbanRegistry.countries()` |
| `BbanStructure.hasNationalCheckDigit(cc)` | `IbanRegistry.find("XX").orElseThrow().bbanLayout().contains(NATIONAL_CHECK_DIGITS)`; whether pololei can verify them: `NationalChecks.find("XX").isPresent()` |

### Exceptions and error codes

| iban4j | pololei `IbanError` |
|--------|---------------------|
| `IbanFormatException` `IBAN_NOT_NULL`, `IBAN_NOT_EMPTY` | `EMPTY` |
| `IBAN_FORMATTING` | `INVALID_FORMAT` (only with an explicit `IbanFormat`) |
| `IBAN_VALID_CHARACTERS` | `INVALID_CHARACTER` |
| `COUNTRY_CODE_TWO_LETTERS`, `COUNTRY_CODE_UPPER_CASE_LETTERS`, `COUNTRY_CODE_NOT_NULL` | `INVALID_COUNTRY_CODE` (lower case input is accepted by `parse`) |
| `COUNTRY_CODE_EXISTS`, `UnsupportedCountryException` | `UNSUPPORTED_COUNTRY` |
| `BBAN_LENGTH` | `INVALID_LENGTH` (`expectedLength()`, `actualLength()`) |
| `BBAN_ONLY_DIGITS`, `BBAN_ONLY_UPPER_CASE_LETTERS`, `BBAN_ONLY_DIGITS_OR_LETTERS`, `BBAN_INVALID_ENTRY_TYPE` | `INVALID_BBAN` (`position()`, `expectedType()`) |
| `CHECK_DIGIT_ONLY_DIGITS`, `CHECK_DIGIT_TWO_DIGITS`, `InvalidCheckDigitException` | `INVALID_CHECK_DIGITS` |
| `IllegalArgumentException` "Country-specific rules check failed" | `INVALID_NATIONAL_CHECK_DIGITS` (`rule()`, `position()`..`lastPosition()`) |
| `BANK_CODE_NOT_NULL`, `ACCOUNT_NUMBER_NOT_NULL`, ... (builder) | `INVALID_LENGTH` with expected length and 0 |
| – | `NATIONAL_CHECK_NOT_SUPPORTED`, `UNDEFINED_FIELD` |
| `getActual()`, `getExpected()`, `getInvalidCharacter()` | `actualLength()`, `expectedLength()`, `expectedType()`, `position()` |

`InvalidIbanException` extends `IllegalArgumentException`, like iban4j's exceptions; `result()` gives all details.
Messages never contain the input.

## BIC

| iban4j | pololei |
|--------|---------|
| `Bic.valueOf(s)` | `Bic.parse(s)` |
| `BicUtil.validate(s)` | `Bic.validate(s)` or `Bic.parse(s)` |
| `bic.getBankCode()`, `BicUtil.getBankCode(s)` | `bic.institutionCode()` |
| `bic.getCountryCode()`, `BicUtil.getCountryCode(s)` | `bic.countryCode()` (`String`) |
| `bic.getLocationCode()`, `BicUtil.getLocationCode(s)` | `bic.locationCode()` |
| `bic.getBranchCode()`, `BicUtil.getBranchCode(s)` | `bic.branchCode()` (`Optional`) |
| `BicUtil.hasBranchCode(s)` | `bic.branchCode().isPresent()` |
| `BicFormatException` | `InvalidBicException` with `BicError` |

pololei follows ISO 9362:2022: digits are allowed in the first four characters.

## EPC QR code

Add `io.github.nalukit:pololei-epc` (GWT: inherit `io.github.nalukit.pololei.epc.PololeiEpc`).

| iban4j | pololei |
|--------|---------|
| `EpcQrCode.builder()` | `EpcQrPayload.builder()` |
| `.version(Version.V1/V2)` | `.version(EpcQrPayload.Version.V001/V002)` |
| `.characterSet(CharacterSet.UTF_8)` | `.characterSet(EpcQrPayload.CharacterSet.UTF_8)`; all eight sets (UTF-8, ISO 8859-1, -2, -4, -5, -7, -10, -15), on the JVM and in GWT |
| `.bic(bic)`, `.beneficiaryName(s)`, `.iban(iban)` | same |
| `.amount(BigDecimal)` | `.amount(BigDecimal)` |
| `.amount(BigDecimal, Currency)` | no counterpart: EPC069-12 only knows EUR |
| `.purposeCode(s)` | `.purpose(s)` |
| `.remittanceReference(s)`, `.remittanceText(s)` | same |
| `.beneficiaryToOriginatorInformation(s)` | `.information(s)` |
| `.build().getPayload()`, `.buildPayload()` | `.build().text()` |

Differences: pololei requires an IBAN of a SEPA country and reports the byte size (`byteLength()`).

## Fields that differ

Bank and branch identifier follow the SWIFT IBAN registry, and pololei names a position by its meaning. For these
countries a getter of iban4j and the corresponding pololei field return different values (registry examples):

| Country | iban4j | pololei | Why |
|---------|--------|---------|-----|
| AL | `getBranchCode()` `1100`, `getNationalCheckDigit()` `9` | `branchIdentifier()` `11009`, `field(NATIONAL_CHECK_DIGITS)` `9` | the registry branch identifier contains the check digit; the field lies within it |
| PL | `getBankCode()` `109`, `getBranchCode()` `0101`, `getNationalCheckDigit()` `4` | `bankIdentifier()` `10901014`, no branch, `field(NATIONAL_CHECK_DIGITS)` `4` | the registry bank identifier is the 8 digit sort code; the check digit lies within it |
| SI | `getBankCode()` `26`, `getBranchCode()` `330` | `bankIdentifier()` `26330`, no branch | registry bank identifier of 5 digits |
| FI | `getBankCode()` `123456`, `getAccountNumber()` `0000078` | `bankIdentifier()` `123`, `field(ACCOUNT_NUMBER)` `4560000078` | registry bank identifier of 3 digits |
| BY | `getBranchCode()` `3600`, `getAccountNumber()` `900000002Z00AB00` | no branch, `field(ACCOUNT_NUMBER)` `3600900000002Z00AB00` | the registry defines no branch identifier |
| SC | `getBankCode()` `SSCB`, `getAccountType()` `USD` | `bankIdentifier()` `SSCB11`, `field(CURRENCY)` `USD` | the extension is part of the registry bank identifier; the last three characters are a currency |
| TR | `getNationalCheckDigit()` `0` | `field(RESERVED)` `0`, no national check digits | position 6 is reserved by the registry (currently `0`), not a check digit |

```java
Iban poland = Iban.parse("PL61109010140000071219812874");
poland.bankIdentifier();                          // Optional[10901014]
poland.field(BbanField.NATIONAL_CHECK_DIGITS);    // Optional[4], position 8 of the bank identifier
poland.field(BbanField.ACCOUNT_NUMBER);           // Optional[0000071219812874]

Iban turkiye = Iban.parse("TR330006100519786457841326");
turkiye.field(BbanField.RESERVED);                // Optional[0]
turkiye.field(BbanField.ACCOUNT_NUMBER);          // Optional[0519786457841326]

Iban.parse("SC18SSCB11010000000000001497USD")
    .field(BbanField.CURRENCY);                   // Optional[USD]
```

BR is not compared: iban4j rejects the registry example, whose bank identifier contains letters.

## Behaviour that changes

- **Prefixes**: GF, GP, MQ, RE, PF, TF, YT, NC, BL, MF, PM, WF, GG, IM, JE, AX use the IBAN prefix of FR, GB or FI
  and are rejected; AO, CV, GA, IR, MA, MZ, SN have no registry entry. FK, HN, MN, NI, SO, YE are new.
- **Structures**: AE, BR, MU, PK, TR and UA follow the registry, see [registry-audit.md](registry-audit.md).
- **Bank and branch**: positions follow the registry; for example the Polish bank identifier has 8 digits, the
  Slovenian 5, the Finnish 3.
- **Netherlands**: no eleven test, because new Dutch account numbers need not pass it.
- **Check digits** 00, 01 and 99 are rejected.
- **Builder**: `build()` never creates an invalid IBAN; there is no `build(false)`.

## Example

```java
// iban4j
Iban iban = new Iban.Builder()
                .countryCode(CountryCode.IT)
                .nationalCheckDigit("X")
                .bankCode("05428")
                .branchCode("11101")
                .accountNumber("000000123456")
                .build();
String account = iban.getAccountNumber();                 // 000000123456
IbanUtil.isValid(input, ValidationConfig.builder().enableCountryRules(true).build());

// pololei
Iban iban = Iban.builder()
                .countryCode("IT")
                .bankIdentifier("05428")
                .branchIdentifier("11101")
                .field(BbanField.ACCOUNT_NUMBER, "000000123456")
                .nationalCheckDigits(true)                // calculates the CIN X
                .build();
String account = iban.field(BbanField.ACCOUNT_NUMBER)
                     .orElseThrow();                      // 000000123456
IbanValidator.standard()
             .nationalCheck(NationalCheckMode.IF_SUPPORTED)
             .isValid(input);
```

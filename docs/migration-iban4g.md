# Migrating from iban4g to pololei

## Dependencies

| iban4g                                 | pololei                                     |
|----------------------------------------|---------------------------------------------|
| `io.github.nalukit:iban4g` (Java)      | `io.github.nalukit:pololei`                 |
| `io.github.nalukit:iban4g` (GWT/J2CL)  | `io.github.nalukit:pololei-gwt`             |
| `<inherits name="...iban4g.Iban4g"/>`  | `<inherits name="io.github.nalukit.pololei.Pololei"/>` |
| package `io.github.nalukit.iban4g.shared` | package `io.github.nalukit.pololei`, errors and exceptions in `io.github.nalukit.pololei.error` |

pololei needs Java 21 or newer.

## API

| iban4g                                              | pololei                                                   |
|-----------------------------------------------------|-----------------------------------------------------------|
| `Iban.valueOf(s)`                                   | `Iban.parse(s)` (also accepts print format and lower case) |
| `Iban.valueOf(s, IbanFormat.Default)`               | `Iban.parse(s, IbanFormat.PRINT)`                         |
| `IbanUtil.validate(s)` (throws)                     | `Iban.validate(s)` returns a `ValidationResult`           |
| `try { IbanUtil.validate(s) } catch ...`           | `Iban.isValid(s)`                                         |
| `IbanUtil.isSupportedCountry(cc)`                   | `IbanRegistry.isSupported(cc)`                            |
| `IbanUtil.calculateCheckDigit(s)`                   | `IbanCheckDigits.calculate(countryCode, bban)`            |
| `iban.toFormattedString()`                         | `iban.toPrintFormat()`                                    |
| `iban.getCountryCode()` (enum `CountryCode`)        | `iban.countryCode()` (`String`)                           |
| `iban.getCheckDigit()`                              | `iban.checkDigits()`                                      |
| `iban.getBban()`                                    | `iban.bban()`                                             |
| `iban.getBankCode()`                                | `iban.bankIdentifier()` (`Optional`)                      |
| `iban.getBranchCode()`                              | `iban.branchIdentifier()` (`Optional`)                    |
| `iban.getAccountNumber()`, `getNationalCheckDigit()`, `getAccountType()`, ... | `iban.field(BbanField.ACCOUNT_NUMBER)`, `field(NATIONAL_CHECK_DIGITS)`, `field(ACCOUNT_TYPE)`, ... (see [bban-fields.md](bban-fields.md)); `iban.accountNumber()` returns all BBAN characters outside bank and branch identifier |
| `Iban.Builder().countryCode(CountryCode.AT)...build()` | `Iban.builder().countryCode("AT")...build()`           |
| `Iban.random(CountryCode.AT)`, `.buildRandom()`    | `Iban.random("AT", random)` (the `Random` makes test data reproducible) |
| `Bic.valueOf(s)`, `BicUtil.validate(s)`             | `Bic.parse(s)`, `Bic.validate(s)`                         |
| `bic.getBankCode()`                                 | `bic.institutionCode()`                                   |
| `bic.getLocationCode()`, `getBranchCode()`          | `bic.locationCode()`, `bic.branchCode()` (`Optional`)     |
| `IbanFormatException`, `InvalidCheckDigitException`, `UnsupportedCountryException` | `InvalidIbanException` with `error()` (`IbanError`) |
| `BicFormatException`                                | `InvalidBicException` with `error()` (`BicError`)         |
| `BbanStructureProvider`                             | `IbanRegistry`                                            |

## Behaviour changes

- New: `IbanBuilder.padAccountNumber(true)` left pads a short account number with zeros.
- **Country data follows the SWIFT IBAN registry.** Territories that SWIFT lists under another country
  (for example GF, GP, RE, AX, GG, JE or IM, which use FR, FI or GB) and codes outside the registry (CV, GA, MG, IR) are no
  longer accepted as IBAN prefixes.
- The registry only defines bank and branch identifier. National check digits, account types and similar
  parts are part of `accountNumber()`; read them with `field(...)`.
- National check digits are checked with `IbanValidator` (see [national-checks.md](national-checks.md)).
- Check digits `00`, `01` and `99` are rejected.
- A BIC may contain digits in its first four characters (ISO 9362:2022), so `DEU1DEFF` is now valid.
- Error messages no longer contain the input.

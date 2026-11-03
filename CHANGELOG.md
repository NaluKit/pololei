# Changelog

## [1.0.0-rc01] - 2026-11-03

Closes the functional gaps found by comparing pololei with iban4j. Remaining differences are
deliberate and documented in [docs/migration-iban4j.md](docs/migration-iban4j.md) (*Fields that differ*, *Behaviour
that changes*) and [docs/registry-audit.md](docs/registry-audit.md). All API changes are additive; existing calls
keep their results unless listed under *Changed behaviour*.

### Added

- National check digits: `IbanValidator` with `NationalCheckMode` (`OFF`, `IF_SUPPORTED`, `REQUIRED`), rules for
  BA, BE, ES, FI, FR, IT, MC, ME, MK, NO, PT, RS, SI, SK, SM and TN in `io.github.nalukit.pololei.national`
  ([docs/national-checks.md](docs/national-checks.md)).
- `ValidationResult.nationalCheck()`, `rule()` and `lastPosition()`; `IbanError.INVALID_NATIONAL_CHECK_DIGITS`,
  `NATIONAL_CHECK_NOT_SUPPORTED` and `UNDEFINED_FIELD`.
- BBAN fields: `BbanField`, `BbanLayout`, `IbanCountry.bbanLayout()`, `Iban.field(BbanField)`, `Iban.fields()`,
  `IbanBuilder.field(BbanField, String)` ([docs/bban-fields.md](docs/bban-fields.md)).
- `Bban.validate(country, bban)` and `Bban.validateField(country, field, value)`.
- `IbanBuilder.nationalCheckDigits(true)` calculates national check digits; `IbanBuilder.buildRandom(Random)` fills
  missing parts at random and keeps the given ones.
- Padding of bank and branch identifier (`padBankIdentifier`, `padBranchIdentifier`) and a configurable
  `paddingCharacter`.
- New module `pololei-epc`: EPC QR code (GiroCode) payloads, versions 001 and 002, UTF-8 and ISO 8859-1, JVM and GWT.
- Stable JPMS names: `Automatic-Module-Name` `io.github.nalukit.pololei` and `io.github.nalukit.pololei.epc`.
- `CharacterType.randomChar(Random)` is public.
- National BBAN layouts for AL, DJ, EE, FO, HU, MR, PL, SE, TL, TR and XK: national check digits and the national
  account number are separate fields. For AL and PL `NATIONAL_CHECK_DIGITS` lies within the registry branch or bank
  identifier; `BbanField.RESERVED` is the reserved position 6 of Turkiye.
- Own national check digit rules per validator: `IbanValidator.nationalRule(NationalCheck)` and
  `findNationalRule(String)`; they replace or add a rule for one validator only.
- `pololei-epc`: the character sets ISO 8859-2, -4, -5, -7, -10 and -15 (codes 3 to 8), on the JVM and in GWT.
- New module `pololei-creditor`: SEPA Creditor Identifier (EPC262-08) with parsing, structure and check digit
  validation for all SEPA countries, the national length for Germany, `of(...)` to calculate check digits and
  `isSameCreditor(...)` that ignores the business code; JVM and GWT (iban4j issue #27).
- `Iban4jFieldComparisonTest` compares the national fields with iban4j for 82 of the 83 common country codes (BR:
  iban4j rejects the registry example); expected differences are checked with their exact values.
- CI: `mvn verify` (including GWT and module path tests) on Java 21 and 25 for pushes and pull requests, and a
  release dry run that builds sources and JavaDoc without signing. `scripts/release/check-release.sh` checks version,
  changelog section and repository links of a release; the release job runs it, the dry run runs it for release
  versions and skips it for development snapshots. `check-release-test.sh` tests the script.
- POM, README and SECURITY.md link consistently to `NaluKit/pololei`; the SCM connection has the valid
  `scm:git:` form.
- Documentation: registry audit against iban4j ([docs/registry-audit.md](docs/registry-audit.md)) and a migration
  guide for iban4j users ([docs/migration-iban4j.md](docs/migration-iban4j.md)).

- Documentation of thread safety (README and JavaDoc) and a concurrency test.
- README revised: sections for `Bban` validation, `IbanValidator` options, BIC rules, EPC builder rules,
  serialization and requirements; `SECURITY.md` for pololei (it described another project before).

### Changed behaviour

- `Iban.field(ACCOUNT_NUMBER)` of AL, DJ, EE, FO, HU, MR, PL, SE, TL, TR and XK no longer contains the national
  check digits (or, for TR, the reserved position); they are separate fields. `Iban.accountNumber()` is unchanged.

- `NationalChecks.all()` is ordered by country code as documented (MC came after ME).
- `ValidationResult` has final fields, so a result is safely published to other threads even without
  synchronization. GWT-RPC now uses `ValidationResult_CustomFieldSerializer` from `pololei-gwt`, which transfers
  strings and numbers only and validates a transferred `Iban` or `Bic` again; the serialization policy of an
  application no longer needs entries for the error enums. Java serialization stays compatible.

- `IbanBuilder.bankIdentifier(...)` and `branchIdentifier(...)` without `accountNumber(...)` now use the national
  layout: for France and Portugal a branch identifier is accepted (positions 6-10 and 5-8); before, building failed.
- `IbanBuilder.padAccountNumber(true)` with `accountNumber(...)` rejects a short value where the remaining positions
  contain more than the account number (for example IT, ES, FR): padding used to shift the national check digits or
  the branch code. Use `field(BbanField.ACCOUNT_NUMBER, ...)` there. A value of the full length is accepted as before.
- Padding no longer turns an empty account number into zeros; the builder reports the missing value.
- A builder without any BBAN part reports the first missing field in BBAN order.

### Unchanged on purpose

- `Iban.accountNumber()`, `Iban.bankIdentifier()` and `Iban.branchIdentifier()` keep following the SWIFT IBAN
  registry.
- `Iban.random(...)` does not calculate national check digits; use `Iban.builder().nationalCheckDigits(true).buildRandom(random)`.
- Country data stays SWIFT IBAN registry release 100, prefixes of iban4j outside the registry are rejected.

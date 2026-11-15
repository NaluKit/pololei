# Changelog

All notable changes to pololei are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Added

- Release evidence for audits: every module is published with its SBOM in CycloneDX format
  (`<module>-<version>-cyclonedx.json`); the build is reproducible (fixed archive timestamp, set
  to the commit time of the release); the release JARs carry a GitHub build provenance
  attestation; the GitHub release lists the JARs, SBOMs, `SHA256SUMS`, the OWASP
  Dependency-Check report of the release build, and the changes of the version.
  `scripts/release/collect-release-files.sh` collects these files, also to rebuild and compare a
  release. `SECURITY.md` describes how releases are built and how to verify them.
- Public identifier corpus `test-pololei-corpus` (profile `corpus`, part of every CI build, never
  published): 1131 IBANs, BICs and Creditor Identifiers from the unchanged, commit-pinned test files
  of python-stdnum, Apache Commons Validator, schwifty and sepa_king, with SHA-256 and license of
  every file, a reviewed expectation for every value and the reason for each of the 45 differences
  to the upstream statement. `scripts/fixtures.py` verifies and restores the files.
- The SWIFT IBAN registry file the country data is generated from is pinned by release and SHA-256
  in `scripts/registry/registry-source.properties`; `generate-registry.sh` rejects any other file
  and writes the SHA-256 to `IbanRegistryData`. `IbanRegistrySourceTest` keeps the pinned values,
  the generated class and `docs/registry-audit.md` in line.

### Changed

- Release process: `scripts/release/check-release.sh <version>` checks release version, Git tag,
  `pom.xml`, `SECURITY.md`, `CHANGELOG.md`, README and the versioned API reference
  `docs/api/<version>.md`; `--site <dir>` also checks the website and `--artifacts` builds the
  quickstart of the reference against the published artifacts. Pull requests against `main` run the
  static checks, so an inconsistent release is found before the merge. The deploy job of `dev` and
  `main` is one job; the GitHub release is created only after the deploy succeeded.
- CI: weekly build for new vulnerabilities in unchanged dependencies, dependency updates only in
  that build, Dependabot for GitHub Actions and Maven, least-privilege permissions, and the OWASP
  Dependency-Check version in the parent POM.
- `pololei-gwt` is packaged by the `maven-jar-plugin`, because `gwt:package-lib` ignores the fixed
  archive timestamp. The JAR has the same entries as before.
- README dependency snippets use the published release instead of a `${pololei.version}`
  placeholder.
- `generate-registry.sh` takes only the registry file; release and SHA-256 come from
  `scripts/registry/registry-source.properties`.
- Build plugins and test dependencies on the versions of the other NaluKit projects: JUnit 6.1.3,
  maven-compiler-plugin 3.16.0, maven-javadoc-plugin 3.12.0, flatten-maven-plugin 1.9.0,
  maven-resources-plugin 3.5.0; gwt-maven-plugin 1.3.0.

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
- Documentation: the [API reference for 1.0.0-rc01](docs/api/1.0.0-rc01.md) with a reproducible quickstart; every
  release gets its own reference in `docs/api/<version>.md`.

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

[Unreleased]: https://github.com/NaluKit/pololei/compare/1.0.0-rc01...HEAD
[1.0.0-rc01]: https://github.com/NaluKit/pololei/releases/tag/1.0.0-rc01

# Public identifier test corpus

Unchanged test files of eight public projects, pinned to a commit and retrieved on 2026-11-15: the
IBANs, BICs and SEPA Creditor Identifiers their authors state as valid or invalid, and their EPC QR
code test cases with the payload they expect or the input they reject. The corpus supplements the unit
tests, which are written against the specifications, with third-party cases: values found in the wild,
other formats of input, territories, deliberate errors, and other implementations of EPC069-12.

**Results: 1131 values from 7 files (580 IBANs, 537 BICs, 14 Creditor Identifiers). pololei agrees with the upstream statement for 1086 of them; 45 results are explained one by one.**

**EPC QR code: 67 cases from 4 files; pololei builds 39 payloads and rejects 28 inputs. It agrees with the upstream test for 58 cases; 9 results are explained one by one.**

`ManifestSummaryTest` compares these numbers with the expectations, so they cannot drift apart.
A passing corpus test confirms the documented baseline, including the differences. It does not
establish that every upstream statement is right, nor full conformance of pololei.

## Running the tests

From the repository root, using Java 21 and Maven:

```sh
mvn -Pcorpus -pl test-pololei-corpus -am test
```

With a populated Maven cache, add `-o` to run offline. The tests themselves do not download
anything. The profile is opt-in for local builds and part of every CI build; this module is
neither installed nor published.

Each run writes `target/corpus-report/results.tsv` with the actual result of every value.

## What is checked?

- `FixtureIntegrityTest`: SHA-256 of every retained file and license text, a commit-pinned source
  URL that names the upstream path, and no file in the corpus that is not listed.
- `ExpectationCoverageTest`: the values are taken from the unchanged files with the line range and
  pattern of their section in [sections.tsv](src/test/resources/corpus/sections.tsv), never copied by
  hand. Every taken value has exactly one expectation in
  [expectations.tsv](src/test/resources/corpus/expectations.tsv), at the same file and line and in
  the same order. A note is required exactly where the pololei result differs from the upstream
  statement, or where an IBAN the upstream project calls valid fails its national check digits.
- `PublicCorpusTest`: every value is validated with pololei and compared with its expectation:
  `Iban.validate`, `Bic.validate` or `CreditorIdentifier.validate` (the error, or `VALID`), and for
  IBANs also `IbanValidator` with `NationalCheckMode.IF_SUPPORTED` (`VALID/<status>` or the error).
  Fixing or changing a behavior therefore fails the test first and requires a deliberate update of
  the expectation.
- `EpcCaseIntegrityTest`: every EPC QR code case in
  [epc-cases.tsv](src/test/resources/corpus/epc-cases.tsv) names the line range of its upstream test;
  every input is backed by a literal fragment that must occur in that range, or at a named other line
  (for example a constant of the test file). A payload the upstream test states completely is read from
  the file (a Ruby string, a Go or JavaScript template literal); a note is required exactly where
  pololei differs from the upstream test.
- `EpcCorpusTest`: builds every case with `EpcQrPayload.Builder` and compares the payload byte for byte,
  or the reason of the rejection, with the reviewed result.
- `ManifestSummaryTest`: the numbers above.

The upstream statement of a section is one of `valid`, `invalid`, `experimental` (schwifty: a
country outside the IBAN registry, accepted only in an experimental mode) and `national-invalid`
(python-stdnum: valid by the international rules, invalid national check digits).

## Differences

Each difference is listed with its reason in the `note` column of
[expectations.tsv](src/test/resources/corpus/expectations.tsv). In groups:

| Values | Upstream | pololei | Reason |
| ---: | --- | --- | --- |
| 8 | sepa_king: valid Creditor Identifiers | `INVALID_CHECK_DIGITS` | sepa_king checks only the structure; the check digits, recalculated with ISO 7064 MOD 97-10 as in EPC262-08, are wrong. Only `DE98ZZZ09999999999` and `HU74111A12345676` are correct. |
| 14 | commons-validator: valid IBANs of territories (AX, BL, GF, GP, MF, MQ, NC, PF, PM, RE, TF, WF, YT) | `UNSUPPORTED_COUNTRY` | The SWIFT IBAN registry release 100 lists these territories under FR or FI; see [docs/registry-audit.md](../docs/registry-audit.md). |
| 1 | schwifty: valid IBAN of Senegal | `UNSUPPORTED_COUNTRY` | No registry entry; schwifty lists the same IBAN as experimental. |
| 2 | schwifty: experimental IBANs of HN and YE | valid | Part of registry release 100. |
| 11 | python-stdnum: valid IBANs with `-` or `.` | `INVALID_CHARACTER` | pololei accepts the electronic and the print format; stdnum removes these separators first. |
| 1 | python-stdnum: valid BIC `GEBA-BEBB` | `INVALID_LENGTH` | Same for a BIC. |
| 1 | python-stdnum: invalid Belgian IBAN | valid, national check fails | stdnum.be.iban includes the national check digits; pololei reports them with the national check. |
| 7 | commons-validator, sepa_king: invalid IBANs in lower case or with spaces | valid | pololei accepts lower case letters and spaces and normalizes them. |

Every IBAN the upstream projects call valid and pololei accepts also passes its national check digits
where pololei has a rule.

## EPC QR code cases

A case states what its upstream test states: the complete payload (`payload`), single assertions on
lines and byte length (`asserts`, segno checks lines one by one), or that the input is rejected
(`rejected`). The inputs are those of the upstream test, mapped to the builder: girocode's
`reference` and sepa-payment-qr-code's `reference` are the unstructured text, their
`creditor_reference` and `structuredReference` the structured reference; payme's `Remittance` is the
text in `New()` and the reference in `NewStructured()`. Where segno chooses the character set by
itself, the case takes it from the asserted line or length and says so in its description. segno's
cases for illegal encodings are not included: the builder takes the character set as a typed value.

Both examples of [EPC069-12 v3.0](https://europeanpaymentscouncil.eu/sites/default/files/kb/file/2022-09/EPC069-12%20v3.0%20Quick%20Response%20Code%20-%20Guidelines%20to%20Enable%20the%20Data%20Capture%20for%20the%20Initiation%20of%20an%20SCT_0.pdf),
section 2.3, are covered: example 1 completely by girocode (`girocode-1`) and with its 96 bytes in UTF-8
by segno, example 2 completely by girocode in UTF-8 (`girocode-3`) and with its 103 bytes in ISO 8859-1
by segno. pololei agrees with all of them. Like segno, pololei rejects a value of whitespace only in a
text field; `null` and an empty string mean "not set".

| Cases | Upstream | pololei | Reason |
| ---: | --- | --- | --- |
| 2 | payme: payload with trailing line feeds, IBAN with spaces, `EUR12.30` | payload without them | EPC069-12 section 2.2: the last populated element is not followed by a separator; the examples of section 2.3 show the IBAN without spaces and `EUR12.3`. |
| 2 | sepa-payment-qr-code: payload with structured reference, unstructured text left out | rejected | Section 2.2: only one of both may be populated; the library silently drops the text it was given. |
| 3 | segno: rejects a payload without remittance information | payload | Section 2.2 marks both remittance elements as optional. |
| 2 | segno: rejects the purposes `DE1` and `x` | payload | Section 2.2: the purpose has 1 to 4 alphanumeric characters. |

## Sources and licenses

| Source | Pinned commit | Files | Values | License |
| --- | --- | ---: | ---: | --- |
| [python-stdnum](https://github.com/arthurdejong/python-stdnum) | `7ddf2ed279bbd46c44c6661f3bdaaac29d3adf0c` | 3 | 841 | LGPL-2.1-or-later |
| [Apache Commons Validator](https://github.com/apache/commons-validator) | `8ca512b49d1ca58b61ebf11af82f0e714fb3be56` | 1 | 126 | Apache-2.0; NOTICE included |
| [schwifty](https://github.com/mdomke/schwifty) | `a9be09003d41397526201edb2999951720961441` | 2 | 137 | MIT |
| [sepa_king](https://github.com/salesking/sepa_king) | `41d561362e7af4bcd7da7bfad97e6e5e2d32f7bb` | 1 | 27 | MIT |
| [girocode](https://github.com/mtgrosser/girocode) | `79db005c563fb38e713cb281388e9b6b3b9676d0` | 1 | 5 cases | MIT |
| [payme](https://github.com/jovandeginste/payme) | `c9df800fc935c1a5dafd21aaa9c1da23c01fc441` | 2 | 3 cases | MIT |
| [sepa-payment-qr-code](https://github.com/derhuerst/sepa-payment-qr-code) | `62d7b9d4ad44c9e257dc9c7b45dcb00565bf1bd9` | 1 | 4 cases | ISC |
| [segno](https://github.com/heuer/segno) | `b11dc2913c22b22b3bc0a6efaa989904c44977bf` | 1 | 55 cases | BSD-3-Clause |

The original files are retained under `src/test/resources/corpus/<source>/`, preserving their
upstream paths: `tests/test_iban.doctest`, `tests/test_bic.doctest` and `tests/test_be_iban.doctest`
of python-stdnum, `IBANValidatorTest.java` of Commons Validator, `tests/test_iban.py` and
`tests/test_bic.py` of schwifty, `spec/validator_spec.rb` of sepa_king, `test/girocode_test.rb` of
girocode, `pkg/payment/payment_test.go` and `pkg/payment/output_test.go` (constants) of payme,
`test.js` of sepa-payment-qr-code, and `tests/test_helpers_epcqr.py` of segno. Only the sections listed
in `sections.tsv` and the cases of `epc-cases.tsv` are used; the rest of each file is code of the
upstream test and is not run.

[manifest.tsv](src/test/resources/corpus/manifest.tsv) records each file's commit-pinned download
URL, SHA-256 hash and license. License texts and the Commons Validator NOTICE are retained under
`corpus/licenses/` ([licenses.json](src/test/resources/corpus/licenses.json)). The LGPL continues to
apply to the retained python-stdnum files; they are not relicensed under the project license. Corpus
metadata and expectations were added here; the upstream bytes are unchanged.

## Verifying and restoring fixtures

```sh
python3 test-pololei-corpus/scripts/fixtures.py
python3 test-pololei-corpus/scripts/fixtures.py --restore
```

Without options, the script verifies the hashes of the retained files and license texts locally.
`--restore` downloads only missing or changed originals from the pinned GitHub URLs, verifies the
bytes before writing them, and leaves expectations unchanged. Updating upstream versions requires
deliberate changes to files, sources, hashes, sections and reviewed expectations; test runs never
generate new expected values.

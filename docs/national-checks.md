# National check digits

The IBAN check digits (ISO 7064 MOD 97-10) protect the whole IBAN. Many countries also have national check digits
inside the BBAN. pololei checks them on request:

```java
IbanValidator validator = IbanValidator.standard()
                                       .nationalCheck(NationalCheckMode.IF_SUPPORTED);
validator.isValid("FR1420041010050500013M02606");   // true
validator.isValid("FR8420041010050500013M02607");   // false: RIB key 07 is wrong, the IBAN check digits are fine
```

| Mode | Country with rule | Country without rule (or BBAN the rule cannot judge) |
|------|-------------------|-------------------------------------------------------|
| `OFF` (default, same as `Iban.validate`) | not checked, `NOT_PERFORMED` | `NOT_PERFORMED` |
| `IF_SUPPORTED` | `PASSED` or invalid with `INVALID_NATIONAL_CHECK_DIGITS` (`FAILED`) | valid, `NOT_SUPPORTED` |
| `REQUIRED` | as above | invalid with `NATIONAL_CHECK_NOT_SUPPORTED` |

`ValidationResult.nationalCheck()` returns the status. A failed check reports the rule (`rule()`, for example
`FR_RIB_KEY`) and the IBAN positions of the check digits (`position()` to `lastPosition()`); the message never
contains the input. The national check runs after all international checks, so its error comes last.

`IbanBuilder.nationalCheckDigits(true)` calculates the national check digits when building, also together with
`buildRandom(Random)`.

## Rules

Positions are BBAN positions (1-based). All rules work on the JVM and in GWT.

| Country | Rule | Check positions | Algorithm |
|---------|------|-----------------|-----------|
| BA | `BA_MOD97` | 15-16 | ISO 7064 MOD 97-10 over the BBAN: BBAN mod 97 = 1 |
| BE | `BE_MOD97` | 11-12 | first 10 digits mod 97; remainder 0 becomes 97 |
| ES | `ES_CCC` | 9-10 | two weighted mod 11 digits (weights 1, 2, 4, 8, 5, 10, 9, 7, 3, 6): over `00` + entidad + oficina and over the account number; 11 becomes 0, 10 becomes 1 |
| FI | `FI_LUHN` | 14 | Luhn over the first 13 digits (machine format) |
| FR | `FR_RIB_KEY` | 22-23 | 97 - ((bank, branch, account) × 100 mod 97); letters A/J=1, B/K/S=2, ... I/R/Z=9 |
| IT | `IT_CIN` | 1 | CIN: values of ABI, CAB and account number on odd and even positions, sum mod 26 as letter |
| MC | `MC_RIB_KEY` | 22-23 | as FR (Monaco uses the French RIB) |
| ME | `ME_MOD97` | 17-18 | ISO 7064 MOD 97-10 over the BBAN |
| MK | `MK_MOD97` | 14-15 | ISO 7064 MOD 97-10 over the BBAN, **digits only** (see below) |
| NO | `NO_MOD11` | 11 | weighted mod 11 (weights 5, 4, 3, 2, 7, 6, 5, 4, 3, 2), remainder 1 has no valid check digit; bank `0000` (postgiro): Luhn over the 7 digit account |
| PT | `PT_MOD97` | 20-21 | ISO 7064 MOD 97-10 over the BBAN (NIB check digits) |
| RS | `RS_MOD97` | 17-18 | ISO 7064 MOD 97-10 over the BBAN |
| SI | `SI_MOD97` | 14-15 | ISO 7064 MOD 97-10 over the BBAN |
| SK | `SK_MOD11` | 5-20 | weighted mod 11 = 0 over the prefix (weights 10, 5, 8, 4, 2, 1) and over the basic number (weights 6, 3, 7, 9, 10, 5, 8, 4, 2, 1) |
| SM | `SM_CIN` | 1 | as IT (San Marino uses the Italian BBAN) |
| TN | `TN_RIB_KEY` | 19-20 | 97 - ((bank, branch, account) × 100 mod 97) |

### Special cases

- **North Macedonia (MK)**: the registry allows letters in the account number (`10!c`), the MOD 97-10 rule is only
  defined for digits. A BBAN with letters gets `NOT_APPLICABLE`: valid with `NOT_SUPPORTED` in `IF_SUPPORTED`, invalid in
  `REQUIRED`. iban4j fails such IBANs instead. Random IBANs with national check digits contain digits only.
- **Norway (NO)**: a weighted sum with remainder 1 has no valid check digit; such account numbers are not issued, so
  every check digit fails and generation draws new values. Postgiro accounts (bank `0000`) use Luhn
  (python-stdnum, `stdnum.no.kontonr`); iban4j does not know this case.
- **Spain (ES)**: check digits 10 and 11 become 1 and 0.
- **Belgium (BE)**: remainder 0 becomes 97.
- **Slovakia (SK)**: there is no separate check digit field; both parts must be divisible by 11. Generation sets the
  last digit of each part and draws again if that would have to be 10.

### Countries deliberately without rule

- **Netherlands (NL)**: iban4j applies the eleven test (*elfproef*). Since 1 January 2020 Dutch banks may issue
  account numbers that do not pass it
  ([Betaalvereniging](https://www.betaalvereniging.nl/en/news/last-ten-digits-dutch-iban-not-separate-from-2020/)),
  so the rule would reject valid IBANs. pololei has no NL rule (`NOT_SUPPORTED`).
- Countries with national check digits whose algorithm is bank specific or not sufficiently documented (for example
  DE, HU, PL, AL, EE) have no rule. Their check digits are available as `BbanField.NATIONAL_CHECK_DIGITS` where the
  position is known (see [bban-fields.md](bban-fields.md)).

An application that needs a rule for one of these countries, or a different rule, adds its own `NationalCheck` to an
`IbanValidator` with `nationalRule(...)`; it replaces the built-in rule of the country for that validator only.

## Test data and sources

`NationalChecksTest` checks for every country the example of the SWIFT IBAN registry (release 100) and, except for
MC and SM, at least one IBAN from a second, independent source: published accounts of organisations and test vectors
of [python-stdnum](https://arthurdejong.org/python-stdnum/) 2.2. Every algorithm was additionally cross-checked
against an independent Python implementation written from the descriptions above. For negative cases the test
changes a national check digit and recalculates the IBAN check digits, so the IBAN stays internationally valid.

| Country | Second source |
|---------|---------------|
| BA | `BA391610000205360069`, account listing on xtransfer.com |
| BE | `BE29001510480764`, Rode Kruis-Vlaanderen SEPA mandate form (2024); `BE32123456789002`, python-stdnum `stdnum.be.iban` |
| ES | `ES5000491837542210249123`, donation account published by RTVC (2023); `1234 1234 16 1234567890`, python-stdnum `stdnum.es.ccc` |
| FI | `FI0557134520027557`, Suomen Punainen Risti, Turunmaan piiri |
| FR | `FR7610278012500001648634055`, OCOVAS donation form (CHRU Strasbourg) |
| IT | `IT38R0760103000000000900050`, `IT60P0200805255000104428939`, Italian Ministry of Foreign Affairs (esteri.it, 2016) |
| ME | `ME25510000000006234133`, python-stdnum `stdnum.me.iban` |
| MK | `MK07300701000001228`, Red Cross of the Republic of North Macedonia (ckrm.org.mk) |
| NO | `0000.4090403` (postgiro), python-stdnum `stdnum.no.kontonr` |
| PT | `PT50003500270008240223053`, Cruz Vermelha Portuguesa (Millennium bcp press release, 2019) |
| RS | `RS35205007080003980905`, Crveni krst Srbije (eurohoops.net, 2014) |
| SI | `SI56029220019831742`, Rdeči križ Slovenije (bizi.si) |
| SK | `SK5411000000002945456524`, Slovenská katolícka charita (teraz.sk); CZ test vector `034278-0727558021` of python-stdnum (same algorithm) |
| TN | `TN5905000000001508508829`, Croissant-Rouge tunisien (webdo.tn, 2023) |
| MC, SM | registry example only; the algorithm is the French and Italian one |

The accounts are only used as test vectors; whether they are still in use does not matter.

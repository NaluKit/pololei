# BBAN fields and the account number

## Decision: `accountNumber()` keeps its meaning, the national fields are added

*Status: decided for the next minor release; no incompatible change.*

`Iban.accountNumber()` returns, as documented since the first release, **all BBAN characters outside the bank and
branch identifier of the SWIFT IBAN registry**. For several countries that is more than the account number in the
national sense, because the registry does not describe national check digits, account types or, for France, the
branch code:

| IBAN | `accountNumber()` | national account number |
|------|-------------------|-------------------------|
| `IT60X0542811101000000123456` | `X000000123456` (CIN + account) | `000000123456` |
| `ES9121000418450200051332` | `450200051332` (check digits + account) | `0200051332` |
| `FR1420041010050500013M02606` | `010050500013M02606` (branch + account + RIB key) | `0500013M026` |

Changing `accountNumber()` would silently change the results of existing callers. Instead pololei adds an
unambiguous, additive API:

- `Iban.field(BbanField)` and `Iban.fields()` read the national fields;
- `IbanBuilder.field(BbanField, String)` sets them; `bankIdentifier(...)` and `branchIdentifier(...)` are shortcuts
  for the two identifier fields;
- `IbanCountry.bbanLayout()` describes position and length of every field;
- `Bban.validateField(...)` validates a single field.

Getter and builder input belong together and mean the same:

| Read | Write | Meaning |
|------|-------|---------|
| `iban.accountNumber()` | `builder.accountNumber(...)` | everything outside the registry's bank and branch identifier |
| `iban.field(ACCOUNT_NUMBER)` | `builder.field(ACCOUNT_NUMBER, ...)` | the national account number |
| `iban.field(NATIONAL_CHECK_DIGITS)` | `builder.field(NATIONAL_CHECK_DIGITS, ...)` or `nationalCheckDigits(true)` | national check digits, given or calculated |

`accountNumber(...)` cannot be combined with `field(...)` for other fields than bank and branch identifier.
`padAccountNumber(true)` pads `field(ACCOUNT_NUMBER, ...)`; it pads `accountNumber(...)` only where that is the
national account number: for IT or ES padding would shift the CIN or the check digits, so a short value is rejected
there with a hint to use the field. A field the country does not have is empty (`Optional.empty()`) and is rejected
by the builder with `UNDEFINED_FIELD`.

```java
Iban iban = Iban.parse("FR1420041010050500013M02606");
iban.field(BbanField.BRANCH_IDENTIFIER);       // Optional[01005]
iban.field(BbanField.ACCOUNT_NUMBER);          // Optional[0500013M026]
iban.field(BbanField.NATIONAL_CHECK_DIGITS);   // Optional[06]
iban.branchIdentifier();                       // Optional.empty: the registry defines no French branch

Iban.builder()
    .countryCode("FR")
    .bankIdentifier("20041")
    .branchIdentifier("01005")
    .field(BbanField.ACCOUNT_NUMBER, "0500013M026")
    .nationalCheckDigits(true)
    .build();                                  // FR1420041010050500013M02606
```

`Iban.bankIdentifier()` and `Iban.branchIdentifier()` keep following the registry, so for France and Portugal the
branch is only available as a field.

## Fields

| Field | Meaning |
|-------|---------|
| `BANK_IDENTIFIER` | bank identifier, always as in the registry |
| `BANK_IDENTIFIER_EXTENSION` | trailing part of the bank identifier (Seychelles); lies inside the bank identifier |
| `BRANCH_IDENTIFIER` | branch identifier |
| `ACCOUNT_NUMBER` | national account number |
| `NATIONAL_CHECK_DIGITS` | national check digits; for AL and PL inside the registry branch or bank identifier |
| `ACCOUNT_TYPE` | account type |
| `OWNER_ACCOUNT_TYPE` | position of the account holder (Brazil) |
| `IDENTIFICATION_NUMBER` | identification number of the holder (Icelandic kennitala) |
| `CURRENCY` | ISO 4217 currency code (Seychelles) |
| `RESERVED` | position reserved for future use (Turkiye, currently `0`) |

Only `BANK_IDENTIFIER_EXTENSION` and `NATIONAL_CHECK_DIGITS` can lie inside another field. The builder accepts both
the enclosing field and the nested one, as long as they agree.

## Layouts

Countries without an entry below get the **registry layout**: bank and branch identifier as in the registry, the
remaining positions as `ACCOUNT_NUMBER` (`BbanLayout.isSupplemented()` is `false`). The supplements are maintained
by hand in `BbanLayoutData`; one that does not agree with the registry (BBAN length, bank identifier, registry branch
identifier) is ignored, and `BbanLayoutTest` fails. Positions are BBAN positions.

| Country | Layout | Source |
|---------|--------|--------|
| AL | bank 1-3, branch 4-8 (check digit 8), account 9-24 | Albanian account format: branch 4 digits and a check digit over bank and branch; the registry branch is 5 digits |
| BA | bank 1-3, branch 4-6, account 7-14, check 15-16 | registry structure `3!n3!n8!n2!n`; check digits ISO 7064 MOD 97-10 |
| BE | bank 1-3, account 4-10, check 11-12 | National Bank of Belgium, Belgian account number format |
| BG | bank 1-4, branch 5-8, account type 9-10, account 11-18 | Bulgarian National Bank, Ordinance 3; registry structure `4!a4!n2!n8!c` |
| BR | bank 1-8, branch 9-13, account 14-23, account type 24, holder position 25 | SWIFT IBAN registry, Brazil; Banco Central do Brasil |
| DJ | bank 1-5, branch 6-10, account 11-21, key 22-23 | registry structure `5!n5!n11!n2!n` |
| EE | bank 1-2, account 3-15, check 16 | Estonian account number, check digit by the 7-3-1 method |
| ES | bank 1-4, branch 5-8, check 9-10, account 11-20 | Código Cuenta Cliente (CCC) |
| FI | bank 1-3, account 4-13, check 14 | Finnish account number in machine format |
| FO | bank 1-4, account 5-13, check 14 | registry structure `4!n9!n1!n` |
| FR | bank 1-5, **branch 6-10**, account 11-21, RIB key 22-23 | RIB (CFONB); the registry defines no branch |
| HU | bank 1-3, branch 4-7, account 8-23, check 24 | registry structure `3!n4!n1!n15!n1!n`; position 8, the check digit over bank and branch, stays in the account as in iban4j, because a layout has one check digit field |
| IS | bank 1-2, branch 3-4, account type 5-6, account 7-12, kennitala 13-22 | registry structure `4!n2!n6!n10!n`; Icelandic account format |
| IT | CIN 1, ABI 2-6, CAB 7-11, account 12-23 | Banca d'Italia / ABI, coordinate bancarie |
| MC | as FR (bank, branch from the registry) | Monaco uses the French RIB |
| ME | bank 1-3, account 4-16, check 17-18 | ISO 7064 MOD 97-10 national check digits |
| MK | bank 1-3, account 4-13, check 14-15 | ISO 7064 MOD 97-10 national check digits |
| MR | bank 1-5, branch 6-10, account 11-21, key 22-23 | registry structure `5!n5!n11!n2!n` |
| NO | bank 1-4, account 5-10, check 11 | Norwegian kontonummer |
| PL | bank 1-8 (check digit 8), account 9-24 | Polish sort code (bank 3, branch 4, check digit 1); the registry bank identifier is the whole sort code |
| PT | bank 1-4, **branch 5-8**, account 9-19, check 20-21 | NIB (Banco de Portugal); the registry defines no branch |
| RS | bank 1-3, account 4-16, check 17-18 | ISO 7064 MOD 97-10 national check digits |
| SC | bank 1-6 (extension 5-6), branch 7-8, account 9-24, currency 25-27 | SWIFT IBAN registry, Seychelles (`4!a2!n2!n16!n3!a`) |
| SE | bank 1-3, account 4-19, check 20 | registry structure `3!n16!n1!n` |
| SI | bank 1-5, account 6-13, check 14-15 | ISO 7064 MOD 97-10 national check digits |
| SK | bank 1-4, account 5-20 (prefix 6 and basic number 10) | Slovak account number; checks see [national-checks.md](national-checks.md) |
| SM | as IT | San Marino uses the Italian BBAN |
| TL | bank 1-3, account 4-17, check 18-19 | registry structure `3!n14!n2!n` |
| TN | bank 1-2, branch 3-5, account 6-18, key 19-20 | Tunisian RIB |
| TR | bank 1-5, reserved 6, account 7-22 | registry structure `5!n1!n16!c`, position 6 reserved |
| XK | bank 1-2, branch 3-4, account 5-14, check 15-16 | registry structure `4!n10!n2!n` |

## Validating fields without an IBAN

```java
Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "37040044");   // valid
Bban.validateField("DE", BbanField.BANK_IDENTIFIER, "3704004X");   // INVALID_BBAN, position 8, expected DIGITS
Bban.validateField("DE", BbanField.NATIONAL_CHECK_DIGITS, "12");   // UNDEFINED_FIELD
Bban.validate("GB", "NWBK60161331926819");                         // the whole BBAN
```

Results distinguish `EMPTY`, `INVALID_CHARACTER`, `INVALID_LENGTH`, `INVALID_BBAN` (wrong character type),
`UNDEFINED_FIELD`, `INVALID_COUNTRY_CODE` and `UNSUPPORTED_COUNTRY`. Positions of `validateField` count the
characters of the field, positions of `validate` the characters of the BBAN (add 4 for the IBAN position). Both use
the same structure and layout as `Iban.validate`, which `BbanTest.consistentWithIbanValidation` checks for every
country.

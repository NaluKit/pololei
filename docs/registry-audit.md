# Registry data audit (pololei vs. iban4j)

This audit compares the IBAN country data of pololei with
[iban4j](https://github.com/arturmkrtchyan/iban4j/tree/b2f6be595cdd30cd722ef9d1706da0d100ba8eff), commit `b2f6be5`
(21 October 2026). It records every difference, the source pololei relies on and the decision. Each decision is
kept by a regression test in `RegistryAuditTest`.

## Source and reproducibility

| Item | Value |
|------|-------|
| Source of truth | SWIFT IBAN Registry, release 100, *IBAN Registry (TXT)* |
| SHA-256 of the TXT file used | `ad13d9595a991790ad1a5a1363d574a43357c710e3bce2170f95c9bef8932df0` |
| Generator | `scripts/registry/generate-registry.sh <file> "SWIFT IBAN Registry Release 100"` |
| Result | byte-identical `IbanRegistryData.java` (89 countries), checked on 2 November 2026 |

The registry file is not part of the repository (SWIFT terms of use). Running the generator with the file above
reproduces `IbanRegistryData.java` without any change.

## Rules

1. Supported prefixes, BBAN structures, IBAN lengths and the positions of bank and branch identifier follow the
   SWIFT IBAN registry only. A different value in iban4j alone is no reason for a change.
2. National fields that the registry does not describe (check digits, account type, the French branch code, ...)
   live in the separate, hand-maintained `BbanLayout` supplement, each with its source (see
   [bban-fields.md](bban-fields.md)). They never change bank or branch identifier where the registry defines them.
3. A deliberately kept difference gets a regression test.

## Summary

| Finding | Count | Decision |
|---------|------:|----------|
| Prefixes in pololei | 89 | release 100 |
| Prefixes in iban4j | 106 | |
| Common prefixes | 83 | |
| Union | 112 | |
| Only pololei: FK, HN, MN, NI, SO, YE | 6 | **kept**: part of release 100; their registry examples are valid (`prefixesOnlyPololeiAccepts`) |
| Only iban4j: AO, AX, BL, CV, GA, GF, GG, GP, IM, IR, JE, MA, MF, MQ, MZ, NC, PF, PM, RE, SN, TF, WF, YT | 23 | **rejected** (`UNSUPPORTED_COUNTRY`): territories that the registry lists under FR, GB or FI use those prefixes; the others have no registry entry (`prefixesOnlyIban4jAccepts`) |
| Different character mask: AE, BR, MU, PK, TR, UA | 6 | **registry kept**, see below (`characterMasks`) |
| Different bank or branch position: AL, BY, FI, FR, PL, PT, SI | 7 | **registry kept** (`bankAndBranchPositions`); FR and PT get a branch in the national layout |

### Character masks

| Prefix | Release 100 | iban4j | Effect in pololei |
|--------|-------------|--------|-------------------|
| AE | `3!n16!n` | `3!n16!c` | letters in the account number are rejected |
| BR | `8!c5!n10!n1!c1!c` | `8!n5!n10!n1!a1!c` | letters in the bank identifier and a digit as account type are accepted; the registry example `BR6699999A03000010009795493C1` is rejected by iban4j |
| MU | `4!a2!n2!n12!n3!n3!a` | `6!c2!n18!c` | account number numeric, currency alphabetic |
| PK | `4!a16!c` | `4!c16!n` | bank identifier letters only, account number alphanumeric |
| TR | `5!n1!n16!c` | `5!n1!c16!c` | the reserved sixth character is a digit |
| UA | `6!n19!c` | `6!n19!n` | letters in the account number are accepted |

### Bank and branch positions

| Prefix | Release 100 (bank / branch) | iban4j (bank / branch) | Decision |
|--------|-----------------------------|------------------------|----------|
| AL | 1-3 / 4-8 | 1-3 / 4-7 | registry; iban4j treats position 8 as national check digit |
| BY | 1-4 / – | 1-4 / 5-8 | registry |
| FI | 1-3 / – | 1-6 / – | registry |
| FR | 1-5 / – | 1-5 / 6-10 | registry; `BbanLayout` adds the branch 6-10 (CFONB RIB), `Iban.branchIdentifier()` stays empty |
| PL | 1-8 / – | 1-3 / 4-7 | registry: the 8 digit settlement number includes its check digit |
| PT | 1-4 / – | 1-4 / 5-8 | registry; `BbanLayout` adds the branch 5-8 (NIB) |
| SI | 1-5 / – | 1-2 / 3-5 | registry |

## Matrix of all 112 prefixes

Generated from `IbanRegistryData.java` and `org/iban4j/bban/BbanStructure.java` of the commit above. Positions are
BBAN positions; "–" means not defined or not supported.

| Prefix | Supported by | IBAN length | pololei (release 100) | Bank / branch pololei | iban4j | Bank / branch iban4j | Difference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AD | both | 24 | 4!n4!n12!c | 1-4 / 5-8 | 4!n4!n12!c | 1-4 / 5-8 | – |
| AE | both | 23 | 3!n16!n | 1-3 / – | 3!n16!c | 1-3 / – | character mask |
| AL | both | 28 | 8!n16!c | 1-3 / 4-8 | 3!n4!n1!n16!c | 1-3 / 4-7 | branch |
| AO | iban4j only | 25 | – | – / – | 4!n4!n11!n2!n | 1-4 / 5-8 | – |
| AT | both | 20 | 5!n11!n | 1-5 / – | 5!n11!n | 1-5 / – | – |
| AX | iban4j only | 18 | – | – / – | 6!n7!n1!n | 1-6 / – | – |
| AZ | both | 28 | 4!a20!c | 1-4 / – | 4!a20!c | 1-4 / – | – |
| BA | both | 20 | 3!n3!n8!n2!n | 1-3 / 4-6 | 3!n3!n8!n2!n | 1-3 / 4-6 | – |
| BE | both | 16 | 3!n7!n2!n | 1-3 / – | 3!n7!n2!n | 1-3 / – | – |
| BG | both | 22 | 4!a4!n2!n8!c | 1-4 / 5-8 | 4!a4!n2!n8!c | 1-4 / 5-8 | – |
| BH | both | 22 | 4!a14!c | 1-4 / – | 4!a14!c | 1-4 / – | – |
| BI | both | 27 | 5!n5!n11!n2!n | 1-5 / 6-10 | 5!n5!n13!n | 1-5 / 6-10 | – |
| BL | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| BR | both | 29 | 8!c5!n10!n1!c1!c | 1-8 / 9-13 | 8!n5!n10!n1!a1!c | 1-8 / 9-13 | character mask |
| BY | both | 28 | 4!c4!n16!c | 1-4 / – | 4!c4!n16!c | 1-4 / 5-8 | branch |
| CH | both | 21 | 5!n12!c | 1-5 / – | 5!n12!c | 1-5 / – | – |
| CR | both | 22 | 4!n14!n | 1-4 / – | 4!n14!n | 1-4 / – | – |
| CV | iban4j only | 25 | – | – / – | 4!n4!n13!c | 1-4 / 5-8 | – |
| CY | both | 28 | 3!n5!n16!c | 1-3 / 4-8 | 3!n5!n16!c | 1-3 / 4-8 | – |
| CZ | both | 24 | 4!n16!n | 1-4 / – | 4!n16!n | 1-4 / – | – |
| DE | both | 22 | 8!n10!n | 1-8 / – | 8!n10!n | 1-8 / – | – |
| DJ | both | 27 | 5!n5!n11!n2!n | 1-5 / 6-10 | 5!n5!n11!n2!n | 1-5 / 6-10 | – |
| DK | both | 18 | 4!n9!n1!n | 1-4 / – | 4!n10!n | 1-4 / – | – |
| DO | both | 28 | 4!c20!n | 1-4 / – | 4!c20!n | 1-4 / – | – |
| EE | both | 20 | 2!n14!n | 1-2 / – | 2!n13!n1!n | 1-2 / – | – |
| EG | both | 29 | 4!n4!n17!n | 1-4 / 5-8 | 4!n4!n17!n | 1-4 / 5-8 | – |
| ES | both | 24 | 4!n4!n1!n1!n10!n | 1-4 / 5-8 | 4!n4!n2!n10!n | 1-4 / 5-8 | – |
| FI | both | 18 | 3!n11!n | 1-3 / – | 6!n7!n1!n | 1-6 / – | bank |
| FK | pololei only | 18 | 2!a12!n | 1-2 / – | – | – / – | – |
| FO | both | 18 | 4!n9!n1!n | 1-4 / – | 4!n9!n1!n | 1-4 / – | – |
| FR | both | 27 | 5!n5!n11!c2!n | 1-5 / – | 5!n5!n11!c2!n | 1-5 / 6-10 | branch |
| GA | iban4j only | 27 | – | – / – | 5!n5!n13!c | 1-5 / 6-10 | – |
| GB | both | 22 | 4!a6!n8!n | 1-4 / 5-10 | 4!a6!n8!n | 1-4 / 5-10 | – |
| GE | both | 22 | 2!a16!n | 1-2 / – | 2!a16!n | 1-2 / – | – |
| GF | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| GG | iban4j only | 22 | – | – / – | 4!a6!n8!n | 1-4 / 5-10 | – |
| GI | both | 23 | 4!a15!c | 1-4 / – | 4!a15!c | 1-4 / – | – |
| GL | both | 18 | 4!n9!n1!n | 1-4 / – | 4!n10!n | 1-4 / – | – |
| GP | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| GR | both | 27 | 3!n4!n16!c | 1-3 / 4-7 | 3!n4!n16!c | 1-3 / 4-7 | – |
| GT | both | 28 | 4!c20!c | 1-4 / – | 4!c20!c | 1-4 / – | – |
| HN | pololei only | 28 | 4!a20!n | 1-4 / – | – | – / – | – |
| HR | both | 21 | 7!n10!n | 1-7 / – | 7!n10!n | 1-7 / – | – |
| HU | both | 28 | 3!n4!n1!n15!n1!n | 1-3 / 4-7 | 3!n4!n16!n1!n | 1-3 / 4-7 | – |
| IE | both | 22 | 4!a6!n8!n | 1-4 / 5-10 | 4!a6!n8!n | 1-4 / 5-10 | – |
| IL | both | 23 | 3!n3!n13!n | 1-3 / 4-6 | 3!n3!n13!n | 1-3 / 4-6 | – |
| IM | iban4j only | 22 | – | – / – | 4!a6!n8!n | 1-4 / 5-10 | – |
| IQ | both | 23 | 4!a3!n12!n | 1-4 / 5-7 | 4!a3!n12!n | 1-4 / 5-7 | – |
| IR | iban4j only | 26 | – | – / – | 3!n19!n | 1-3 / – | – |
| IS | both | 26 | 4!n2!n6!n10!n | 1-2 / 3-4 | 2!n2!n2!n6!n10!n | 1-2 / 3-4 | – |
| IT | both | 27 | 1!a5!n5!n12!c | 2-6 / 7-11 | 1!a5!n5!n12!c | 2-6 / 7-11 | – |
| JE | iban4j only | 22 | – | – / – | 4!a6!n8!n | 1-4 / 5-10 | – |
| JO | both | 30 | 4!a4!n18!c | 1-4 / 5-8 | 4!a4!n18!c | 1-4 / 5-8 | – |
| KW | both | 30 | 4!a22!c | 1-4 / – | 4!a22!c | 1-4 / – | – |
| KZ | both | 20 | 3!n13!c | 1-3 / – | 3!n13!c | 1-3 / – | – |
| LB | both | 28 | 4!n20!c | 1-4 / – | 4!n20!c | 1-4 / – | – |
| LC | both | 32 | 4!a24!c | 1-4 / – | 4!a24!c | 1-4 / – | – |
| LI | both | 21 | 5!n12!c | 1-5 / – | 5!n12!c | 1-5 / – | – |
| LT | both | 20 | 5!n11!n | 1-5 / – | 5!n11!n | 1-5 / – | – |
| LU | both | 20 | 3!n13!c | 1-3 / – | 3!n13!c | 1-3 / – | – |
| LV | both | 21 | 4!a13!c | 1-4 / – | 4!a13!c | 1-4 / – | – |
| LY | both | 25 | 3!n3!n15!n | 1-3 / 4-6 | 3!n3!n15!n | 1-3 / 4-6 | – |
| MA | iban4j only | 28 | – | – / – | 3!n5!n16!n | 1-3 / 4-8 | – |
| MC | both | 27 | 5!n5!n11!c2!n | 1-5 / 6-10 | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| MD | both | 24 | 2!c18!c | 1-2 / – | 2!c18!c | 1-2 / – | – |
| ME | both | 22 | 3!n13!n2!n | 1-3 / – | 3!n13!n2!n | 1-3 / – | – |
| MF | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| MK | both | 19 | 3!n10!c2!n | 1-3 / – | 3!n10!c2!n | 1-3 / – | – |
| MN | pololei only | 20 | 4!n12!n | 1-4 / – | – | – / – | – |
| MQ | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| MR | both | 27 | 5!n5!n11!n2!n | 1-5 / 6-10 | 5!n5!n11!n2!n | 1-5 / 6-10 | – |
| MT | both | 31 | 4!a5!n18!c | 1-4 / 5-9 | 4!a5!n18!c | 1-4 / 5-9 | – |
| MU | both | 30 | 4!a2!n2!n12!n3!n3!a | 1-6 / 7-8 | 6!c2!n18!c | 1-6 / 7-8 | character mask |
| MZ | iban4j only | 25 | – | – / – | 4!n4!n11!n2!n | 1-4 / 5-8 | – |
| NC | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| NI | pololei only | 28 | 4!a20!n | 1-4 / – | – | – / – | – |
| NL | both | 18 | 4!a10!n | 1-4 / – | 4!a10!n | 1-4 / – | – |
| NO | both | 15 | 4!n6!n1!n | 1-4 / – | 4!n6!n1!n | 1-4 / – | – |
| OM | both | 23 | 3!n16!c | 1-3 / – | 3!n16!c | 1-3 / – | – |
| PF | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| PK | both | 24 | 4!a16!c | 1-4 / – | 4!c16!n | 1-4 / – | character mask |
| PL | both | 28 | 8!n16!n | 1-8 / – | 3!n4!n1!n16!n | 1-3 / 4-7 | bank, branch |
| PM | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| PS | both | 29 | 4!a21!c | 1-4 / – | 4!a21!c | 1-4 / – | – |
| PT | both | 25 | 4!n4!n11!n2!n | 1-4 / – | 4!n4!n11!n2!n | 1-4 / 5-8 | branch |
| QA | both | 29 | 4!a21!c | 1-4 / – | 4!a21!c | 1-4 / – | – |
| RE | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| RO | both | 24 | 4!a16!c | 1-4 / – | 4!a16!c | 1-4 / – | – |
| RS | both | 22 | 3!n13!n2!n | 1-3 / – | 3!n13!n2!n | 1-3 / – | – |
| RU | both | 33 | 9!n5!n15!c | 1-9 / 10-14 | 9!n5!n15!c | 1-9 / 10-14 | – |
| SA | both | 24 | 2!n18!c | 1-2 / – | 2!n18!c | 1-2 / – | – |
| SC | both | 31 | 4!a2!n2!n16!n3!a | 1-6 / 7-8 | 4!a2!n2!n16!n3!a | 1-6 / 7-8 | – |
| SD | both | 18 | 2!n12!n | 1-2 / – | 2!n12!n | 1-2 / – | – |
| SE | both | 24 | 3!n16!n1!n | 1-3 / – | 3!n16!n1!n | 1-3 / – | – |
| SI | both | 19 | 5!n8!n2!n | 1-5 / – | 2!n3!n8!n2!n | 1-2 / 3-5 | bank, branch |
| SK | both | 24 | 4!n6!n10!n | 1-4 / – | 4!n16!n | 1-4 / – | – |
| SM | both | 27 | 1!a5!n5!n12!c | 2-6 / 7-11 | 1!a5!n5!n12!c | 2-6 / 7-11 | – |
| SN | iban4j only | 28 | – | – / – | 5!c5!n12!n2!n | 1-5 / 6-10 | – |
| SO | pololei only | 23 | 4!n3!n12!n | 1-4 / 5-7 | – | – / – | – |
| ST | both | 25 | 4!n4!n11!n2!n | 1-4 / 5-8 | 4!n4!n13!n | 1-4 / 5-8 | – |
| SV | both | 28 | 4!a20!n | 1-4 / – | 4!a20!n | 1-4 / – | – |
| TF | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| TL | both | 23 | 3!n14!n2!n | 1-3 / – | 3!n14!n2!n | 1-3 / – | – |
| TN | both | 24 | 2!n3!n13!n2!n | 1-2 / 3-5 | 2!n3!n13!n2!n | 1-2 / 3-5 | – |
| TR | both | 26 | 5!n1!n16!c | 1-5 / – | 5!n1!c16!c | 1-5 / – | character mask |
| UA | both | 29 | 6!n19!c | 1-6 / – | 6!n19!n | 1-6 / – | character mask |
| VA | both | 22 | 3!n15!n | 1-3 / – | 3!n15!n | 1-3 / – | – |
| VG | both | 24 | 4!a16!n | 1-4 / – | 4!a16!n | 1-4 / – | – |
| WF | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |
| XK | both | 20 | 4!n10!n2!n | 1-2 / 3-4 | 2!n2!n10!n2!n | 1-2 / 3-4 | – |
| YE | pololei only | 30 | 4!a4!n18!c | 1-4 / 5-8 | – | – / – | – |
| YT | iban4j only | 27 | – | – / – | 5!n5!n11!c2!n | 1-5 / 6-10 | – |

# Security Policy

## Supported versions

Security fixes are provided only for the latest released version. A fix is published as a new
release; older versions receive no patches. To get security fixes, update to the latest version.

| Version         | Supported | Notes                                                           |
|-----------------|-----------|-----------------------------------------------------------------|
| 1.0.0-rc01      | Yes       | Published to Maven Central.                                     |
| `HEAD-SNAPSHOT` | No        | Development builds of the `dev` branch. Not for production use. |

Each new release replaces the previous one as the only supported version.

pololei validates IBANs and BICs that usually come from users or other systems. Security relevant are in
particular:

- validation that accepts an invalid IBAN or BIC, or rejects a valid one;
- deserialization: Java serialization and GWT-RPC must never create an invalid `Iban`, `Bic` or
  `ValidationResult` from a manipulated stream or request;
- error messages and exceptions, which must not contain the complete input (account data);
- EPC QR code payloads: values must not be able to add or shift lines of the payload.

pololei has no runtime dependencies; `pololei-gwt` uses the `gwt-user` version of the application.

## Reporting a vulnerability

Please do **not** open a public issue for a vulnerability.

1. Preferred: report it privately via GitHub at
   [Security → Report a vulnerability](https://github.com/NaluKit/pololei/security/advisories/new).
2. Alternatively, send an e-mail to `contact@nalu-solutions.de` with `[security] pololei` in the
   subject.

Please include:

- the affected pololei version or commit and the module (for example `pololei-gwt`),
- a description of the impact and, if possible, a minimal proof of concept,
- no real account data: use example IBANs such as the ones published in the SWIFT IBAN registry.

## After your report

- We confirm receipt within five working days and tell you whether we can reproduce the issue.
- We assess the severity and agree on a disclosure date with you. Until then the report stays
  private.
- The fix is published as a new release of the latest version, together with a GitHub security
  advisory that names the affected and fixed versions. We credit you unless you prefer otherwise.

Thank you for helping to keep pololei and its users safe.

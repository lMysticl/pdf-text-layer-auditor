# Security policy

## Supported versions

Security fixes are applied to the latest published release.

| Version | Supported |
|---|---|
| 0.4.x | Yes |
| 0.3.x | No |
| 0.2.x | No |
| 0.1.x | No |

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability or attach a sensitive PDF.

Use [GitHub private vulnerability reporting](https://github.com/lMysticl/pdf-text-layer-auditor/security/advisories/new) and include:

- the affected version;
- the smallest reproduction steps;
- the expected security boundary;
- the observed impact;
- a synthetic or safely redacted fixture, if one is required.

This tool parses untrusted binary input but is not a sandbox. Continue to enforce external CPU, memory, disk, and time limits when processing untrusted files.

The GitHub Action accepts untrusted PDF content from pull requests. Use it with
the `pull_request` event and the documented read-only `contents` and
`pull-requests` permissions. The action rejects `pull_request_target`; do not
work around that guard by passing a more privileged token to untrusted code.

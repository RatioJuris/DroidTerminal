# Security policy

Droid Terminal is a remote-access tool. Security is an architectural requirement, not a final polish step.

## Security boundaries

- **Untrusted input:** all terminal output, SSH banners, host keys, file names and remote paths.
- **Trusted local storage:** application-private files and preferences, protected by Android sandboxing and, for secrets, by Android Keystore-backed encryption.
- **User-authorized trust:** host-key acceptance and private-key export. The application never silently performs either.
- **Network:** SSH-2 only. Cleartext protocols are out of scope.

## Rules

1. Private keys, passphrases and passwords are never stored in plaintext.
2. Encryption keys are generated and protected by Android Keystore; no key material is hardcoded.
3. Sensitive data is never written to logs, crash reports, analytics or intents.
4. Host-key verification is always enabled. Unknown and changed keys require explicit user comparison and confirmation.
5. Biometrics, when added, gate access to an encrypted vault; they are not a replacement for encryption.
6. Destructive SFTP operations require confirmation.
7. `android:allowBackup="false"` is set so application data, including the future vault, is not copied into cloud or local backups.
8. Dependencies are licence-reviewed and subject to Gradle dependency verification.

## Privacy commitments

The [RatioJuris Privacy Promise](PRIVACY.md) is a product commitment: no analytics, no advertising, no developer-operated telemetry or data collection, no mandatory RatioJuris cloud services, and local storage of application data. SSH connections that the user explicitly authorises to servers of their choosing are not developer-operated data collection.

Encrypted credential storage and vault authentication independent of the device screen lock are required security objectives for Phase 5. They are not implemented in Phase 1 and are not claimed as present.

## Android permissions

Phase 1 requests no permissions. `INTERNET` will be added with the SSH engine because it is essential to the product; no other permission will be added without a documented need.

## Reporting a vulnerability

Report suspected vulnerabilities privately through the repository's security advisory channel. Do not publish private keys, credentials or reproduction data in a public issue.

## Limitations

Phase 1 contains no SSH transport, vault implementation or terminal emulator. The security properties of those subsystems cannot be claimed until their code and tests exist.

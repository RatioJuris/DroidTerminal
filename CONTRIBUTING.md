# Contributing

## Development model

GitHub is the primary development and build environment. `main` is the release integration branch; active work happens on `develop` and feature branches. GitHub Actions is authoritative: a green local build is useful for iteration but never replaces a green CI run.

## Workflow

1. Create a feature branch from `develop`.
2. Make focused changes and add tests alongside the functionality.
3. Run `.\gradlew --no-daemon testDebugUnitTest lint assembleDebug` locally if the optional Android toolchain is available.
4. Push and open a pull request into `develop`.
5. Resolve CI failures before requesting review.

## Code expectations

- Kotlin, Jetpack Compose, AndroidX and Material 3 unless an architectural decision says otherwise.
- Keep module boundaries intact: the terminal engine must not depend on SSH; SSH must not depend on Compose UI.
- Never add credentials, keystores, private keys or hard-coded encryption keys.
- Never disable host-key verification or security checks to make a test pass.
- Use the GPL source header shown in existing files.
- Update documentation and `THIRD_PARTY_NOTICES` where relevant.

## Commits

Use short, imperative commit messages. Group changes logically (for example: "Add keypad modifier state model and tests"). Do not commit generated build outputs.

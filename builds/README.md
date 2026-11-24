# Builds

CI-published build outputs are committed to this directory so they remain in the
repository instead of expiring as GitHub Actions artifacts.

## Layout

```
builds/debug/droidterminal-debug-<run-number>-<commit-sha>.apk
```

- `run-number` is the GitHub Actions run that produced the file.
- `commit-sha` is the exact source commit the APK was built from.

## Policy

- Only the `verify` job on `develop` publishes here. Pull requests are verified
  but do not write to the repository.
- The workflow pushes with `GITHUB_TOKEN`; GitHub does not trigger a new run for
  those commits, so this cannot create a CI loop.
- Reports (test and lint HTML/SARIF) remain short-lived Actions artifacts; only
  installable build outputs are retained here.
- Files are append-only. Do not rewrite or delete a published build without an
  explicit repository cleanup decision.

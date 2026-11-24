# Assumptions

Recorded when the three-phase build directive was received. These resolve conflicts
between the new directive and the standing repository constraints without blocking work.

- **New directive governs planning.** The three-phase plan (complete UI → fill placeholders → UX review) supersedes the previously delivered Phase 2 Batch 3 engine-verification plan. Engine modules are not revisited or modified in these phases.
- **Marked placeholders are now authorised.** The earlier "no mock UI or demo data" constraint is read as prohibiting mock data *presented as finished*. The new directive explicitly requires mock data plus `// TODO(phase2):` markers and `PHASE2_TODO.md`, so all placeholder data is clearly marked and inventoried.
- **Build authority unchanged.** GitHub Actions remains the only build/test authority. "The app runs without errors" is verified by CI compile, Android lint and unit tests on `develop`; no local Gradle invocation. Physical/visual rendering is a device-validation activity and cannot be asserted locally under this policy.
- **Branch and commit rules unchanged.** `develop` only, never `main`; commit subjects at most 15 characters.
- **State lifetime.** Phase 1 screen state is in-memory via `remember`; complex model lists are not saved across process death. Persistence is a Phase 2 item and is listed in `PHASE2_TODO.md`.
- **Icon surface.** Only icon vectors already proven available in `material-icons-core` are used; other affordances use text labels to avoid adding a dependency during Phase 1.

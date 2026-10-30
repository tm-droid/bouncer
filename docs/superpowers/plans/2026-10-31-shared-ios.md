# Bouncer shared Android/iOS implementation plan

Goal: unify the repo, centralize backend detail retrieval and deliver a real-data native iOS match experience. Specification: ../specs/2026-10-31-shared-ios-design.md.

1. Repository migration: preserve Android history, restructure into android/ plus root appwrite/.github, sanitized iOS import; shared public config; repair paths, CI, docs and parity inputs. Acceptance: clean structure with no lost tracked Android content, no credentials, correct release filters, vendor/config tests.
2. Shared detail service: tests first for route contract/cache/validation, generated parser reuse, private-cache configuration, bounded deadline and maintenance. Acceptance: full Node suites, cold/warm/stale/partial/budget/unknown-ID tests and zero AI invocation.
3. iOS client and screens: native config/gateway/models/repository + cache/lifecycle, real match list/detail/scorecard/commentary, meaningful test targets. Acceptance: pinned SDK, no old network/provider/random data paths, fixture tests and simulator flow.
4. Android integration: map shared detail route into current fallback seams, request-specific timeout and existing guards/caches. Acceptance: regression tests, unchanged other scrapers and live/archival behavior.
5. Final review, scheduled verification, docs and handoff: run all relevant checks only when queue allows; fix correctness findings; commit units, preserve source checkouts; report deployment steps and unverified live state. No push/deploy.

Execution: separate ownership per unit; U2 and U3 can run in parallel after U1. U4 depends on the pinned U2 contract. Root agent owns ledger/spec/plan; workers must not edit others' files. Queue constraints override build cadence.

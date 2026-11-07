# Student deactivation build verification

Traces to UC-STU-deactivate-student and [the design](../design/stu.md). This report records actual observations; passing feature tests do not imply a green repository build.

| Design test-list row | Evidence / remaining gap |
|---|---|
| Confirm deactivation | `deactivate-student.spec.cy.js`: confirmation sends one POST, prevents duplicate click, refreshes status and shows success (stubbed action). Controller test verifies response envelope. |
| Cancel warning (4a) | Cypress verifies no mutation; local browser warning and cancellation observed. |
| Preserve records and team | Security integration preserves team; evaluation ordering tests preserve saved evaluations. Explicit WAR-record retention assertion not added. |
| Recompute owed work | Existing `SubmissionStatusServiceTest` and integration tests cover inactive eligibility; no new end-to-end withdrawal/reminder scenario. |
| Reject inactive evaluation target | Evaluation unit/integration tests reject creates and edits; UI filters inactive roster and saved targets. Form omission not independently browser-tested. |
| Keep historical reporting | Evaluation unit test covers inactive history and absence without history, including no current team. |
| Deny existing credentials (6a) | Security integration: existing student token and new login rejected. |
| Repeat deactivation (6b) | Service/concurrency tests, HTTP 409 test, Cypress conflict refresh. |
| Enforce actor scope (6c) | Owning admin, assigned instructor, foreign admin, unassigned instructor, and student denial covered across service/HTTP tests. |
| Enforce concurrent ordering | MySQL tests cover one successful confirmation, evaluation create/update ordering, stale account and section reads. |
| Deny inactive instructor credentials | Security integration verifies existing token and new login rejected. |
| Prevent generic edit bypass | StudentService tests preserve status in both directions; edit UI no longer offers status control. |
| Existing writer preservation | Five original failing real-service writer regressions now pass with Student-only DynamicUpdate. |
| Current eligibility under lock | MySQL stale-status, transfer-scope, and evaluation ordering regressions pass. |

## Recorded results

- Shared P2 fix targeted run: 70 passed, 0 failed/errors/skipped. New regression first failed with the leaked inactive-account message, then passed with the same team-scope refusal for both states.
- Controller test: 1 passed. Security/JWT integration: 5 passed.
- Full Cypress run: 23 tests; 15 passed, 4 failed, 4 skipped. All 4 new deactivation cases passed. Failures occurred in older login, peer-evaluation, and weekly-activity specs; these failures were not reproduced against a separate baseline browser stack, so their baseline status is unverified.
- `npm run build-only`: passed. `npm run build`: failed with 18 TypeScript diagnostics; the exact diagnostics were reproduced on an origin/main checkout using the same installed dependencies.
- Full Maven suite was started; final result is recorded below when available. Do not infer full-suite success from targeted runs.
- Isolated manual stack: backend 8081, MySQL 3307, Mailpit SMTP 1026/web 8026; frontend 5173. Demo accounts only. Manual browser observed login, student details, warning and cancellation; live confirmation through the browser was not completed.

## Review and participation

The author reviewed the shared changes and found a P2 account-status disclosure before team scoping; fixed separately in c3bedf2, with a red/green regression. The user approved the shared changes and that fix. This is not a fresh-context independent review of the completed build.

Author triage: reviewed lifecycle locking, Student mapping, scoped evaluation creation, controller envelopes, JWT validation and route matching; checked UI confirmation and target-filter paths against tests. Human test vetting, an independent fresh-context review, a confirmation-dialog screenshot, the remaining gaps above, and resolution/acceptance of repository-wide failures remain outstanding. Do not merge on the strength of this report alone.

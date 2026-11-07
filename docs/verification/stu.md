# Student deactivation build verification

Traces to UC-STU-deactivate-student and [the design](../design/stu.md). This report records actual observations; passing feature tests do not imply a green repository build.

| Design test-list row | Evidence / remaining gap |
|---|---|
| Confirm deactivation | `deactivate-student.spec.cy.js`: confirmation sends one POST, prevents duplicate click, refreshes status and shows success (stubbed action). Controller test verifies response envelope. |
| Cancel warning (4a) | Cypress verifies no mutation; local browser warning and cancellation observed. |
| Preserve records and team | Security integration preserves team; evaluation ordering tests preserve saved evaluations. Explicit WAR/evaluation retention now passes in `withdrawalPreservesWorkAndRecomputesWhatActiveTeammateOwes`. |
| Recompute owed work | Existing `SubmissionStatusServiceTest` and integration tests cover inactive eligibility; new integrated withdrawal/owed-work/active-teammate completion scenario passes; actual reminder-email sending remains outside this new test. |
| Reject inactive evaluation target | Evaluation unit/integration tests reject creates and edits; UI filters inactive roster and saved targets. New and saved form omission now browser-tested (stubbed roster/history fixtures). |
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
- Full Maven suite completed: **476 passed, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
- Isolated manual stack: backend 8081, MySQL 3307, Mailpit SMTP 1026/web 8026; frontend 5173. Demo accounts only. Manual browser observed login, student details, warning and cancellation; real browser confirmation now passed, including status after reload.

## Review and participation

The author reviewed the shared changes and found a P2 account-status disclosure before team scoping; fixed separately in c3bedf2, with a red/green regression. The user approved the shared changes and that fix. A separate fresh-context review of the completed production diff is now recorded below.

Author triage: reviewed lifecycle locking, Student mapping, scoped evaluation creation, controller envelopes, JWT validation and route matching; checked UI confirmation and target-filter paths against tests. Fresh-context review and additional user-requested gap tests are recorded below. Final human acceptance and resolution/acceptance of repository-wide failures remain outstanding. Do not merge on the strength of this report alone.

## Retained test oracles and vetting

This inventory covers tests added/changed for this build and the named pre-existing eligibility tests reused as acceptance evidence, not unrelated tests in the 476-test repository suite. Parameterized rows explicitly name their cases. The oracle is the requirement that determines the expected outcome, not the implementation expression being asserted.

| Retained test | Requirement/business-rule oracle and observed outcome |
|---|---|
| `StudentDeactivationControllerTest.dedicatedActionUsesStandardSuccessAndErrorEnvelopes` | Steps 5–6,6b,6c; design API contract and architecture Result — HTTP success/403/404/409 envelopes. |
| `StudentDeactivationServiceTest.owningAdminDeactivatesWithoutDeletingOrSavingOverOtherFields` | UC-STU steps 5–6; BR-student-lifecycle — flag becomes false, profile unchanged, no deletion. |
| `StudentDeactivationServiceTest.assignedInstructorDeactivates` | 6c; BR-student-lifecycle — assigned instructor allowed. |
| `StudentDeactivationServiceTest.alreadyInactiveIsAConflict` | 6b; design HTTP conflict contract — repeated confirmation rejected. |
| `StudentDeactivationServiceTest.deniedScopeDoesNotDiscloseStatus` | 6c; architecture authorization — scope denial before status disclosure. Mock-level refusal only. |
| `StudentDeactivationServiceTest.missingStudentIsNotFound` | Design API contract — missing student maps to not found. |
| `StudentDeactivationServiceTest.studentRoleCannotUseLifecycleAction` | 6c; BR-student-lifecycle — student denied before repository access. |
| `StudentDeactivationServiceTest.missingIdentityIsDeniedBeforeLookup` | 6c; architecture caller identity / fail closed — unresolved caller denied. |
| `StudentDeactivationSecurityIntegrationTest.deactivationRevokesExistingStudentTokenAndNewLoginAndRetainsTeam` | 6a; FR-SEC-active-account; BR-student-lifecycle — working token then 401, new login 401, team retained, repeat 409. |
| `StudentDeactivationSecurityIntegrationTest.inactiveInstructorTokenIsRejected` | FR-SEC-active-account — instructor existing token and new login denied. |
| `StudentDeactivationSecurityIntegrationTest.routeAndOwnershipDenialsAndMissingStudent` | 6c; BR-student-lifecycle; design API — student and foreign admin 403, missing ID 404, unchanged status. |
| `StudentDeactivationSecurityIntegrationTest.assignedInstructorCanDeactivate` | 6c; BR-student-lifecycle — assigned instructor HTTP success. |
| `StudentDeactivationSecurityIntegrationTest.unassignedInstructorIsDeniedWithoutStateChange` | 6c; BR-student-lifecycle — assignment removed, HTTP 403 and enabled unchanged. |
| `StudentStaleWriterIntegrationTest.staleWriterDoesNotReactivateStudent` | BR-student-lifecycle; FR-SEC-active-account; design existing-writer row — PROFILE, ASSIGN_TEAM, REMOVE_TEAM, TRANSFER_TEAM, RESET_PASSWORD each retain false and intended edits. |
| `StudentStaleWriterIntegrationTest.lockedReadRehydratesAnAlreadyManagedActiveSnapshot` | Design current-eligibility row; BR-inactive-peer-evaluations — locked read sees committed false despite cached true. |
| `StudentStaleWriterIntegrationTest.lifecycleScopeComesFromOwnershipOrAssignment` | 6c; BR-student-lifecycle — OWNING_ADMIN and ASSIGNED_INSTRUCTOR succeed; FOREIGN_ADMIN, UNASSIGNED_INSTRUCTOR and STUDENT fail. |
| `StudentStaleWriterIntegrationTest.scopedLookupRejectsAnInstructorAfterConcurrentSectionTransfer` | 6c; design current-eligibility row — old section snapshot cannot authorize after committed transfer. |
| `StudentStaleWriterIntegrationTest.concurrentConfirmationsProduceExactlyOneSuccess` | 6b; design conditional transition — exactly one success, one already-inactive refusal. |
| `StudentStaleWriterIntegrationTest.deactivationFirstRejectsStaleEvaluationWrite` | BR-inactive-peer-evaluations; design concurrent-ordering row — CREATE and UPDATE after deactivation reject; saved history unchanged. |
| `StudentStaleWriterIntegrationTest.evaluationWriteHoldsDeactivationUntilItsCommit` | BR-inactive-peer-evaluations; design concurrent-ordering row — CREATE and UPDATE hold deactivation until commit; saved evaluation retained. Bounded future timeout checks blocking. |
| `StudentStaleWriterIntegrationTest.outOfScopeTargetIsRefusedWithoutDisclosingStatus` | BR-team-scoped-access; architecture scoped persistence; review P2 — active and inactive out-of-team targets receive identical refusal and no mutation. |
| `StudentStaleWriterIntegrationTest.withdrawalPreservesWorkAndRecomputesWhatActiveTeammateOwes` | BR-student-lifecycle, BR-submission-owed; design preservation/recompute rows — WAR and evaluation retained; active teammate completed after withdrawal; withdrawn student owes neither item. |
| `StudentServiceTest.testStudentUpdateOwnInfo` | UC-ACC-edit-account; design generic-bypass row — self-profile update cannot alter enabled or roles. |
| `StudentServiceTest.testInstructorUpdatesStudent` | UC-STU steps 3–6; design generic-bypass row — profile edits allowed, status preserved; old status-write assertion deliberately reversed. |
| `StudentServiceTest.profileUpdateCannotReactivateAnInactiveStudent` | UC-STU steps 3–6; design generic-bypass row — profile changes preserve inactive status and roles. |
| `EvaluationServiceTest.rejectInactiveTargetOnCreate` | BR-inactive-peer-evaluations — inactive in-team target rejected, no save. |
| `EvaluationServiceTest.rejectInactiveTargetOnUpdateWithoutChangingSavedComments` | BR-inactive-peer-evaluations — inactive target edit rejected and stored comments preserved. |
| `EvaluationServiceTest.weeklyReportIncludesInactiveStudentOnlyWithSavedHistoryEvenWithoutCurrentTeam` | BR-inactive-peer-evaluations; historical-report row — inactive included in saved-history week and absent otherwise. Mocked existence answers; does not validate the real existence query. |
| `EvaluationServiceTest.outOfTeamInactiveTargetReceivesTheSameRefusalAsAnActiveTarget` | BR-team-scoped-access; architecture scoped persistence; review P2 — same refusal for active/inactive outside team, no save or unscoped locked lookup. |
| `SubmissionStatusServiceTest.testWeeklyActivityReportOwedByEligibleStudentsWithNoActivity` | BR-submission-owed — testWeeklyActivityReportOwedByEligibleStudentsWithNoActivity; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceTest.testPeerEvaluationOwedUntilEveryActiveTeammateIncludingSelfIsEvaluated` | BR-submission-owed — testPeerEvaluationOwedUntilEveryActiveTeammateIncludingSelfIsEvaluated; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceTest.testEvaluatingSomeoneOutsideTheTeamDoesNotCount` | BR-submission-owed — testEvaluatingSomeoneOutsideTheTeamDoesNotCount; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceTest.testFindOwedItemsListsOnlyTheItemsEachStudentOwes` | BR-submission-owed — testFindOwedItemsListsOnlyTheItemsEachStudentOwes; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceTest.testNobodyOwesWhenEveryoneSubmitted` | BR-submission-owed — testNobodyOwesWhenEveryoneSubmitted; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceIntegrationTest.testWeeklyActivityReportOwedInSeedData` | BR-submission-owed — testWeeklyActivityReportOwedInSeedData; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceIntegrationTest.testPeerEvaluationOwedInSeedData` | BR-submission-owed — testPeerEvaluationOwedInSeedData; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceIntegrationTest.testDeletingAStudentsLastActivityMakesTheReportOwedAgain` | BR-submission-owed — testDeletingAStudentsLastActivityMakesTheReportOwedAgain; existing eligibility/owed-work regression reused by this use case. |
| `SubmissionStatusServiceIntegrationTest.testAnEvaluationOfADeactivatedTeammateIsNotRequired` | BR-submission-owed — testAnEvaluationOfADeactivatedTeammateIsNotRequired; existing eligibility/owed-work regression reused by this use case. |

| Retained browser test | Oracle and scope |
|---|---|
| `warns about consequences and cancels without a mutation (4a)` | UC-STU step 4,4a — warning consequences and zero requests on cancel. |
| `confirms once, prevents duplicate submission, and refreshes status` | Steps 5–6 — one dedicated POST, pending button disabled, refreshed inactive status, success notice; action response stubbed. |
| `reports a repeated confirmation and refreshes inactive status (6b)` | 6b — conflict notice and refreshed inactive state; response stubbed. |
| `does not claim success after a failed request` | Step 6 success contingent on server success; failure notice and active state; response stubbed. |
| `omits inactive targets from new evaluation forms` | BR-inactive-peer-evaluations; design inactive-target row — actual form table omits inactive roster member. API fixtures stubbed. |
| `omits inactive targets from saved evaluation forms` | Same rule — saved inactive targets omitted from actual form. API fixtures stubbed. |
| `confirms deactivation through the real browser, API and database` | Steps 3–6 — real POST success and inactive status retained after browser reload; warning screenshot captured. |

No tests deleted. Existing generic-update assertion was revised to prohibit status changes, as the approved design requires. New tests passed as recorded below.

## One-line mutation chosen by the user

The user selected `EvaluationServiceTest.weeklyReportIncludesInactiveStudentOnlyWithSavedHistoryEvenWithoutCurrentTeam` as least trusted because it supplies mocked history answers. Its oracle is BR-inactive-peer-evaluations, not the repository implementation. We temporarily replaced the inactive branch `this.evaluationRepository.existsByWeekAndEvaluateeId(week, student.getId())` with `true`. The targeted test failed (1 test, 1 failure, 0 errors), catching inclusion in a week without history. The original line was restored in a finally block; the same test then passed (1 test, 0 failures/errors/skipped). The mutation was not committed. This establishes sensitivity to bypassing the history condition, not correctness of the real history query or all possible mutations.

## Fresh-context review

A separate reviewer agent with no inherited chat history read repository guidance, the production diff, the use case/business rules, design, architecture crosscutting concepts, and relevant tests. No actionable introduced production defect found; no tests executed by that reviewer.

| Finding | Disposition |
|---|---|
| Explicit WAR retention assertion missing | User requested test now; added `withdrawalPreservesWorkAndRecomputesWhatActiveTeammateOwes`, result below. |
| No integrated withdrawal-to-owed-work scenario | Same new MySQL test asserts recomputation and active-teammate completion; no actual reminder email asserted. |
| Browser form-omission coverage missing | User requested new and saved form browser cases; results below. |
| Real browser confirmation missing | User requested real endpoint confirmation and reload case; result below. |
| Screenshot and human test vetting outstanding; stale pending-Maven report | Screenshot case added; user chose mutation and requested gaps tested. Maven corrected to 476 passed. Final human acceptance remains separate. |

Prior author-review P2 status disclosure: fixed in c3bedf2 with red/green test; already approved by user. Labeled non-blocking diff comment: https://github.com/ralphACastilleja/add-a-use-case/pull/3#discussion_r4500193682 (gap disposition updated by this work).

## Additional review-step results

- `StudentStaleWriterIntegrationTest`: **20 passed**, 0 failures/errors/skipped, including new explicit WAR/evaluation retention and withdrawal/owed-work scenario.
- `deactivation-review.spec.cy.js`: **3 passed**, 0 failed/skipped: new/saved form omission (fixtures stubbed), and real browser/API confirmation with persistent inactive status after reload.
- [Confirmation dialog screenshot](stu-confirmation.png) captured before the real action on the isolated demo stack.
- Final production source matches the committed implementation; mutation restored. These additions change tests/docs only. The earlier full Maven result remains 476 passed; the full suite was not rerun after these test-only additions.
- Not verified: real reminder email after withdrawal; actual SQL history-query correctness by the mocked history unit test; baseline browser reproduction of the four older Cypress failures. Type-check remains blocked by the 18 reproduced origin/main diagnostics. Full browser suite was not rerun after adding the 3 new cases.
- Human participation: user chose the historical-report mutation and required the three identified gaps to be tested. Final acceptance of this inventory/results is pending; no human sign-off is implied. PR remains open and draft.

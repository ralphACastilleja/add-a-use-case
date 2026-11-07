# **Student account lifecycle**

**Realizes** [UC-STU-deactivate-student](../requirements/use-cases.md#uc-stu-deactivate-student-the-course-admininstructor-deactivates-a-student)
**Depends on** FR-SEC-active-account, BR-student-lifecycle, BR-submission-owed, BR-inactive-peer-evaluations

This flow uses the existing [platform containers](architectural-design.md) and student account model. The existing enabled flag is sufficient. The design requires no schema change.

```mermaid
sequenceDiagram
    actor Instructor as Course admin or instructor
    participant Details as Student details
    participant Security as SecurityConfiguration
    participant Controller as StudentController
    participant Service as StudentService
    participant Repository as StudentRepository
    Instructor->>Details: Choose Deactivate
    Details-->>Instructor: Show consequences and ask for confirmation
    Instructor->>Details: Confirm
    Details->>Security: POST /students/{studentId}/deactivate
    Security->>Controller: Authorized role
    Controller->>Service: Deactivate selected student
    Service->>Repository: Check scope and update enabled flag
    Repository-->>Service: Updated
    Controller-->>Details: 200 with Result success envelope
    Details-->>Instructor: Refresh details and show success
```

## **Account updates and access**

- Register the POST matcher under the configured API base in `SecurityConfiguration` for `ROLE_admin` and `ROLE_instructor`. Resolve instructor scope from the target student's current course section in the service. Check scope before disclosing account status. Use 403 for denied scope, 404 for a missing student, and 409 for an already inactive student.
- Apply the state change transactionally. Make the enabled-to-disabled transition conditional so two confirmations cannot both succeed. Remove the student status dropdown and the generic update path's ability to write the enabled flag.
- On authenticated student requests, resolve the current account using the JWT userId claim and reject inactive accounts at authentication with 401. Preserve existing token validation and login checks.

## **Performance tracking**

- Reuse `SubmissionStatusService` for reminder and owed-item eligibility. Keep the team roster intact and filter the peer evaluation form's targets independently of the general team roster.
- In `EvaluationService`, enforce eligibility on submission even if the form was loaded earlier. For weekly reporting, include an inactive student only if a saved evaluation for that student exists in the requested week. Preserve the existing behavior for active students.

## **Test list**

| Scenario | Source | Expected result |
| --- | --- | --- |
| Confirm deactivation | Steps 3–6 | Warning covers consequences. Confirm sends the dedicated request and shows success after the server updates the account. |
| Cancel warning | 4a | Details remain visible and the client sends no mutation. |
| Preserve records and team | BR-student-lifecycle | Student is inactive and the team, saved reports, and saved evaluations are retained. |
| Recompute owed work | BR-submission-owed | Withdrawn student owes neither item and an active teammate can finish without rating her. |
| Reject inactive evaluation target | BR-inactive-peer-evaluations | Form omits the target and a stale submission is rejected without adding a rating. |
| Keep historical reporting | BR-inactive-peer-evaluations | Inactive student appears with saved ratings for the requested week and is absent for a week without them. |
| Deny existing credentials | 6a, FR-SEC-active-account | A token that worked before deactivation is denied on the next protected request. A new login is denied too. |
| Repeat deactivation | 6b | A second confirmation reports the student already inactive and preserves state. |
| Enforce actor scope | 6c, BR-student-lifecycle | Course admin and assigned instructor can deactivate. Unassigned instructor and student receive a denial with no state change. |
| Enforce concurrent ordering | BR-inactive-peer-evaluations, 6b | Concurrent confirmations produce one success. Evaluation writes serialize with deactivation and cannot commit after it. |
| Deny inactive instructor credentials | FR-SEC-active-account | A previously issued instructor token is rejected after the account becomes inactive. |
| Prevent generic edit bypass | Steps 3–6 | Generic profile updates cannot change account status. |

## **Class boundaries**

```mermaid
classDiagram
    StudentController --> StudentService
    StudentService --> StudentRepository
    StudentRepository --> Student
    SecurityConfiguration --> StudentRepository : current account check
    EvaluationService --> Student : target eligibility
    SubmissionStatusService --> Student : owed work eligibility
```

Use the existing controller response and exception conventions for the dedicated action.
Keep account status changes out of generic profile updates while preserving name and email updates.
Reactivation remains a separate use case. The retained team membership remains subject to any explicit team reassignment made while the account is inactive.

## **Consistency and authorization decisions**

- Obtain caller identity and course scope through UserUtils. A course admin must own the target student's course. An instructor must be assigned to the target student's current course section. Resolve scope and change status in one transaction under a student row lock so reassignment cannot invalidate the checked ownership before the update.
- Evaluation writes and deactivation acquire the same target student row lock before checking enabled status. A write that commits first remains saved history. A deactivation that commits first causes the subsequent evaluation write to fail. Apply this to creation and updates submitted after deactivation.
- The shared account-status check covers both student and instructor tokens under FR-SEC-active-account, using the existing account discriminator and userId. Include an inactive-instructor token regression test. This does not add an instructor lifecycle action.
- Use the existing Result success envelope and centralized error handling. Include cross-course admin denial, concurrent confirmations, and concurrent evaluation writes in the backend acceptance tests.

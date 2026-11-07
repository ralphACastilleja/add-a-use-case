package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import team.projectpulse.evaluation.EvaluationService;
import team.projectpulse.evaluation.PeerEvaluation;
import team.projectpulse.system.exception.StudentAlreadyInactiveException;
import team.projectpulse.system.exception.PeerEvaluationIllegalArgumentException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.team.Team;
import team.projectpulse.team.TeamService;
import team.projectpulse.user.UserService;
import team.projectpulse.user.resetpassword.ResetPasswordToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** UC-STU-deactivate-student: real writers must not restore a stale enabled flag.
 * Independent transactions are deliberately interleaved without timing-dependent sleeps:
 * writer reads active -> status transaction commits inactive -> writer mutates and commits.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentStaleWriterIntegrationTest extends AbstractIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired StudentService studentService;
    @Autowired TeamService teamService;
    @Autowired UserService userService;
    @Autowired StudentRepository studentRepository;
    @Autowired EvaluationService evaluationService;

    enum Writer { PROFILE, ASSIGN_TEAM, REMOVE_TEAM, TRANSFER_TEAM, RESET_PASSWORD }
    record Fixture(Integer studentId, Integer courseId, Integer instructorId,
                   Integer teamId, Integer otherTeamId, Integer destinationId, String email, String token) {}

    @ParameterizedTest(name = "{0} preserves disabled status and its intended update")
    @EnumSource(Writer.class)
    void staleWriterDoesNotReactivateStudent(Writer writer) {
        Fixture fixture = transaction().execute(status -> createFixture());
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("student", null,
                            List.of(new SimpleGrantedAuthority("ROLE_student"))));
            transaction().executeWithoutResult(status -> {
                Student stale = entityManager.find(Student.class, fixture.studentId());
                assertThat(stale.isEnabled()).isTrue();
                // Represents a status-only transaction before the dedicated action exists.
                transaction().executeWithoutResult(other ->
                        entityManager.find(Student.class, fixture.studentId()).setEnabled(false));
                assertThat(stale.isEnabled()).isTrue(); // Prove the old snapshot is still present.
                switch (writer) {
                    case PROFILE -> studentService.updateStudent(fixture.studentId(),
                            new Student("changed", "Changed", "Name", fixture.email(), "unused", true, "student"));
                    case ASSIGN_TEAM -> teamService.assignStudentToTeam(fixture.otherTeamId(), fixture.studentId());
                    case REMOVE_TEAM -> teamService.removeStudentFromTeam(fixture.teamId(), fixture.studentId());
                    case TRANSFER_TEAM -> teamService.transferTeamToAnotherSection(fixture.teamId(), fixture.destinationId());
                    case RESET_PASSWORD -> userService.resetPassword(Map.of("email", fixture.email(),
                            "token", fixture.token(), "newPassword", "Changed123"));
                }
            });
            transaction().executeWithoutResult(status -> {
                Student saved = entityManager.find(Student.class, fixture.studentId());
                assertThat(saved.isEnabled()).as("%s must not restore enabled=true", writer).isFalse();
                switch (writer) {
                    case PROFILE -> assertThat(saved.getFirstName()).isEqualTo("Changed");
                    case ASSIGN_TEAM -> assertThat(saved.getTeam().getTeamId()).isEqualTo(fixture.otherTeamId());
                    case REMOVE_TEAM -> assertThat(saved.getTeam()).isNull();
                    case TRANSFER_TEAM -> assertThat(saved.getSection().getSectionId()).isEqualTo(fixture.destinationId());
                    case RESET_PASSWORD -> assertThat(saved.getPassword()).isNotEqualTo("Original123");
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
            cleanup(fixture);
        }
    }

    @Test
    void lockedReadRehydratesAnAlreadyManagedActiveSnapshot() {
        Fixture f = transaction().execute(status -> createFixture());
        try {
            transaction().executeWithoutResult(status -> {
                Student stale = entityManager.find(Student.class, f.studentId());
                transaction().executeWithoutResult(other -> entityManager.find(Student.class, f.studentId()).setEnabled(false));
                assertThat(stale.isEnabled()).isTrue();
                Student current = studentRepository.findByIdForUpdate(f.studentId()).orElseThrow();
                assertThat(current).isNotSameAs(stale);
                assertThat(current.isEnabled()).isFalse();
            });
        } finally { cleanup(f); }
    }

    enum Actor { OWNING_ADMIN, ASSIGNED_INSTRUCTOR, FOREIGN_ADMIN, UNASSIGNED_INSTRUCTOR, STUDENT }

    @ParameterizedTest
    @EnumSource(Actor.class)
    void lifecycleScopeComesFromOwnershipOrAssignment(Actor actor) {
        Fixture own = transaction().execute(status -> createFixture());
        Fixture other = transaction().execute(status -> createFixture());
        try {
            String role = switch (actor) {
                case OWNING_ADMIN, FOREIGN_ADMIN -> "ROLE_admin";
                case ASSIGNED_INSTRUCTOR, UNASSIGNED_INSTRUCTOR -> "ROLE_instructor";
                case STUDENT -> "ROLE_student";
            };
            Integer id = switch (actor) {
                case OWNING_ADMIN, ASSIGNED_INSTRUCTOR -> own.instructorId();
                case FOREIGN_ADMIN, UNASSIGNED_INSTRUCTOR -> other.instructorId();
                case STUDENT -> own.studentId();
            };
            authenticate(id, role);
            boolean allowed = actor == Actor.OWNING_ADMIN || actor == Actor.ASSIGNED_INSTRUCTOR;
            if (allowed) {
                transaction().executeWithoutResult(status -> studentService.deactivateStudent(own.studentId()));
            } else {
                assertThatThrownBy(() -> transaction().executeWithoutResult(status -> studentService.deactivateStudent(own.studentId())))
                        .isInstanceOf(AccessDeniedException.class);
            }
            transaction().executeWithoutResult(status -> {
                Student saved = entityManager.find(Student.class, own.studentId());
                assertThat(saved.isEnabled()).isEqualTo(!allowed);
                assertThat(saved.getTeam().getTeamId()).isEqualTo(own.teamId());
            });
        } finally { SecurityContextHolder.clearContext(); cleanup(own); cleanup(other); }
    }

    @Test
    void scopedLookupRejectsAnInstructorAfterConcurrentSectionTransfer() {
        Fixture own = transaction().execute(status -> createFixture());
        Fixture other = transaction().execute(status -> createFixture());
        try {
            authenticate(own.instructorId(), "ROLE_instructor");
            transaction().executeWithoutResult(status -> {
                Student stale = entityManager.find(Student.class, own.studentId());
                Integer oldSection = stale.getSection().getSectionId();
                transaction().executeWithoutResult(change -> {
                    Section destination = entityManager.find(Section.class, own.destinationId());
                    destination.getInstructors().clear();
                    destination.getInstructors().add(entityManager.find(Instructor.class, other.instructorId()));
                    teamService.transferTeamToAnotherSection(own.teamId(), own.destinationId());
                });
                assertThat(stale.getSection().getSectionId()).isEqualTo(oldSection);
                assertThatThrownBy(() -> studentService.deactivateStudent(own.studentId()))
                        .isInstanceOf(AccessDeniedException.class);
                // The expected service refusal marks this outer transaction rollback-only.
                status.setRollbackOnly();
            });
            transaction().executeWithoutResult(status -> {
                Student saved = entityManager.find(Student.class, own.studentId());
                assertThat(saved.isEnabled()).isTrue();
                assertThat(saved.getSection().getSectionId()).isEqualTo(own.destinationId());
            });
        } finally { SecurityContextHolder.clearContext(); cleanup(own); cleanup(other); }
    }

    @Test
    void concurrentConfirmationsProduceExactlyOneSuccess() throws Exception {
        Fixture f = transaction().execute(status -> createFixture());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Callable<String> confirmation = () -> {
                authenticate(f.instructorId(), "ROLE_admin");
                try {
                    await(start);
                    studentService.deactivateStudent(f.studentId());
                    return "success";
                } catch (StudentAlreadyInactiveException expected) {
                    return "already inactive";
                } finally { SecurityContextHolder.clearContext(); }
            };
            Future<String> first = workers.submit(confirmation);
            Future<String> second = workers.submit(confirmation);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "already inactive");
        } finally { cleanup(f); }
    }

    enum EvaluationWrite { CREATE, UPDATE }

    @ParameterizedTest
    @EnumSource(EvaluationWrite.class)
    void deactivationFirstRejectsStaleEvaluationWrite(EvaluationWrite write) {
        Fixture f = transaction().execute(status -> createFixture());
        try {
            Integer savedId = write == EvaluationWrite.UPDATE ? saveEvaluation(f) : null;
            authenticate(f.instructorId(), "ROLE_admin");
            assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
                Student stale = entityManager.find(Student.class, f.studentId());
                transaction().executeWithoutResult(other -> studentService.deactivateStudent(f.studentId()));
                assertThat(stale.isEnabled()).isTrue();
                writeEvaluation(f, savedId);
            })).isInstanceOf(PeerEvaluationIllegalArgumentException.class);
            transaction().executeWithoutResult(status -> {
                List<PeerEvaluation> saved = entityManager.createQuery(
                        "select e from PeerEvaluation e where e.evaluatee.id = :id", PeerEvaluation.class)
                        .setParameter("id", f.studentId()).getResultList();
                assertThat(saved).hasSize(write == EvaluationWrite.UPDATE ? 1 : 0);
                if (savedId != null) assertThat(saved.getFirst().getPublicComment()).isEqualTo("history");
            });
        } finally { SecurityContextHolder.clearContext(); cleanup(f); }
    }

    @ParameterizedTest
    @EnumSource(EvaluationWrite.class)
    void evaluationWriteHoldsDeactivationUntilItsCommit(EvaluationWrite write) throws Exception {
        Fixture f = transaction().execute(status -> createFixture());
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Integer savedId = write == EvaluationWrite.UPDATE ? saveEvaluation(f) : null;
            Future<?> evaluation = workers.submit(() -> transaction().executeWithoutResult(status -> {
                writeEvaluation(f, savedId);
                entityManager.flush();
                written.countDown();
                await(commit);
            }));
            await(written);
            Future<?> deactivation = workers.submit(() -> {
                authenticate(f.instructorId(), "ROLE_admin");
                try {
                    confirmationStarted.countDown();
                    studentService.deactivateStudent(f.studentId());
                } finally { SecurityContextHolder.clearContext(); }
            });
            try {
                await(confirmationStarted);
                assertThatThrownBy(() -> deactivation.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally { commit.countDown(); }
            evaluation.get(10, TimeUnit.SECONDS);
            deactivation.get(10, TimeUnit.SECONDS);
            transaction().executeWithoutResult(status -> {
                assertThat(entityManager.find(Student.class, f.studentId()).isEnabled()).isFalse();
                List<PeerEvaluation> saved = entityManager.createQuery(
                        "select e from PeerEvaluation e where e.evaluatee.id = :id", PeerEvaluation.class)
                        .setParameter("id", f.studentId()).getResultList();
                assertThat(saved).hasSize(1);
                assertThat(saved.getFirst().getPublicComment()).isEqualTo("changed");
            });
        } finally { commit.countDown(); cleanup(f); }
    }

    private Integer saveEvaluation(Fixture f) {
        return transaction().execute(status -> {
            Student student = entityManager.find(Student.class, f.studentId());
            PeerEvaluation evaluation = new PeerEvaluation("2023-W32", student, student, List.of(), "history", "private history");
            entityManager.persist(evaluation);
            return evaluation.getPeerEvaluationId();
        });
    }

    private void writeEvaluation(Fixture f, Integer savedId) {
        Student student = entityManager.find(Student.class, f.studentId());
        PeerEvaluation submission = new PeerEvaluation("2023-W32", student, student, List.of(), "changed", "private");
        if (savedId == null) evaluationService.addPeerEvaluation(submission);
        else evaluationService.updatePeerEvaluation(savedId, submission);
    }

    private void authenticate(Integer userId, String role) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("test")
                .claim("userId", userId).claim("authorities", role).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(role))));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out coordinating transactions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void cleanup(Fixture f) {
        transaction().executeWithoutResult(status -> {
            entityManager.createQuery("delete from PeerEvaluation e where e.evaluatee.id = :id")
                    .setParameter("id", f.studentId()).executeUpdate();
            entityManager.createQuery("delete from ResetPasswordToken t where t.email = :email")
                    .setParameter("email", f.email()).executeUpdate();
            entityManager.remove(entityManager.find(Course.class, f.courseId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(Instructor.class, f.instructorId()));
        });
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(20);
        return template;
    }

    private Fixture createFixture() {
        String email = "stale-writer-" + java.util.UUID.randomUUID() + "@example.test";
        Instructor instructor = new Instructor(email + "-teacher", "Test", "Teacher", email + ".teacher", "Original123", true, "instructor");
        entityManager.persist(instructor);
        Course course = new Course("Stale writer test", "isolated fixture");
        course.setCourseAdmin(instructor);
        entityManager.persist(course);
        Section source = new Section();
        Section destination = new Section();
        course.addSection(source);
        course.addSection(destination);
        source.setActiveWeeks(List.of("2023-W32"));
        source.getInstructors().add(instructor);
        destination.getInstructors().add(instructor);
        entityManager.persist(source);
        entityManager.persist(destination);
        Team team = new Team("Source", "", "");
        Team otherTeam = new Team("Other", "", "");
        source.addTeam(team);
        source.addTeam(otherTeam);
        team.addInstructor(instructor);
        entityManager.persist(team);
        entityManager.persist(otherTeam);
        Student student = new Student(email, "Test", "Student", email, "Original123", true, "student");
        source.addStudent(student);
        team.addStudent(student);
        entityManager.persist(student);
        ResetPasswordToken token = new ResetPasswordToken(email);
        entityManager.persist(token);
        return new Fixture(student.getId(), course.getCourseId(), instructor.getId(),
                team.getTeamId(), otherTeam.getTeamId(), destination.getSectionId(), email, token.getToken());
    }
}

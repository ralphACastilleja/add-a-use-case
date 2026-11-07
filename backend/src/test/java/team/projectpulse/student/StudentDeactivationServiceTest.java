package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.system.exception.StudentAlreadyInactiveException;
import team.projectpulse.user.userinvitation.UserInvitationService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/** UC-STU-deactivate-student: service behavior for 6b and 6c. Persistence/scope races are integration tests. */
@ExtendWith(MockitoExtension.class)
class StudentDeactivationServiceTest {
    @Mock StudentRepository studentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock UserInvitationService userInvitationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserUtils userUtils;
    @InjectMocks StudentService service;

    Student student;

    @BeforeEach
    void setUp() {
        student = new Student("test", "Test", "Student", "test@example.test", "Original123", true, "student");
    }

    @Test
    void owningAdminDeactivatesWithoutDeletingOrSavingOverOtherFields() {
        given(userUtils.hasRole("ROLE_admin")).willReturn(true);
        given(userUtils.getUserId()).willReturn(7);
        given(studentRepository.findInDeactivationScopeForUpdate(3, 7, true)).willReturn(Optional.of(student));
        assertThat(service.deactivateStudent(3)).isSameAs(student);
        assertThat(student.isEnabled()).isFalse();
        assertThat(student.getEmail()).isEqualTo("test@example.test");
        verify(studentRepository, never()).save(any());
        verify(studentRepository, never()).deleteById(any());
    }

    @Test
    void assignedInstructorDeactivates() {
        given(userUtils.hasRole("ROLE_admin")).willReturn(false);
        given(userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(userUtils.getUserId()).willReturn(7);
        given(studentRepository.findInDeactivationScopeForUpdate(3, 7, false)).willReturn(Optional.of(student));
        service.deactivateStudent(3);
        assertThat(student.isEnabled()).isFalse();
    }

    @Test
    void alreadyInactiveIsAConflict() {
        given(userUtils.hasRole("ROLE_admin")).willReturn(true);
        given(userUtils.getUserId()).willReturn(7);
        student.setEnabled(false);
        given(studentRepository.findInDeactivationScopeForUpdate(3, 7, true)).willReturn(Optional.of(student));
        assertThatThrownBy(() -> service.deactivateStudent(3)).isInstanceOf(StudentAlreadyInactiveException.class);
    }

    @Test
    void deniedScopeDoesNotDiscloseStatus() {
        given(userUtils.hasRole("ROLE_admin")).willReturn(true);
        given(userUtils.getUserId()).willReturn(7);
        given(studentRepository.existsById(3)).willReturn(true);
        assertThatThrownBy(() -> service.deactivateStudent(3)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingStudentIsNotFound() {
        given(userUtils.hasRole("ROLE_admin")).willReturn(true);
        given(userUtils.getUserId()).willReturn(7);
        assertThatThrownBy(() -> service.deactivateStudent(3)).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void studentRoleCannotUseLifecycleAction() {
        assertThatThrownBy(() -> service.deactivateStudent(3)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void missingIdentityIsDeniedBeforeLookup() {
        given(userUtils.getUserId()).willReturn(null);
        given(userUtils.hasRole("ROLE_admin")).willReturn(true);
        assertThatThrownBy(() -> service.deactivateStudent(3)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(studentRepository);
    }
}

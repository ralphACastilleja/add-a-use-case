package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.AccessDeniedException;
import team.projectpulse.student.converter.*;
import team.projectpulse.system.exception.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentDeactivationControllerTest {
    @Test
    void dedicatedActionUsesStandardSuccessAndErrorEnvelopes() throws Exception {
        StudentService service = mock(StudentService.class);
        Student student = new Student("test", "Test", "Student", "test@example.test", "password", false, "student");
        student.setId(4);
        var converter = mock(StudentToStudentDtoConverter.class);
        var mvc = MockMvcBuilders.standaloneSetup(new StudentController(service,
                mock(StudentRegistrationToStudentConverter.class), converter, mock(StudentDtoToStudentConverter.class)))
                .addPlaceholderValue("api.endpoint.base-url", "/api/v1")
                .setControllerAdvice(new ExceptionHandlerAdvice()).build();
        when(service.deactivateStudent(4)).thenReturn(student);
        mvc.perform(post("/api/v1/students/4/deactivate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(200));
        verify(converter).convert(student);
        doThrow(new StudentAlreadyInactiveException()).when(service).deactivateStudent(4);
        mvc.perform(post("/api/v1/students/4/deactivate"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        doThrow(new AccessDeniedException("scope")).when(service).deactivateStudent(4);
        mvc.perform(post("/api/v1/students/4/deactivate"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        doThrow(new ObjectNotFoundException("student",4)).when(service).deactivateStudent(4);
        mvc.perform(post("/api/v1/students/4/deactivate"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }
}

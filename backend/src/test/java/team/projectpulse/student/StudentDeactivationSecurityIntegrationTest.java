package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.json.JSONObject;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.user.UserRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class StudentDeactivationSecurityIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;

    private String token(String email) throws Exception {
        var response = mvc.perform(post("/api/v1/users/login").with(httpBasic(email,"123456")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(response).getJSONObject("data").getString("token");
    }

    @Test
    void deactivationRevokesExistingStudentTokenAndNewLoginAndRetainsTeam() throws Exception {
        String admin = token("b.wei@abc.edu"), student = token("j.smith@abc.edu");
        Integer team = students.findById(4).orElseThrow().getTeam().getTeamId();
        mvc.perform(get("/api/v1/students/4").header(HttpHeaders.AUTHORIZATION,student)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(students.findById(4).orElseThrow().getTeam().getTeamId()).isEqualTo(team);
        mvc.perform(get("/api/v1/students/4").header(HttpHeaders.AUTHORIZATION,student)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/login").with(httpBasic("j.smith@abc.edu","123456"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("This student is already deactivated."));
    }

    @Test
    void inactiveInstructorTokenIsRejected() throws Exception {
        String instructor = token("b.gates@abc.edu");
        var account = users.findByEmail("b.gates@abc.edu").orElseThrow();
        account.setEnabled(false); users.flush();
        mvc.perform(post("/api/v1/students/search").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header(HttpHeaders.AUTHORIZATION,instructor)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/login").with(httpBasic("b.gates@abc.edu","123456"))).andExpect(status().isUnauthorized());
    }

    @Test
    void routeAndOwnershipDenialsAndMissingStudent() throws Exception {
        String student = token("j.smith@abc.edu"), foreignAdmin = token("t.cook@abc.edu"), admin=token("b.wei@abc.edu");
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,foreignAdmin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/students/2147483647/deactivate").header(HttpHeaders.AUTHORIZATION,admin)).andExpect(status().isNotFound());
        assertThat(students.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void assignedInstructorCanDeactivate() throws Exception {
        String instructor = token("b.gates@abc.edu");
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,instructor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void unassignedInstructorIsDeniedWithoutStateChange() throws Exception {
        String instructor = token("b.gates@abc.edu");
        students.findById(4).orElseThrow().getSection().getInstructors()
                .removeIf(account -> account.getEmail().equals("b.gates@abc.edu"));
        students.flush();
        mvc.perform(post("/api/v1/students/4/deactivate").header(HttpHeaders.AUTHORIZATION,instructor))
                .andExpect(status().isForbidden());
        assertThat(students.findById(4).orElseThrow().isEnabled()).isTrue();
    }
}

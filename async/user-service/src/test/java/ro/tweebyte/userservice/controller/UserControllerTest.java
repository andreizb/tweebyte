package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import ro.tweebyte.userservice.model.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(MockMvcRequestBuilders.get("/users/" + userId)

                .header("Authorization", "Bearer AUTH_TOKEN")).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void testGetUserSummary() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(MockMvcRequestBuilders.get("/users/summary/" + userId))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void testGetUserSummaryByUserName() throws Exception {
        String userName = "testUser";

        mockMvc.perform(MockMvcRequestBuilders.get("/users/summary/name/" + userName))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void testSearchUser() throws Exception {
        String searchTerm = "testSearch";

        mockMvc.perform(MockMvcRequestBuilders.get("/users/search/" + searchTerm)

                .header("Authorization", "Bearer validToken"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void testUpdateUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(MockMvcRequestBuilders.put("/users/" + userId)

                .param("userName", "newUserName")
                .param("email", "newEmail@example.com")
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

}
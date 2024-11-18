package ro.tweebyte.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testUserRegister() throws Exception {
        MockMultipartFile pictureFile = new MockMultipartFile("picture", "test.jpg", "image/jpeg", "test picture".getBytes());

        UserRegisterRequest request = new UserRegisterRequest();
        request.setEmail("test@test.com");
        request.setPassword("testPassword");
        request.setUserName("testUser");

        mockMvc.perform(MockMvcRequestBuilders.multipart("/auth/register")
                .file(pictureFile)
                .param("userName", request.getUserName())
                .param("password", request.getPassword())
                .param("birthDate", "09/06/2023")
                .param("email", request.getEmail()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(print());
    }

    @Test
    public void testUserLogin() throws Exception {
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("testPassword");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                .contentType("application/json")
                .content(asJsonString(request)))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andDo(print());
    }

    private String asJsonString(final Object obj) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
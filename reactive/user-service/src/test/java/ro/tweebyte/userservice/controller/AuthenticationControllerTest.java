package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.service.AuthenticationService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = AuthenticationController.class, excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
public class AuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticationService authenticationService;

    @BeforeEach
    public void setUp() {
        AuthenticationResponse mockResponse = new AuthenticationResponse("TokenHere");
        given(authenticationService.register(any())).willReturn(Mono.just(mockResponse));
        given(authenticationService.login(any())).willReturn(Mono.just(mockResponse));
    }

    @Test
    public void userLogin() {
        UserLoginRequest request = new UserLoginRequest("user", "pass");
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.csrf())
            .post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.token").isEqualTo("TokenHere");
    }

    @Test
    public void userRegister() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("email", "test@example.com");
        formData.add("password", "password123");
        formData.add("birthDate", "1990-01-01");
        formData.add("userName", "JohnDoe");

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/auth/register")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(formData)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("TokenHere");
    }

}

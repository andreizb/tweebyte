package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.service.UserService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = UserController.class, excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
public class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @MockBean
    private UserDetailsService userDetailsService;

    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        UserDto testUserDto = new UserDto();
        testUserDto.setId(testUserId);
        testUserDto.setUserName("Test User");
        testUserDto.setEmail("test@example.com");
        testUserDto.setBiography("Bio");

        given(userService.getUserProfile(any(UUID.class), any())).willReturn(Mono.just(testUserDto));
        given(userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(testUserDto));
        given(userService.getUserSummaryByUserName(any(String.class))).willReturn(Mono.just(testUserDto));
        given(userService.searchUser(any(String.class))).willReturn(Flux.just(testUserDto));
        given(userService.updateUser(eq(testUserId), any(UserUpdateRequest.class))).willReturn(Mono.empty());
    }

    @Test
    @WithMockUser
    public void getUserProfile() {
        webTestClient.get().uri("/users/{userId}", testUserId)
                .header("Authorization", "Bearer someToken")  // Add the Authorization header
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(testUserId.toString());
    }

    @Test
    @WithMockUser
    public void getUserSummary() {
        webTestClient.get().uri("/users/summary/{userId}", testUserId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(testUserId.toString());
    }

    @Test
    @WithMockUser
    public void getUserSummaryByUserName() {
        String testUserName = "Test User";
        webTestClient.get().uri("/users/summary/name/{userName}", testUserName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.user_name").isEqualTo(testUserName);
    }

    @Test
    @WithMockUser
    public void searchUser() {
        String searchTerm = "search";
        webTestClient.get().uri("/users/search/{searchTerm}", searchTerm)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(UserDto.class).hasSize(1);
    }

}

package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
@WebFluxTest(controllers = UserController.class)
public class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

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
    public void getUserProfile() {
        webTestClient.get().uri("/users/{userId}", testUserId)
                .header("Authorization", "Bearer someToken")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(testUserId.toString());
    }

    @Test
    public void getUserSummary() {
        webTestClient.get().uri("/users/summary/{userId}", testUserId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(testUserId.toString());
    }

    @Test
    public void getUserSummaryByUserName() {
        String testUserName = "Test User";
        webTestClient.get().uri("/users/summary/name/{userName}", testUserName)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.user_name").isEqualTo(testUserName);
    }

    @Test
    public void searchUser() {
        String searchTerm = "search";
        webTestClient.get().uri("/users/search/{searchTerm}", searchTerm)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserDto.class).hasSize(1);
    }

    @Test
    public void testUpdateUser() {
        // multipart PUT /users/{id}
        // returns 204 No Content. The setUp() stubs userService.updateUser to Mono.empty().
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("userName", "newUserName");
        formData.add("email", "newEmail@example.com");

        webTestClient.put().uri("/users/{userId}", testUserId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(formData)
                .exchange()
                .expectStatus().isNoContent();
    }

}

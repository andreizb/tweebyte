package ro.tweebyte.tweetservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserClientTest {

    @InjectMocks
    private UserClient userClient;

    @Mock
    private WebClient webClientMock;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;

    @Mock
    private WebClient.ResponseSpec responseSpecMock;

    @BeforeEach
    public void setUp() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(String.class), any(Object[].class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.onStatus(any(), any())).thenReturn(responseSpecMock);
    }

    @Test
    public void testGetUserSummaryByName_success() {
        String userName = "testUser";
        UserDto expectedUser = new UserDto();
        expectedUser.setUserName(userName);

        when(responseSpecMock.bodyToMono(UserDto.class)).thenReturn(Mono.just(expectedUser));

        StepVerifier.create(userClient.getUserSummary(userName))
            .expectNext(expectedUser)
            .verifyComplete();

        verify(webClientMock).get();
        verify(requestHeadersUriSpecMock).uri(eq("/users/summary/name/{userName}"), eq(userName));
        verify(requestHeadersSpecMock).retrieve();
        verify(responseSpecMock).bodyToMono(UserDto.class);
    }

    @Test
    public void testGetUserSummaryById_success() {
        UUID userId = UUID.randomUUID();
        UserDto expectedUser = new UserDto();
        expectedUser.setId(userId);

        when(responseSpecMock.bodyToMono(UserDto.class)).thenReturn(Mono.just(expectedUser));

        StepVerifier.create(userClient.getUserSummary(userId))
            .expectNext(expectedUser)
            .verifyComplete();

        verify(webClientMock).get();
        verify(requestHeadersUriSpecMock).uri(eq("/users/summary/{userId}"), eq(userId));
        verify(requestHeadersSpecMock).retrieve();
        verify(responseSpecMock).bodyToMono(UserDto.class);
    }

    @Test
    public void testGetUserSummaryByName_userNotFound() {
        String userName = "nonexistentUser";

        when(responseSpecMock.bodyToMono(UserDto.class)).thenReturn(Mono.error(new UserNotFoundException("User not found for name: " + userName)));

        StepVerifier.create(userClient.getUserSummary(userName))
            .expectErrorMatches(throwable -> throwable instanceof UserNotFoundException &&
                throwable.getMessage().equals("User not found for name: " + userName))
            .verify();

        verify(webClientMock).get();
        verify(requestHeadersUriSpecMock).uri(eq("/users/summary/name/{userName}"), eq(userName));
        verify(requestHeadersSpecMock).retrieve();
    }

    @Test
    public void testGetUserSummaryById_userNotFound() {
        UUID userId = UUID.randomUUID();

        when(responseSpecMock.bodyToMono(UserDto.class)).thenReturn(Mono.error(new UserNotFoundException("User not found for id: " + userId)));

        StepVerifier.create(userClient.getUserSummary(userId))
            .expectErrorMatches(throwable -> throwable instanceof UserNotFoundException &&
                throwable.getMessage().equals("User not found for id: " + userId))
            .verify();

        verify(webClientMock).get();
        verify(requestHeadersUriSpecMock).uri(eq("/users/summary/{userId}"), eq(userId));
        verify(requestHeadersSpecMock).retrieve();
    }
}

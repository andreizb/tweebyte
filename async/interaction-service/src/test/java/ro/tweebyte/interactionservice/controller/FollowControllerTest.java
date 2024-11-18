package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WebAppConfiguration
class FollowControllerTest {

    private static final String BASE_URL = "/follows";
    private final UUID userId = UUID.randomUUID();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockBean
    private FollowService followService;

    @BeforeEach
    public void setup() {
        CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test@test.com");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    void getFollowers() throws Exception {
        UUID userId = UUID.randomUUID();
        List<FollowDto> mockFollowers = Collections.emptyList();
        when(followService.getFollowers(userId)).thenReturn(CompletableFuture.completedFuture(mockFollowers));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{userId}/followers", userId))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(followService).getFollowers(userId);
    }

    @Test
    void getFollowing() throws Exception {
        UUID userId = UUID.randomUUID();
        List<FollowDto> mockFollowing = Collections.emptyList();
        when(followService.getFollowing(userId)).thenReturn(CompletableFuture.completedFuture(mockFollowing));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{userId}/following", userId))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(followService).getFollowing(userId);
    }

    @Test
    void getFollowersCount() throws Exception {
        UUID userId = UUID.randomUUID();
        when(followService.getFollowersCount(userId)).thenReturn(CompletableFuture.completedFuture(10L));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{userId}/followers/count", userId))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().string("10"));

        verify(followService).getFollowersCount(userId);
    }

    @Test
    void getFollowersIdentifiers() throws Exception {
        UUID userId = UUID.randomUUID();
        List<UUID> mockIdentifiers = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(followService.getFollowedIdentifiers(userId)).thenReturn(CompletableFuture.completedFuture(mockIdentifiers));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{userId}/followers/identifiers", userId))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(followService).getFollowedIdentifiers(userId);
    }

    @Test
    void getFollowingCount() throws Exception {
        UUID userId = UUID.randomUUID();
        when(followService.getFollowingCount(userId)).thenReturn(CompletableFuture.completedFuture(15L));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{userId}/following/count", userId))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().string("15"));

        verify(followService).getFollowingCount(userId);
    }

    @Test
    void getFollowRequests() throws Exception {
        List<FollowDto> mockRequests = Collections.emptyList();
        when(followService.getFollowRequests(userId)).thenReturn(CompletableFuture.completedFuture(mockRequests));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/" + userId + "/requests"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(followService).getFollowRequests(userId);
    }

    @Test
    void follow() throws Exception {
        UUID followedId = UUID.randomUUID();
        FollowDto mockFollowDto = new FollowDto();
        when(followService.follow(userId, followedId)).thenReturn(CompletableFuture.completedFuture(mockFollowDto));

        MvcResult result = mockMvc.perform(post(BASE_URL + "/{followedId}", followedId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isNoContent());

        verify(followService).follow(userId, followedId);
    }

    @Test
    void updateFollowRequest() throws Exception {
        UUID followRequestId = UUID.randomUUID();
        FollowEntity.Status status = FollowEntity.Status.ACCEPTED;
        when(followService.updateFollowRequest(userId, followRequestId, status))
            .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put(BASE_URL + "/{followRequestId}/{status}", followRequestId, status)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isNoContent());

        verify(followService).updateFollowRequest(userId, followRequestId, status);
    }

    @Test
    void unfollow() throws Exception {
        UUID followedId = UUID.randomUUID();
        when(followService.unfollow(userId, followedId)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(delete(BASE_URL + "/{followedId}", followedId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isNoContent());

        verify(followService).unfollow(userId, followedId);
    }
}

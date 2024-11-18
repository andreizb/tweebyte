package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.service.LikeService;
import ro.tweebyte.interactionservice.service.RecommendationService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class RecommendationControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private RecommendationService recommendationService;

    private final UUID userId = UUID.randomUUID();

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
    public void findFollowRecommendations() throws Exception {
        List<UserDto> expectedRecommendations = Collections.singletonList(new UserDto());
        when(recommendationService.recommendUsersToFollow(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(expectedRecommendations));

        mockMvc.perform(get("/recommendations/follow")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(recommendationService).recommendUsersToFollow(any(UUID.class), any());
    }

    @Test
    public void findHashtagRecommendations() throws Exception {
        List<TweetDto.HashtagDto> expectedHashtags = Collections.singletonList(new TweetDto.HashtagDto());
        when(recommendationService.computePopularHashtags()).thenReturn(CompletableFuture.completedFuture(expectedHashtags));

        mockMvc.perform(get("/recommendations/hashtags")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(recommendationService).computePopularHashtags();
    }

    @Test
    void findTweetSummaries() throws Exception {
        List<UUID> tweetIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<TweetSummaryDto> expectedSummaries = List.of(
            new TweetSummaryDto(UUID.randomUUID(), 10L, 5L, 3L, null),
            new TweetSummaryDto(UUID.randomUUID(), 20L, 15L, 10L, null)
        );

        when(recommendationService.findTweetSummaries(tweetIds))
            .thenReturn(CompletableFuture.completedFuture(expectedSummaries));

        mockMvc.perform(asyncDispatch(
                mockMvc.perform(post("/recommendations/tweet/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + tweetIds.get(0) + "\",\"" + tweetIds.get(1) + "\"]"))
                    .andReturn()
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(expectedSummaries.size()))
            .andExpect(jsonPath("$[0].tweet_id").value(expectedSummaries.get(0).getTweetId().toString()))
            .andExpect(jsonPath("$[1].tweet_id").value(expectedSummaries.get(1).getTweetId().toString()));

        verify(recommendationService).findTweetSummaries(tweetIds);
    }
}

package ro.tweebyte.interactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WebAppConfiguration
class RetweetControllerTest {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@MockBean
	private RetweetService retweetService;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final UUID userId = UUID.randomUUID();
	private final UUID tweetId = UUID.randomUUID();
	private final UUID retweetId = UUID.randomUUID();

	@BeforeEach
	void setup() {
		CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test@test.com");
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList());
		SecurityContextHolder.getContext().setAuthentication(authentication);

		mockMvc = MockMvcBuilders.webAppContextSetup(context)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.build();
	}

	@Test
	void testCreateRetweet() throws Exception {
		RetweetCreateRequest request = new RetweetCreateRequest().setOriginalTweetId(tweetId).setContent("Retweet content");
		RetweetDto expectedRetweet = new RetweetDto().setId(retweetId).setContent(request.getContent());

		when(retweetService.createRetweet(any())).thenReturn(CompletableFuture.completedFuture(expectedRetweet));

		MvcResult result = mockMvc.perform(post("/retweets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(retweetId.toString()))
			.andExpect(jsonPath("$.content").value("Retweet content"));

		verify(retweetService).createRetweet(any());
	}

	@Test
	void testUpdateRetweet() throws Exception {
		RetweetUpdateRequest request = new RetweetUpdateRequest().setContent("Updated content");

		when(retweetService.updateRetweet(any())).thenReturn(CompletableFuture.completedFuture(null));

		MvcResult result = mockMvc.perform(put("/retweets/{retweetId}", retweetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk());

		verify(retweetService).updateRetweet(any());
	}

	@Test
	void testDeleteRetweet() throws Exception {
		when(retweetService.deleteRetweet(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

		MvcResult result = mockMvc.perform(delete("/retweets/{retweetId}", retweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk());

		verify(retweetService).deleteRetweet(eq(retweetId), any());
	}

	@Test
	void testGetRetweetsByUser() throws Exception {
		List<RetweetDto> retweets = Collections.singletonList(new RetweetDto().setId(retweetId).setContent("User retweet"));

		when(retweetService.getRetweetsByUser(eq(userId))).thenReturn(CompletableFuture.completedFuture(retweets));

		MvcResult result = mockMvc.perform(get("/retweets/user/{userId}", userId))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(retweetId.toString()))
			.andExpect(jsonPath("$[0].content").value("User retweet"));

		verify(retweetService).getRetweetsByUser(userId);
	}

	@Test
	void testGetAllRetweetsOfTweet() throws Exception {
		List<RetweetDto> retweets = Collections.singletonList(new RetweetDto().setId(retweetId).setContent("Tweet retweet"));

		when(retweetService.getRetweetsOfTweet(eq(tweetId))).thenReturn(CompletableFuture.completedFuture(retweets));

		MvcResult result = mockMvc.perform(get("/retweets/tweet/{tweetId}", tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(retweetId.toString()))
			.andExpect(jsonPath("$[0].content").value("Tweet retweet"));

		verify(retweetService).getRetweetsOfTweet(tweetId);
	}

	@Test
	void testGetRetweetCountOfTweet() throws Exception {
		when(retweetService.getRetweetCountOfTweet(eq(tweetId))).thenReturn(CompletableFuture.completedFuture(3L));

		MvcResult result = mockMvc.perform(get("/retweets/tweet/{tweetId}/count", tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").value(3));

		verify(retweetService).getRetweetCountOfTweet(tweetId);
	}
}

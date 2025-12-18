package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WebAppConfiguration
class LikeControllerTest {

	private static final String BASE_URL = "/likes";
	private final UUID userId = UUID.randomUUID();

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@MockBean
	private LikeService likeService;

	@BeforeEach
	public void setup() {
		mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.build();
	}

	@Test
	void getUserLikes() throws Exception {
		List<LikeDto> mockLikes = Collections.emptyList();
		when(likeService.getUserLikes(userId)).thenReturn(CompletableFuture.completedFuture(mockLikes));

		MvcResult result = mockMvc.perform(get(BASE_URL + "/user/{userId}", userId))
				.andExpect(status().isOk())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		verify(likeService).getUserLikes(userId);
	}

	@Test
	void getTweetLikes() throws Exception {
		UUID tweetId = UUID.randomUUID();
		List<LikeDto> mockLikes = Collections.emptyList();
		when(likeService.getTweetLikes(tweetId)).thenReturn(CompletableFuture.completedFuture(mockLikes));

		MvcResult result = mockMvc.perform(get(BASE_URL + "/tweet/{tweetId}", tweetId))
				.andExpect(status().isOk())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		verify(likeService).getTweetLikes(tweetId);
	}

	@Test
	void getTweetLikesCount() throws Exception {
		UUID tweetId = UUID.randomUUID();
		when(likeService.getTweetLikesCount(tweetId)).thenReturn(CompletableFuture.completedFuture(20L));

		MvcResult result = mockMvc.perform(get(BASE_URL + "/{tweetId}/count", tweetId))
				.andExpect(status().isOk())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(content().string("20"));

		verify(likeService).getTweetLikesCount(tweetId);
	}

	@Test
	void likeTweet() throws Exception {
		UUID tweetId = UUID.randomUUID();
		LikeDto mockLikeDto = new LikeDto();
		when(likeService.likeTweet(userId, tweetId)).thenReturn(CompletableFuture.completedFuture(mockLikeDto));

		MvcResult result = mockMvc.perform(post(BASE_URL + "/{userId}/tweets/{tweetId}", userId, tweetId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk());

		verify(likeService).likeTweet(userId, tweetId);
	}

	@Test
	void unlikeTweet() throws Exception {
		UUID tweetId = UUID.randomUUID();
		when(likeService.unlikeTweet(userId, tweetId)).thenReturn(CompletableFuture.completedFuture(null));

		MvcResult result = mockMvc.perform(delete(BASE_URL + "/{userId}/tweets/{tweetId}", userId, tweetId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isNoContent());

		verify(likeService).unlikeTweet(userId, tweetId);
	}

	@Test
	void likeReply() throws Exception {
		UUID replyId = UUID.randomUUID();
		LikeDto mockLikeDto = new LikeDto();
		when(likeService.likeReply(userId, replyId)).thenReturn(CompletableFuture.completedFuture(mockLikeDto));

		MvcResult result = mockMvc.perform(post(BASE_URL + "/{userId}/replies/{replyId}", userId, replyId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk());

		verify(likeService).likeReply(userId, replyId);
	}

	@Test
	void unlikeReply() throws Exception {
		UUID replyId = UUID.randomUUID();
		when(likeService.unlikeReply(userId, replyId)).thenReturn(CompletableFuture.completedFuture(null));

		MvcResult result = mockMvc.perform(delete(BASE_URL + "/{userId}/replies/{replyId}", userId, replyId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isNoContent());

		verify(likeService).unlikeReply(userId, replyId);
	}
}

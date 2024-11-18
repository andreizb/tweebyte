package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TweetSummaryDtoTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
	}

	@Test
	void testSerialization() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());

		TweetSummaryDto tweetSummaryDto = TweetSummaryDto.builder()
			.tweetId(tweetId)
			.likesCount(10L)
			.repliesCount(5L)
			.retweetsCount(3L)
			.topReply(reply)
			.build();

		String json = objectMapper.writeValueAsString(tweetSummaryDto);

		assertTrue(json.contains("\"tweet_id\":\"" + tweetId + "\""));
		assertTrue(json.contains("\"likes_count\":10"));
		assertTrue(json.contains("\"replies_count\":5"));
		assertTrue(json.contains("\"retweets_count\":3"));
		assertTrue(json.contains("\"top_reply\""));
	}

	@Test
	void testDeserialization() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		String json = String.format("{\"tweet_id\":\"%s\",\"likes_count\":10,\"replies_count\":5,\"retweets_count\":3," +
			"\"top_reply\":{\"id\":\"%s\"}}", tweetId, replyId);

		TweetSummaryDto tweetSummaryDto = objectMapper.readValue(json, TweetSummaryDto.class);

		assertEquals(tweetId, tweetSummaryDto.getTweetId());
		assertEquals(10L, tweetSummaryDto.getLikesCount());
		assertEquals(5L, tweetSummaryDto.getRepliesCount());
		assertEquals(3L, tweetSummaryDto.getRetweetsCount());
		assertNotNull(tweetSummaryDto.getTopReply());
		assertEquals(replyId, tweetSummaryDto.getTopReply().getId());
	}

	@Test
	void testLombokGeneratedMethods() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 10L;
		Long repliesCount = 5L;
		Long retweetsCount = 3L;
		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());

		TweetSummaryDto tweetSummaryDto1 = TweetSummaryDto.builder()
			.tweetId(tweetId)
			.likesCount(likesCount)
			.repliesCount(repliesCount)
			.retweetsCount(retweetsCount)
			.topReply(reply)
			.build();

		TweetSummaryDto tweetSummaryDto2 = TweetSummaryDto.builder()
			.tweetId(tweetId)
			.likesCount(likesCount)
			.repliesCount(repliesCount)
			.retweetsCount(retweetsCount)
			.topReply(reply)
			.build();

		assertEquals(tweetSummaryDto1, tweetSummaryDto2);
		assertEquals(tweetSummaryDto1.hashCode(), tweetSummaryDto2.hashCode());
		assertNotNull(tweetSummaryDto1.toString());
		assertTrue(tweetSummaryDto1.toString().contains("TweetSummaryDto"));

		assertEquals(tweetId, tweetSummaryDto1.getTweetId());
		assertEquals(likesCount, tweetSummaryDto1.getLikesCount());
		assertEquals(repliesCount, tweetSummaryDto1.getRepliesCount());
		assertEquals(retweetsCount, tweetSummaryDto1.getRetweetsCount());
		assertEquals(reply, tweetSummaryDto1.getTopReply());
	}

	@Test
	void testBuilderPattern() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 100L;
		Long repliesCount = 50L;
		Long retweetsCount = 30L;
		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());

		TweetSummaryDto tweetSummaryDto = TweetSummaryDto.builder()
			.tweetId(tweetId)
			.likesCount(likesCount)
			.repliesCount(repliesCount)
			.retweetsCount(retweetsCount)
			.topReply(reply)
			.build();

		assertNotNull(tweetSummaryDto);
		assertEquals(tweetId, tweetSummaryDto.getTweetId());
		assertEquals(likesCount, tweetSummaryDto.getLikesCount());
		assertEquals(repliesCount, tweetSummaryDto.getRepliesCount());
		assertEquals(retweetsCount, tweetSummaryDto.getRetweetsCount());
		assertEquals(reply, tweetSummaryDto.getTopReply());
	}

	@Test
	void testChainAccessors() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 50L;
		Long repliesCount = 10L;
		Long retweetsCount = 5L;
		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());

		TweetSummaryDto tweetSummaryDto = new TweetSummaryDto()
			.setTweetId(tweetId)
			.setLikesCount(likesCount)
			.setRepliesCount(repliesCount)
			.setRetweetsCount(retweetsCount)
			.setTopReply(reply);

		assertNotNull(tweetSummaryDto);
		assertEquals(tweetId, tweetSummaryDto.getTweetId());
		assertEquals(likesCount, tweetSummaryDto.getLikesCount());
		assertEquals(repliesCount, tweetSummaryDto.getRepliesCount());
		assertEquals(retweetsCount, tweetSummaryDto.getRetweetsCount());
		assertEquals(reply, tweetSummaryDto.getTopReply());
	}

	@Test
	void testNullValues() {
		TweetSummaryDto tweetSummaryDto = new TweetSummaryDto();

		assertNull(tweetSummaryDto.getTweetId());
		assertNull(tweetSummaryDto.getLikesCount());
		assertNull(tweetSummaryDto.getRepliesCount());
		assertNull(tweetSummaryDto.getRetweetsCount());
		assertNull(tweetSummaryDto.getTopReply());
	}
}
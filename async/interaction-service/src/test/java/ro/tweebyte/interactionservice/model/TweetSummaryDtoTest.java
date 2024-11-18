package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TweetSummaryDtoTest {

	@Test
	void testAllArgsConstructor() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 100L;
		Long repliesCount = 50L;
		Long retweetsCount = 20L;
		ReplyDto topReply = new ReplyDto();

		TweetSummaryDto dto = new TweetSummaryDto(tweetId, likesCount, repliesCount, retweetsCount, topReply);

		assertEquals(tweetId, dto.getTweetId());
		assertEquals(likesCount, dto.getLikesCount());
		assertEquals(repliesCount, dto.getRepliesCount());
		assertEquals(retweetsCount, dto.getRetweetsCount());
		assertEquals(topReply, dto.getTopReply());
	}

	@Test
	void testNoArgsConstructorAndSetters() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 100L;
		Long repliesCount = 50L;
		Long retweetsCount = 20L;
		ReplyDto topReply = new ReplyDto();

		TweetSummaryDto dto = new TweetSummaryDto();
		dto.setTweetId(tweetId);
		dto.setLikesCount(likesCount);
		dto.setRepliesCount(repliesCount);
		dto.setRetweetsCount(retweetsCount);
		dto.setTopReply(topReply);

		assertEquals(tweetId, dto.getTweetId());
		assertEquals(likesCount, dto.getLikesCount());
		assertEquals(repliesCount, dto.getRepliesCount());
		assertEquals(retweetsCount, dto.getRetweetsCount());
		assertEquals(topReply, dto.getTopReply());
	}

	@Test
	void testBuilder() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 100L;
		Long repliesCount = 50L;
		Long retweetsCount = 20L;
		ReplyDto topReply = new ReplyDto();

		TweetSummaryDto dto = TweetSummaryDto.builder()
			.tweetId(tweetId)
			.likesCount(likesCount)
			.repliesCount(repliesCount)
			.retweetsCount(retweetsCount)
			.topReply(topReply)
			.build();

		assertEquals(tweetId, dto.getTweetId());
		assertEquals(likesCount, dto.getLikesCount());
		assertEquals(repliesCount, dto.getRepliesCount());
		assertEquals(retweetsCount, dto.getRetweetsCount());
		assertEquals(topReply, dto.getTopReply());
	}

	@Test
	void testChainAccessors() {
		UUID tweetId = UUID.randomUUID();
		Long likesCount = 100L;
		Long repliesCount = 50L;
		Long retweetsCount = 20L;
		ReplyDto topReply = new ReplyDto();

		TweetSummaryDto dto = new TweetSummaryDto()
			.setTweetId(tweetId)
			.setLikesCount(likesCount)
			.setRepliesCount(repliesCount)
			.setRetweetsCount(retweetsCount)
			.setTopReply(topReply);

		assertEquals(tweetId, dto.getTweetId());
		assertEquals(likesCount, dto.getLikesCount());
		assertEquals(repliesCount, dto.getRepliesCount());
		assertEquals(retweetsCount, dto.getRetweetsCount());
		assertEquals(topReply, dto.getTopReply());
	}
}

package ro.tweebyte.tweetservice.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockStreamingChatModelTest {

    // Fast config: ttftMean 1ms, itlMean 1ms. Real distribution samplers
    // may produce outliers but the 10-token count + concatenation guarantees are shape-invariant.
    private static final int TOKENS = 10;

    private MockStreamingChatModel newModel() {
        return new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, TOKENS);
    }

    @Test
    void streamEmitsConfiguredTokenCount() {
        List<ChatResponse> chunks = newModel()
                .stream(new Prompt(new UserMessage("hi")))
                .collectList()
                .block();
        assertNotNull(chunks);
        assertEquals(TOKENS, chunks.size(),
                "stream() must emit exactly tokensPerResponse ChatResponses");
    }

    @Test
    void streamChunksCarryIndexedTokens() {
        List<ChatResponse> chunks = newModel()
                .stream(new Prompt(new UserMessage("hi")))
                .collectList()
                .block();
        assertNotNull(chunks);
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i).getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.startsWith("token_" + i),
                    "chunk " + i + " should start with token_" + i + " but was " + text);
        }
    }

    @Test
    void callConcatenatesAllStreamChunks() {
        ChatResponse resp = newModel().call(new Prompt(new UserMessage("hi")));
        String text = resp.getResult().getOutput().getText();
        // Every token_N must be present exactly once in concatenation order.
        int idx = -1;
        for (int i = 0; i < TOKENS; i++) {
            int found = text.indexOf("token_" + i, idx + 1);
            assertTrue(found > idx,
                    "token_" + i + " missing or out of order in: " + text);
            idx = found;
        }
        assertEquals("stop",
                resp.getResult().getMetadata().getFinishReason());
    }

    @Test
    void accessorsReflectConstructorArgs() {
        MockStreamingChatModel m = newModel();
        assertEquals(1.0, m.getTtftMeanMs());
        assertEquals(0.2, m.getTtftLogSigma());
        assertEquals(1.0, m.getItlMeanMs());
        assertEquals(2.0, m.getItlGammaShape());
        assertEquals(0.0, m.getItlPBurst());
        assertEquals(TOKENS, m.getTokensPerResponse());
    }

    @Test
    void zeroInflatedConstructorPropagatesPBurst() {
        MockStreamingChatModel m = new MockStreamingChatModel(
                1.0, 0.2, 1.0, 2.0, 0.384, TOKENS);
        assertEquals(0.384, m.getItlPBurst(), 1e-9);
    }

    @Test
    void pBurstClampedAtConstructor() {
        MockStreamingChatModel high = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 1.5, TOKENS);
        assertEquals(1.0, high.getItlPBurst(), 1e-9);
        MockStreamingChatModel low = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, -0.4, TOKENS);
        assertEquals(0.0, low.getItlPBurst(), 1e-9);
    }

    @Test
    void pBurstNaNAtConstructorClampedToZero() {
        // clampPBurst NaN branch.
        MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, Double.NaN, TOKENS);
        assertEquals(0.0, m.getItlPBurst(), 1e-9);
    }

    @Test
    void streamWithSmallPBurstExercisesBothInnerBranches() {
        // pBurst > 0 but small enough that nextDouble() falls on either side often.
        // Running enough tokens makes the false inner-branch (no burst, sample gamma)
        // get hit deterministically across draws.
        MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 0.5, 200);
        List<ChatResponse> chunks = m.stream(new Prompt(new UserMessage("hi")))
                .collectList().block();
        assertNotNull(chunks);
        assertEquals(200, chunks.size());
    }
    @Test
    void streamWithPBurst1EmitsAllTokensWithoutGapDelay() {
        MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 1.0, TOKENS);
        List<ChatResponse> chunks = m.stream(new Prompt(new UserMessage("hi")))
                .collectList().block();
        assertNotNull(chunks);
        assertEquals(TOKENS, chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i).getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.startsWith("token_" + i));
        }
    }
}

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
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WebAppConfiguration
class ReplyControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private ReplyService replyService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID replyId = UUID.randomUUID();
    private final UUID tweetId = UUID.randomUUID();

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
    void createReply() throws Exception {
        ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(tweetId).setContent("Test content");
        ReplyDto expectedReply = new ReplyDto().setId(replyId).setContent(request.getContent());

        when(replyService.createReply(any(ReplyCreateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(expectedReply));

        MvcResult result = mockMvc.perform(post("/replies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(replyId.toString()))
            .andExpect(jsonPath("$.content").value("Test content"));

        verify(replyService).createReply(any(ReplyCreateRequest.class));
    }

    @Test
    void updateReply() throws Exception {
        ReplyUpdateRequest request = new ReplyUpdateRequest().setContent("Updated content");

        when(replyService.updateReply(any(ReplyUpdateRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/replies/{replyId}", replyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk());

        verify(replyService).updateReply(any(ReplyUpdateRequest.class));
    }

    @Test
    void deleteReply() throws Exception {
        when(replyService.deleteReply(userId, replyId)).thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(delete("/replies/{replyId}", replyId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk());

        verify(replyService).deleteReply(userId, replyId);
    }

    @Test
    void getAllRepliesForTweet() throws Exception {
        List<ReplyDto> replies = List.of(new ReplyDto().setId(replyId).setContent("Test reply"));
        when(replyService.getRepliesForTweet(tweetId))
            .thenReturn(CompletableFuture.completedFuture(replies));

        MvcResult result = mockMvc.perform(get("/replies/tweet/{tweetId}", tweetId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(replyId.toString()))
            .andExpect(jsonPath("$[0].content").value("Test reply"));

        verify(replyService).getRepliesForTweet(tweetId);
    }

    @Test
    void getReplyCountForTweet() throws Exception {
        when(replyService.getReplyCountForTweet(tweetId)).thenReturn(CompletableFuture.completedFuture(5L));

        MvcResult result = mockMvc.perform(get("/replies/tweet/{tweetId}/count", tweetId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(5));

        verify(replyService).getReplyCountForTweet(tweetId);
    }

    @Test
    void getTopReplyForTweet() throws Exception {
        ReplyDto topReply = new ReplyDto().setId(replyId).setContent("Top reply");
        when(replyService.getTopReplyForTweet(tweetId))
            .thenReturn(CompletableFuture.completedFuture(topReply));

        MvcResult result = mockMvc.perform(get("/replies/tweet/{tweetId}/top", tweetId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(replyId.toString()))
            .andExpect(jsonPath("$.content").value("Top reply"));

        verify(replyService).getTopReplyForTweet(tweetId);
    }
}

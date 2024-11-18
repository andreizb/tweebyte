package ro.tweebyte.tweetservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.tweebyte.tweetservice.controller.TweetController;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TweetController.class})
public class JwtRequestFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TweetService tweetService;

	@MockBean
	private NimbusJwtDecoder jwtDecoder;

	@MockBean
	private HashtagService hashtagService;

	@InjectMocks
	private JwtRequestFilter jwtRequestFilter;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		jwtRequestFilter = new JwtRequestFilter(jwtDecoder);
		mockMvc = MockMvcBuilders.standaloneSetup(new TweetController(tweetService, hashtagService))
				.addFilters(jwtRequestFilter)
				.build();
	}

	@Test
	public void testValidJwtToken() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		String userEmail = "test@example.com";
		String dummyJwt = "Bearer dummyJwtToken";

		var jwt = mock(Jwt.class);
		when(jwt.getClaimAsString("user_id")).thenReturn(userId.toString());
		when(jwt.getClaimAsString("email")).thenReturn(userEmail);
		when(jwtDecoder.decode("dummyJwtToken")).thenReturn(jwt);

		mockMvc.perform(get("/tweets/" + tweetId)
						.header("Authorization", dummyJwt)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication != null;
		assert authentication.isAuthenticated();
	}

	@Test
	public void testInvalidJwtToken() throws Exception {
		UUID tweetId = UUID.randomUUID();
		String invalidJwt = "Bearer invalidJwtToken";
		when(jwtDecoder.decode("invalidJwtToken")).thenThrow(new RuntimeException("Invalid token"));

		mockMvc.perform(get("/tweets/" + tweetId)
						.header("Authorization", invalidJwt)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testNoJwtToken() throws Exception {
		UUID tweetId = UUID.randomUUID();
		mockMvc.perform(get("/tweets/" + tweetId)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isBadRequest());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testEmptyAuthorizationHeader() throws Exception {
		UUID tweetId = UUID.randomUUID();
		mockMvc.perform(get("/tweets/" + tweetId)
						.header("Authorization", "")
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testNonBearerAuthorizationHeader() throws Exception {
		UUID tweetId = UUID.randomUUID();
		mockMvc.perform(get("/tweets/" + tweetId)
						.header("Authorization", "Basic someToken")
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}
}
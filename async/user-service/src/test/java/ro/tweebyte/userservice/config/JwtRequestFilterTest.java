package ro.tweebyte.userservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.tweebyte.userservice.controller.UserController;
import ro.tweebyte.userservice.service.UserService;
import ro.tweebyte.userservice.model.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, JwtRequestFilter.class})
public class JwtRequestFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private UserService userService;

	@MockBean
	private UserDetailsService userDetailsService;

	@MockBean
	private JwtDecoder jwtDecoder;

	@InjectMocks
	private JwtRequestFilter jwtRequestFilter;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
				.addFilters(jwtRequestFilter)
				.build();
	}

	@Test
	public void testValidJwtToken() throws Exception {
		UUID userId = UUID.randomUUID();
		String userEmail = "test@example.com";
		String dummyJwt = "Bearer dummyJwtToken";

		var jwt = mock(Jwt.class);
		when(jwt.getClaimAsString("user_id")).thenReturn(userId.toString());
		when(jwt.getClaimAsString("email")).thenReturn(userEmail);
		when(jwtDecoder.decode("dummyJwtToken")).thenReturn(jwt);

		var userDetails = new CustomUserDetails(userId, userEmail, "dummyPassword",
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
		when(userDetailsService.loadUserByUsername(userEmail)).thenReturn(userDetails);

		mockMvc.perform(get("/users/" + userId)
						.header("Authorization", dummyJwt)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication != null;
		assert authentication.isAuthenticated();
	}

	@Test
	public void testInvalidJwtToken() throws Exception {
		UUID userId = UUID.randomUUID();
		String invalidJwt = "Bearer invalidJwtToken";
		when(jwtDecoder.decode("invalidJwtToken")).thenThrow(new RuntimeException("Invalid token"));

		mockMvc.perform(get("/users/" + userId)
						.header("Authorization", invalidJwt)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testNoJwtToken() throws Exception {
		UUID userId = UUID.randomUUID();
		mockMvc.perform(get("/users/" + userId)
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isBadRequest());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testEmptyAuthorizationHeader() throws Exception {
		UUID userId = UUID.randomUUID();
		mockMvc.perform(get("/users/" + userId)
						.header("Authorization", "")
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}

	@Test
	public void testNonBearerAuthorizationHeader() throws Exception {
		UUID userId = UUID.randomUUID();
		mockMvc.perform(get("/users/" + userId)
						.header("Authorization", "Basic someToken")
						.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isOk());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assert authentication == null;
	}
}
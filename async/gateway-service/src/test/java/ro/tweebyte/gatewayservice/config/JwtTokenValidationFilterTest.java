package ro.tweebyte.gatewayservice.config;

import com.netflix.zuul.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class JwtTokenValidationFilterTest {

    @Autowired
    private JwtTokenValidationFilter jwtTokenValidationFilter;

    @MockBean
    private HttpServletRequest request;

    @MockBean
    private RequestContext requestContext;

    @BeforeEach
    public void setUp() {
        RequestContext.testSetCurrentContext(requestContext);
    }

    @Test
    void filterType() {
        assertEquals("pre", jwtTokenValidationFilter.filterType());
    }

    @Test
    void filterOrder() {
        assertEquals(1, jwtTokenValidationFilter.filterOrder());
    }

    @Test
    void shouldFilter() {
        when(request.getRequestURI()).thenReturn("/login");
        when(requestContext.getRequest()).thenReturn(request);
        assertFalse(jwtTokenValidationFilter.shouldFilter());

        when(request.getRequestURI()).thenReturn("/register");
        assertFalse(jwtTokenValidationFilter.shouldFilter());

        when(request.getRequestURI()).thenReturn("/other");
        assertTrue(jwtTokenValidationFilter.shouldFilter());
    }

    @Test
    void run() {
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(request1);
        when(request1.getHeader("Authorization")).thenReturn("Bearer asdfg");

        jwtTokenValidationFilter.run();

        Mockito.verify(requestContext, times(1)).setSendZuulResponse(anyBoolean());
    }

}
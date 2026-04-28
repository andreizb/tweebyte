package ro.tweebyte.gatewayservice.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.security.interfaces.RSAPublicKey;

@Component
public class JwtTokenValidationFilter extends ZuulFilter {

    @Autowired
    private RSAPublicKey publicKey;

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        String requestUri = request.getRequestURI();

        return !requestUri.endsWith("/login") && !requestUri.endsWith("/register");
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();

        String jwtToken = extractJwtFromRequest(request);
        try {
            if (jwtToken == null || !validateToken(jwtToken)) {
                ctx.setSendZuulResponse(false);
                ctx.setResponseStatusCode(401);
            }
        } catch (Exception ex) {
            ctx.setSendZuulResponse(false);
            ctx.setResponseStatusCode(401);
        }

        return null;
    }

    private boolean validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

}

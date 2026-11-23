package com.wokandroll.back.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <token>".
 *
 * <ul>
 *   <li>No Bearer header: nothing happens here. Public endpoints work, closed ones return 401.
 *   <li>A valid token: the user goes into the SecurityContext.
 *   <li>A bad token (signature, exp, iss, aud, algorithm): 401 UNAUTHORIZED at once, on every
 *       endpoint, also on public ones (api-contract.md section 4, #151). The frontend then deletes
 *       the token and the guest flow works again.
 *   <li>POST /auth/login and /auth/register skip this filter, so a stale token never blocks a new
 *       login.
 * </ul>
 *
 * <p>Not a @Component on purpose: it is created in SecurityConfig, so @WebMvcTest slices of other
 * packages do not pick it up.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AuthenticationEntryPoint badToken;

  public JwtAuthFilter(JwtService jwtService, AuthenticationEntryPoint badToken) {
    this.jwtService = jwtService;
    this.badToken = badToken;
  }

  /** These two never read the token, so an old token in the browser cannot block a new login. */
  private static final Set<String> NO_TOKEN_CHECK =
      Set.of("/api/v1/auth/login", "/api/v1/auth/register");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return "POST".equals(request.getMethod()) && NO_TOKEN_CHECK.contains(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      Optional<AuthUser> user = jwtService.parse(header.substring(7).trim());
      if (user.isEmpty()) {
        SecurityContextHolder.clearContext();
        badToken.commence(request, response, new BadCredentialsException("Bad token"));
        return;
      }
      AuthUser current = user.get();
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  current, null, List.of(new SimpleGrantedAuthority("ROLE_" + current.role()))));
    }
    chain.doFilter(request, response);
  }
}

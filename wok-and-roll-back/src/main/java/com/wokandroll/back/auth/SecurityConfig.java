package com.wokandroll.back.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who can open what (wok-and-roll-back/docs/auth.md section 3). The guest flow stays public. In a
 * {@code @WebMvcTest} add {@code @Import(SecurityConfig.class)}, otherwise Spring Boot uses its
 * default rules and every request returns 401.
 */
@Configuration
@SecurityScheme(
    name = SecurityConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class SecurityConfig {

  public static final String BEARER = "bearerAuth";

  /** Guest flow: menu, cart quote, checkout and booking work without a token. */
  static final String[] PUBLIC_GET = {
    "/api/health",
    "/api/v1/countries",
    "/api/v1/categories",
    "/api/v1/products",
    "/api/v1/products/*",
    "/api/v1/cafes",
    "/api/v1/delivery-zones",
    "/api/v1/time-slots",
    "/api/v1/assistant"
  };

  static final String[] PUBLIC_POST = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/cart/quote",
    "/api/v1/orders",
    "/api/v1/bookings",
    "/api/v1/assistant/chat"
  };

  static final String[] DOCS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

  @Bean
  public JwtService jwtService(
      @Value("${JWT_SECRET:}") String secret,
      @Value("${JWT_TTL_HOURS:24}") long ttlHours,
      Environment env) {
    return new JwtService(resolveSecret(secret, env), ttlHours);
  }

  /**
   * The development secret is public (it is in the repo), so anyone who knows it could sign an
   * ADMIN token. It is allowed only with the {@code dev} profile. Without that profile the app
   * refuses to start unless {@code JWT_SECRET} is set.
   */
  static String resolveSecret(String secret, Environment env) {
    boolean dev = env.acceptsProfiles(Profiles.of("dev"));
    boolean missing = secret == null || secret.isBlank();
    if (dev && missing) {
      return JwtService.DEV_SECRET;
    }
    if (missing || JwtService.DEV_SECRET.equals(secret)) {
      throw new IllegalStateException(
          "JWT_SECRET is not set. Set it (32+ characters), or run with SPRING_PROFILES_ACTIVE=dev"
              + " for local development only.");
    }
    return secret;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  /**
   * We log in with JWT only. This empty bean stops Spring Boot from creating its default user and
   * printing a generated password in the log.
   */
  @Bean
  public UserDetailsService noFormLoginUsers() {
    return username -> {
      throw new UsernameNotFoundException("Login goes through /api/v1/auth/login");
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
    SecurityErrorHandlers errors = new SecurityErrorHandlers(objectMapper);
    http.csrf(AbstractHttpConfigurer::disable) // no cookies, the token is sent in a header
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, PUBLIC_GET)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST)
                    .permitAll()
                    .requestMatchers(DOCS)
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/staff/**")
                    .hasAnyRole("STAFF", "ADMIN")
                    .requestMatchers("/api/v1/reports/**")
                    .hasAnyRole("ADMIN", "VIEWER")
                    .requestMatchers("/api/v1/me", "/api/v1/me/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(errors.unauthorized())
                    .accessDeniedHandler(errors.forbidden()))
        .addFilterBefore(
            new JwtAuthFilter(jwtService, errors.badToken()),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}

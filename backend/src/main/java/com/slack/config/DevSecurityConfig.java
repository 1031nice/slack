package com.slack.config;

import com.slack.domain.user.User;
import com.slack.service.UserService;
import com.slack.util.DevJwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 개발용 Security 설정
 * dev 프로파일에서만 활성화되며, 간단한 JWT 검증을 사용합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
@RequiredArgsConstructor
public class DevSecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final DevJwtUtil devJwtUtil;

    @Bean
    public DevJwtAuthenticationFilter devJwtAuthenticationFilter(DevJwtUtil devJwtUtil, UserService userService) {
        return new DevJwtAuthenticationFilter(devJwtUtil, userService);
    }

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http, DevJwtAuthenticationFilter devJwtAuthenticationFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                 .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(devJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * 개발용 JWT 인증 필터
     */
    @RequiredArgsConstructor
    public static class DevJwtAuthenticationFilter extends OncePerRequestFilter {

        private final DevJwtUtil devJwtUtil;
        private final UserService userService;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);

                if (devJwtUtil.validateToken(token)) {
                    String authUserId = devJwtUtil.extractUsername(token);

                    // Find or create user
                    User user = userService.findByAuthUserIdOptional(authUserId)
                            .orElseGet(() -> {
                                // Auto-create user for dev mode
                                String email = authUserId + "@dev.local";
                                return userService.createUser(authUserId, email, authUserId);
                            });

                    // Create authentication with User object as principal
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);
        }
    }
}

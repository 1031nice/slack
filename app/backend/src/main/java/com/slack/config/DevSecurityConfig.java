package com.slack.config;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
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
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Security configuration for dev mode only
 * Uses simple JWT validation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
@RequiredArgsConstructor
public class DevSecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

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
                    User user = userService.findOrCreateDevUser(authUserId);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);
        }
    }
}

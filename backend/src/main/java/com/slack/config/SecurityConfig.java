package com.slack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtToUserAuthenticationConverter jwtToUserAuthenticationConverter;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                         JwtToUserAuthenticationConverter jwtToUserAuthenticationConverter) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtToUserAuthenticationConverter = jwtToUserAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                 .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwkSetUri("http://localhost:8081/oauth2/jwks")
                    .jwtAuthenticationConverter(jwtToUserAuthenticationConverter)
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll() // Actuator 엔드포인트는 인증 없이 접근 (메트릭 수집용)
                .requestMatchers("/ws/**").permitAll() // WebSocket 엔드포인트는 인증 없이 접근 (연결 시 JWT 검증)
                .requestMatchers("/api/auth/**").permitAll() // 인증 엔드포인트는 인증 없이 접근
                .anyRequest().authenticated()
            );

        return http.build();
    }
}


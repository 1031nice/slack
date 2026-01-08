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
@org.springframework.context.annotation.Profile("!dev")
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
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}


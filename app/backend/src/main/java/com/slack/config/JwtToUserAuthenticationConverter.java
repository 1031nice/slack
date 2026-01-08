package com.slack.config;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Converts JWT to Authentication with authUserId (String) as principal.
 * This allows controllers to use @AuthenticationPrincipal String authUserId directly.
 */
@Component
@RequiredArgsConstructor
public class JwtToUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String authUserId = jwt.getSubject();
        // Ensure user exists in database
        userService.findByAuthUserId(authUserId);

        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        // Store authUserId (String) as Principal to avoid JPA session issues
        return new UsernamePasswordAuthenticationToken(authUserId, jwt, authorities);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<String> authorityClaims = jwt.getClaimAsStringList("authorities");
        if (authorityClaims != null) {
            return authorityClaims.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

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
 * Converts JWT to Authentication with User entity as principal.
 * This allows controllers to use @AuthenticationPrincipal User directly.
 */
@Component
@RequiredArgsConstructor
public class JwtToUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String authUserId = jwt.getSubject();
        User user = userService.findByAuthUserId(authUserId);

        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        return new UsernamePasswordAuthenticationToken(user, jwt, authorities);
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

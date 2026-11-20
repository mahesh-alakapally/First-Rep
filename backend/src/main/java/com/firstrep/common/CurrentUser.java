package com.firstrep.common;

import com.firstrep.config.SecurityConfig.AuthPrincipal;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserAccountRepository users;

    public CurrentUser(UserAccountRepository users) {
        this.users = users;
    }

    public AuthPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw AppException.unauthorized();
        }
        return principal;
    }

    public UserAccount require() {
        return users.findById(principal().id()).orElseThrow(AppException::unauthorized);
    }

    public boolean hasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
}

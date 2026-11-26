package ru.moscow.hotspot_tracker.main.web;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService;

/** Мост между формой входа Spring Security и учётными записями из порта UserAccountService. */
@Service
class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountService accounts;

    AccountUserDetailsService(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return accounts.findCredentials(email)
                .map(c -> User.withUsername(c.email())
                        .password(c.passwordHash())
                        .authorities("ROLE_" + c.role().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }
}

package ru.mirea.medicinedelivery.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.mirea.medicinedelivery.domain.Role;
import ru.mirea.medicinedelivery.domain.UserAccount;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Security-представление авторизованного пользователя. */
public record UserPrincipal(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        boolean enabled,
        boolean passwordChangeRequired,
        Set<Role> roles) implements UserDetails {

    /** Создаёт principal из доменной сущности. */
    public static UserPrincipal from(UserAccount user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getPasswordHash(), user.isEnabled(), user.isPasswordChangeRequired(), Set.copyOf(user.getRoles()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}

package com.orbitworkbench.identity.application;

import com.orbitworkbench.identity.domain.AppUserRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class OrbitUserDetails implements UserDetails, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final AppUserRecord user;

    public OrbitUserDetails(AppUserRecord user) {
        this.user = user;
    }

    public Long userId() { return user.getId(); }
    public String username() { return user.getUsername(); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return user.isEnabled(); }
}

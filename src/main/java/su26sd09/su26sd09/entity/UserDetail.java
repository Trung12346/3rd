package su26sd09.su26sd09.entity;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UserDetail implements org.springframework.security.core.userdetails.UserDetails {

    private Integer id;
    private String email;
    private String passwordhash;
    private VaiTro role;

    public UserDetail(Integer id,String email,String passwordhash,VaiTro role ) {
        this.id = id;
        this.email = email;
        this.passwordhash = passwordhash;
        this.role = role;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role.getTenVaiTro()));
    }

    @Override
    public @Nullable String getPassword() {
        return passwordhash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

package su26sd09.su26sd09.entity;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public class UserDetail implements org.springframework.security.core.userdetails.UserDetails {

    private final NguoiDung nguoiDung;

    public UserDetail(NguoiDung nguoiDung) {
        this.nguoiDung = nguoiDung;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(nguoiDung.getVaiTro().getTenVaiTro()));
    }

    @Override
    public @Nullable String getPassword() {
        return nguoiDung.getMatKhau_hash();
    }

    @Override
    public String getUsername() {
        return nguoiDung.getEmail();
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

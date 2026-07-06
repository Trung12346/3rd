package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.entity.UserDetail;
import su26sd09.su26sd09.repository.KhachHangRepository;
import su26sd09.su26sd09.repository.NhanVienRepo;

import java.util.Optional;
@Service
public class EmployeeUserDetailsService implements UserDetailsService {
    @Autowired
    NhanVienRepo nhanVienRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<NhanSu> nvCheck = nhanVienRepo.findByEmail(email);
        System.out.println("tim email " + email);
        System.out.println("ket qua" + nvCheck);
        if (nvCheck.isPresent()) {
            NhanSu nv = nvCheck.get();
            System.out.println("nhan su: "+nhanVienRepo.findAll().stream().filter(ns -> ns.getEmail().equalsIgnoreCase(email)).toList().size());

            return  new UserDetail(nv.getId(),nv.getEmail(),nv.getMat_khau_hash(),nv.getVaitro());
        }
        throw new UsernameNotFoundException("Không tìm thấy user với username: " + email);
    }
}

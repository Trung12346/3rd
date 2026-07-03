package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.Admin;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.Nhanvien;
import su26sd09.su26sd09.entity.UserDetail;
import su26sd09.su26sd09.repository.AdminRepo;
import su26sd09.su26sd09.repository.KhachHangRepository;
import su26sd09.su26sd09.repository.NhanVienRepo;

import java.util.Optional;

@Service

public class CustomerUserDetailsService  implements UserDetailsService {

    @Autowired
    KhachHangRepository nguoiDungRepository;

    @Autowired
    NhanVienRepo nhanVienRepo;

    @Autowired
    AdminRepo adminRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<KhachHang> nguoiDung = nguoiDungRepository.findByEmail(email);
        Optional<Nhanvien> nvCheck = nhanVienRepo.findByEmail(email);
        Optional<Admin> adminCheck = adminRepo.findByEmail(email);
        System.out.println("tim email " + email);
        System.out.println("ket qua" + nguoiDung);
        if(adminCheck.isPresent()){
            Admin admin = adminCheck.get();
            return new UserDetail(admin.getMa_admin(),admin.getEmail(),admin.getMat_khau_hash(),admin.getVaiTro());
        }
        if(nguoiDung.isPresent()){
            KhachHang kh = nguoiDung.get();
            return new UserDetail(kh.getMa_khach_hang(),kh.getEmail(),kh.getMatKhau_hash(),kh.getVaiTro());

        }
        if (nvCheck.isPresent()) {
            Nhanvien nv = nvCheck.get();
            return  new UserDetail(nv.getId(),nv.getEmail(),nv.getMat_khau_hash(),nv.getVaitro());
        }
        throw new UsernameNotFoundException("Không tìm thấy user với username: " + email);
    }
}

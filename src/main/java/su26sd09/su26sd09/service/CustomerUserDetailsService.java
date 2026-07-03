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

public class CustomerUserDetailsService  implements UserDetailsService {

    @Autowired
    KhachHangRepository nguoiDungRepository;

    @Autowired
    NhanVienRepo nhanVienRepo;



    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<KhachHang> nguoiDung = nguoiDungRepository.findByEmail(email);
        Optional<NhanSu> nvCheck = nhanVienRepo.findByEmail(email);
        System.out.println("tim email " + email);
        System.out.println("ket qua" + nguoiDung);
        if(nguoiDung.isPresent()){
            KhachHang kh = nguoiDung.get();
            System.out.println(nhanVienRepo.findAll().stream().filter(nv -> nv.getEmail().equalsIgnoreCase(email)));
            return new UserDetail(kh.getMa_khach_hang(),kh.getEmail(),kh.getMatKhau_hash(),kh.getVaiTro());

        }
        if (nvCheck.isPresent()) {
            NhanSu nv = nvCheck.get();
            System.out.println("nhan su: "+nhanVienRepo.findAll().stream().filter(ns -> ns.getEmail().equalsIgnoreCase(email)).toList().size());

            return  new UserDetail(nv.getId(),nv.getEmail(),nv.getMat_khau_hash(),nv.getVaitro());
        }
        throw new UsernameNotFoundException("Không tìm thấy user với username: " + email);
    }
}

package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.VaiTro;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.repository.VaiTroRepo;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    @Autowired
    NguoiDungRepository repo;

    @Autowired
    VaiTroRepo vaiTroRepo;

    @Autowired
    PasswordEncoder passwordEncoder;

    public List<NguoiDung> getAll(){
        return repo.findAll();
    }

    public NguoiDung Getbyid(int id){
        return repo.findById(id).orElse(null);
    }

    public void remove(NguoiDung nguoiDung){
        repo.delete(nguoiDung);
    }

    public void save(NguoiDung nguoiDung){
        repo.save(nguoiDung);
    }

    public List<NguoiDung> search(String keyword) {
        List<NguoiDung> nguoiDungs = repo.findAll();
        if (keyword == null || keyword.isBlank()) {
            return nguoiDungs;
        }

        String q = keyword.toLowerCase(Locale.ROOT);
        return nguoiDungs.stream()
                .filter(nd -> contains(nd.getHoTen(), q)
                        || contains(nd.getEmail(), q)
                        || contains(nd.getSoDienThoai(), q)
                        || (nd.getVaiTro() != null && contains(nd.getVaiTro().getTenVaiTro(), q))
                        || String.valueOf(nd.getMaNguoiDung()).contains(q))
                .toList();
    }

    public List<VaiTro> findAllVaiTro() {
        return vaiTroRepo.findAll();
    }

    public void saveAdmin(NguoiDung formNguoiDung, Integer vaiTroId, String matKhauMoi) {
        VaiTro vaiTro = vaiTroRepo.findById(vaiTroId)
                .orElseThrow(() -> new RuntimeException("Vai tro khong ton tai"));

        if (formNguoiDung.getMaNguoiDung() == null) {
            if (repo.existsByEmail(formNguoiDung.getEmail())) {
                throw new RuntimeException("Email da ton tai");
            }
            if (formNguoiDung.getSoDienThoai() != null
                    && !formNguoiDung.getSoDienThoai().isBlank()
                    && repo.existsBySoDienThoai(formNguoiDung.getSoDienThoai())) {
                throw new RuntimeException("So dien thoai da ton tai");
            }
            if (matKhauMoi == null || matKhauMoi.isBlank()) {
                throw new RuntimeException("Mat khau khong duoc de trong khi them nguoi dung");
            }

            formNguoiDung.setMatKhau_hash(passwordEncoder.encode(matKhauMoi));
            formNguoiDung.setVaiTro(vaiTro);
            repo.save(formNguoiDung);
            return;
        }

        NguoiDung oldNguoiDung = Getbyid(formNguoiDung.getMaNguoiDung());
        if (oldNguoiDung == null) {
            throw new RuntimeException("Khong tim thay nguoi dung");
        }

        NguoiDung sameEmail = repo.findByEmail(formNguoiDung.getEmail());
        if (sameEmail != null && !sameEmail.getMaNguoiDung().equals(oldNguoiDung.getMaNguoiDung())) {
            throw new RuntimeException("Email da ton tai");
        }

        NguoiDung samePhone = null;
        if (formNguoiDung.getSoDienThoai() != null && !formNguoiDung.getSoDienThoai().isBlank()) {
            samePhone = repo.findBySoDienThoai(formNguoiDung.getSoDienThoai());
        }
        if (samePhone != null && !samePhone.getMaNguoiDung().equals(oldNguoiDung.getMaNguoiDung())) {
            throw new RuntimeException("So dien thoai da ton tai");
        }

        oldNguoiDung.setHoTen(formNguoiDung.getHoTen());
        oldNguoiDung.setEmail(formNguoiDung.getEmail());
        oldNguoiDung.setSoDienThoai(formNguoiDung.getSoDienThoai());
        oldNguoiDung.setDiaChi(formNguoiDung.getDiaChi());
        oldNguoiDung.setTrangThai(formNguoiDung.isTrangThai());
        oldNguoiDung.setVaiTro(vaiTro);
        if (matKhauMoi != null && !matKhauMoi.isBlank()) {
            oldNguoiDung.setMatKhau_hash(passwordEncoder.encode(matKhauMoi));
        }

        repo.save(oldNguoiDung);
    }

    public void setTrangThai(int id, boolean trangThai) {
        NguoiDung nguoiDung = Getbyid(id);
        if (nguoiDung != null) {
            nguoiDung.setTrangThai(trangThai);
            repo.save(nguoiDung);
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }


    public boolean checkSoDienThoai(String sodienthoai, Integer id){
        id = id == null ? 0 : id;
           return repo.findOthers(id).stream().anyMatch(x -> x.getSoDienThoai().equals(sodienthoai));
    }

    public boolean checkEmail(String email, Integer id){
        id = id == null ? 0 : id;
           return repo.findOthers(id).stream().anyMatch(x -> x.getEmail().equals(email));
    }

      public List<NguoiDung> TimKiemTheoTen(String name){
        return repo.search(name);
      }

}

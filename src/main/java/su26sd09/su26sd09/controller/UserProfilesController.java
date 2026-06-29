package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.DanhGiaService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/profiles")
public class    UserProfilesController {

    @Autowired
    UserService repo;
    @Autowired
    DatPhongService datPhongRepo;
    @Autowired
    DanhGiaService danhGiaRepo;
    @Autowired
    ChiTietDatPhongService chitietPhongrepo;
    @Autowired
    PasswordEncoder passwordEncoder;

    private NguoiDung getNguoiDungByPrincipal(Principal p) {
        return repo.getAll().stream()
                .filter(n -> n.getEmail().equals(p.getName()))
                .findFirst()
                .orElse(new NguoiDung());
    }

    @GetMapping("")
    public String home(Model model, Principal p,
                       @RequestParam(value = "tab", defaultValue = "overview") String tab) {

        NguoiDung nguoidung = getNguoiDungByPrincipal(p);

        List<DatPhong> allDatPhong = datPhongRepo.FindbyNguoiDung(nguoidung.getMaNguoiDung());

        Map<Integer, String> phongTheoDon = new HashMap<>();
        for (DatPhong datPhong : allDatPhong) {
            String tenPhong = chitietPhongrepo.findByDatPhongId(datPhong.getId()).stream()
                    .filter(ct -> ct.getP() != null)
                    .map(ct -> ct.getP().getSoPhong())
                    .findFirst()
                    .orElse("");
            phongTheoDon.put(datPhong.getId(), tenPhong);
        }

        List<DanhGia> listDanhGia = danhGiaRepo.findByNguoiDung(nguoidung.getMaNguoiDung());

        model.addAttribute("listDatPhong", allDatPhong);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("nguoiDung", nguoidung);
        model.addAttribute("tongPhong", allDatPhong.size());
        model.addAttribute("tongsodanhgia", listDanhGia.size());
        model.addAttribute("listDanhGia", listDanhGia);
        model.addAttribute("activeTab", tab);
        return "customer-setting";
    }

    @PostMapping("/update")
    public String updateProfile(@RequestParam("hoTen") String hoTen,
                                @RequestParam("soDienThoai") String soDienThoai,
                                @RequestParam("diaChi") String diaChi,
                                Principal p,
                                RedirectAttributes redirectAttributes) {
        NguoiDung nguoidung = getNguoiDungByPrincipal(p);
        nguoidung.setHoTen(hoTen);
        nguoidung.setSoDienThoai(soDienThoai);
        nguoidung.setDiaChi(diaChi);
        repo.save(nguoidung);
        redirectAttributes.addFlashAttribute("successMsg", "Cập nhật thông tin thành công!");
        return "redirect:/profiles?tab=profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("matKhauHienTai") String matKhauHienTai,
                                 @RequestParam("matKhauMoi") String matKhauMoi,
                                 @RequestParam("xacNhanMatKhau") String xacNhanMatKhau,
                                 Principal p,
                                 RedirectAttributes redirectAttributes) {
        NguoiDung nguoidung = getNguoiDungByPrincipal(p);

        if (!passwordEncoder.matches(matKhauHienTai, nguoidung.getMatKhau_hash())) {
            redirectAttributes.addFlashAttribute("errorMsg", "Mật khẩu hiện tại không đúng!");
            return "redirect:/profiles?tab=password";
        }
        if (!matKhauMoi.equals(xacNhanMatKhau)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Mật khẩu mới không khớp!");
            return "redirect:/profiles?tab=password";
        }
        if (matKhauMoi.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMsg", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return "redirect:/profiles?tab=password";
        }
        nguoidung.setMatKhau_hash(passwordEncoder.encode(matKhauMoi));
        repo.save(nguoidung);
        redirectAttributes.addFlashAttribute("successMsg", "Đổi mật khẩu thành công!");
        return "redirect:/profiles?tab=password";
    }
}

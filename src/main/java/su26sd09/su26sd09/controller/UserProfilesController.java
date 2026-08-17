package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.ChiTietDichVuService;
import su26sd09.su26sd09.service.DanhGiaService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    ChiTietDichVuService chiTietDichVuService;
    @Autowired
    PasswordEncoder passwordEncoder;

    private KhachHang getNguoiDungByPrincipal(Principal p) {
        return repo.getAll().stream()
                .filter(n -> n.getEmail().equals(p.getName()))
                .findFirst()
                .orElse(new KhachHang());
    }

    @GetMapping("")
    public String home(Model model, Principal p,
                       @RequestParam(value = "tab", defaultValue = "overview") String tab,
                       @RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "keyword", required = false) String keyword) {

        KhachHang nguoidung = getNguoiDungByPrincipal(p);

        List<DatPhong> allDatPhong = datPhongRepo.FindbyNguoiDung(nguoidung.getMa_khach_hang()).stream().filter(x -> !x.getTrangThai().equalsIgnoreCase("chua thanh toan")).collect(Collectors.toList());

        // Sắp xếp giảm dần theo mã đơn (đơn mới nhất lên đầu)
        allDatPhong.sort((a, b) -> b.getId().compareTo(a.getId()));

        Map<Integer, String> phongTheoDon = new HashMap<>();
        for (DatPhong datPhong : allDatPhong) {
            String tenPhong = chitietPhongrepo.findByDatPhongId(datPhong.getId()).stream()
                    .filter(ct -> ct.getP() != null )
                    .map(ct -> ct.getP().getSoPhong())
                    .findFirst()
                    .orElse("");
            phongTheoDon.put(datPhong.getId(), tenPhong);
        }

        // ==== Tìm kiếm cho tab "Đơn đặt phòng" ====
        String kw = keyword != null ? keyword.trim().toLowerCase() : "";
        List<DatPhong> filteredDatPhong = allDatPhong;
        if (!kw.isEmpty()) {
            filteredDatPhong = allDatPhong.stream()
                    .filter(dp -> {
                        String maDon = String.valueOf(dp.getId());
                        String tenPhong = phongTheoDon.getOrDefault(dp.getId(), "");
                        String trangThai = dp.getTrangThai() != null ? dp.getTrangThai() : "";
                        return maDon.contains(kw)
                                || tenPhong.toLowerCase().contains(kw)
                                || trangThai.toLowerCase().contains(kw);
                    })
                    .toList();
        }

        // ==== Phân trang cho tab "Đơn đặt phòng" ====
        int pageSize = 9;
        int totalItems = filteredDatPhong.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<DatPhong> listDatPhongPhanTrang = fromIndex < toIndex
                ? filteredDatPhong.subList(fromIndex, toIndex)
                : List.of();

        List<DanhGia> listDanhGia = danhGiaRepo.findByNguoiDung(nguoidung.getMa_khach_hang());

        model.addAttribute("listDatPhong", allDatPhong);                     // dùng cho tab Tổng quan
        model.addAttribute("listDatPhongPhanTrang", listDatPhongPhanTrang);  // dùng cho tab Đơn đặt phòng
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("tongKetQua", totalItems);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("nguoiDung", nguoidung);
        model.addAttribute("tongPhong", allDatPhong.size());
        model.addAttribute("tongsodanhgia", listDanhGia.size());
        model.addAttribute("listDanhGia", listDanhGia);
        model.addAttribute("activeTab", tab);
        return "customer-setting";
    }


    @GetMapping("/dat-phong/{id}")
    public String chiTietDatPhong(@PathVariable Integer id, Principal p,
                                  RedirectAttributes redirectAttributes) {
        KhachHang nguoidung = getNguoiDungByPrincipal(p);
        DatPhong dp = datPhongRepo.findById(id);

        boolean hopLe = dp != null
                && dp.getN() != null
                && nguoidung.getMa_khach_hang() != null
                && nguoidung.getMa_khach_hang().equals(dp.getN().getMa_khach_hang());

        if (!hopLe) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "Không tìm thấy đơn đặt phòng hoặc bạn không có quyền xem đơn này!");
            return "redirect:/profiles?tab=bookings";
        }

        return "redirect:/thanh-toan/thanh-cong/" + dp.getId();
    }

    /**
     * Phục hồi luồng đặt phòng khi khách (đã đăng nhập) đang thanh toán mà bị mất mạng / rớt phiên.
     *
     * Quy tắc chuyển trang:
     *  - Đơn ở trạng thái "Chua thanh toan" + đã gắn với user hiện tại.
     *  - Nếu đơn chưa có ChiTietDichVu nào (chưa qua bước chọn dịch vụ)  -> quay lại trang chọn dịch vụ bổ sung.
     *  - Nếu đã có dịch vụ nhưng chưa thanh toán                          -> quay lại trang thanh toán.
     *  - Nếu không thuộc 2 trường hợp trên (đã thanh toán / bị hủy ...)  -> đưa về trang chi tiết hóa đơn.
     */
    @GetMapping("/dat-phong/{id}/tiep-tuc-dat")
    public String tiepTucDatPhong(@PathVariable Integer id, Principal p,
                                  RedirectAttributes redirectAttributes) {
        KhachHang nguoidung = getNguoiDungByPrincipal(p);
        DatPhong dp = datPhongRepo.findById(id);

        boolean thuocVeToi = dp != null
                && dp.getN() != null
                && nguoidung.getMa_khach_hang() != null
                && nguoidung.getMa_khach_hang().equals(dp.getN().getMa_khach_hang());

        if (!thuocVeToi) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "Không tìm thấy đơn đặt phòng hoặc bạn không có quyền truy cập.");
            return "redirect:/profiles?tab=bookings";
        }

        String trangThai = dp.getTrangThai() != null ? dp.getTrangThai() : "";

        // Chỉ phục hồi khi đơn đang dở ở khâu thanh toán.
        // Chỉ chấp nhận trạng thái "Chua thanh toan" — các trạng thái khác
        // (Yeu cau dat phong / Da xac nhan / Da huy / Da tra phong...) đều đã qua bước
        // thanh toán nên không thuộc luồng phục hồi này.
        if (!"Chua thanh toan".equalsIgnoreCase(trangThai)) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "Đơn đặt phòng này không còn ở trạng thái chờ thanh toán nên không thể tiếp tục.");
            return "redirect:/profiles?tab=bookings";
        }

        int soDichVu = chiTietDichVuService.findByDatPhongId(id).size();

        if (soDichVu == 0) {
            // Khách dở ở bước chọn dịch vụ bổ sung -> quay lại đúng trang đó
            redirectAttributes.addFlashAttribute("thongBao",
                    "Tiếp tục đặt phòng: bạn có thể chọn dịch vụ bổ sung hoặc bỏ qua và đi tới bước thanh toán.");
            return "redirect:/phong/dat-phong/xac-nhan/" + id;
        }

        // Đã chọn dịch vụ -> đưa thẳng về trang thanh toán
        redirectAttributes.addFlashAttribute("thongBao",
                "Tiếp tục đặt phòng: đơn của bạn đã được khôi phục, vui lòng hoàn tất thanh toán.");
        return "redirect:/thanh-toan/dat-phong/" + id;
    }

    @PostMapping("/update")
    public String updateProfile(@RequestParam("hoTen") String hoTen,
                                @RequestParam("soDienThoai") String soDienThoai,
                                @RequestParam("diaChi") String diaChi,
                                Principal p,
                                RedirectAttributes redirectAttributes) {
        KhachHang nguoidung = getNguoiDungByPrincipal(p);
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
        KhachHang nguoidung = getNguoiDungByPrincipal(p);

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
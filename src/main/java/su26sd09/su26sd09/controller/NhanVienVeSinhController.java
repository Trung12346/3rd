package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import su26sd09.su26sd09.dto.PhongVeSinhAssignment;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.repository.PhongRepository;
import su26sd09.su26sd09.service.JanitorCacheService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trang dành cho nhân viên vệ sinh (tài khoản STAFF, bộ phận "Vệ Sinh"):
 * xem phòng vừa được janitor assignment engine gán, upload ảnh phòng đã dọn.
 * Đồng thời cung cấp endpoint để lễ tân/nhân viên xác nhận ảnh và trả phòng
 * về trạng thái "Trong" từ trang Sơ Đồ Phòng.
 */
@Controller
@RequestMapping("/nhan-su/ve-sinh")
public class NhanVienVeSinhController {

    private static final Path ANH_DIR = Paths.get("media", "ve-sinh");

    @Autowired
    private NhanVienRepo nhanVienRepo;

    @Autowired
    private PhongRepository phongRepository;

    @Autowired
    private JanitorCacheService cacheService;

    @GetMapping
    public String trangVeSinh(Model model, Authentication authentication, HttpServletRequest request) {
        NhanSu nv = nhanSuHienTai(authentication);
        model.addAttribute("nhanVien", nv);

        Optional<PhongVeSinhAssignment> assignment = nv == null
                ? Optional.empty()
                : cacheService.findByNhanVien(nv.getId());

        model.addAttribute("assignment", assignment.orElse(null));
        return "nhan-vien/ve-sinh";
    }

    @PostMapping("/upload")
    public String uploadAnh(@RequestParam("file") MultipartFile file,
                             Authentication authentication,
                             HttpServletRequest request) throws IOException {
        NhanSu nv = nhanSuHienTai(authentication);
        if (nv == null) {
            return "redirect:/nhan-su/ve-sinh";
        }

        Optional<PhongVeSinhAssignment> maybeAssignment = cacheService.findByNhanVien(nv.getId());
        if (maybeAssignment.isEmpty()) {
            request.getSession().setAttribute("toastError", "Bạn hiện chưa được phân công phòng nào để dọn.");
            return "redirect:/nhan-su/ve-sinh";
        }
        if (file == null || file.isEmpty()) {
            request.getSession().setAttribute("toastError", "Vui lòng chọn một ảnh để upload.");
            return "redirect:/nhan-su/ve-sinh";
        }

        PhongVeSinhAssignment assignment = maybeAssignment.get();

        Files.createDirectories(ANH_DIR);

        String extension = ".jpg";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf('.'));
        }

        // Ten file co dinh theo ma phong => lan upload lai se ghi de len anh cu,
        // dung yeu cau "reupload thi thay the anh cu".
        xoaAnhCu(assignment);
        String tenFile = "phong-" + assignment.getMaPhong() + "-" + UUID.randomUUID() + extension;
        Files.write(ANH_DIR.resolve(tenFile), file.getBytes());

        assignment.setDuongDanAnh(tenFile);
        assignment.setTrangThai(PhongVeSinhAssignment.DA_UPLOAD);
        assignment.setThoiGianUpload(LocalDateTime.now().toString());
        cacheService.upsert(assignment);

        request.getSession().setAttribute("toastSuccess", "Đã upload ảnh phòng P" + assignment.getSoPhong() + ", chờ xác nhận.");
        return "redirect:/nhan-su/ve-sinh";
    }

    private void xoaAnhCu(PhongVeSinhAssignment assignment) {
        if (assignment.getDuongDanAnh() == null) return;
        try {
            Files.deleteIfExists(ANH_DIR.resolve(assignment.getDuongDanAnh()));
        } catch (IOException ignored) {
        }
    }

    @GetMapping("/anh/{maPhong}")
    @ResponseBody
    public ResponseEntity<byte[]> xemAnh(@PathVariable int maPhong) throws IOException {
        Optional<PhongVeSinhAssignment> assignment = cacheService.get(maPhong);
        if (assignment.isEmpty() || assignment.get().getDuongDanAnh() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = ANH_DIR.resolve(assignment.get().getDuongDanAnh());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String lower = path.toString().toLowerCase();
        String mime = lower.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE
                : lower.endsWith(".gif") ? MediaType.IMAGE_GIF_VALUE
                : lower.endsWith(".webp") ? "image/webp"
                : MediaType.IMAGE_JPEG_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(mime))
                .body(Files.readAllBytes(path));
    }

    /**
     * Lễ tân xác nhận phòng đã sạch từ Sơ Đồ Phòng: cập nhật phòng về
     * "Trong" và giải phóng nhân viên vệ sinh (xoá khỏi cache) để engine có
     * thể gán họ cho phòng khác.
     */
    @PostMapping("/xac-nhan/{maPhong}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> xacNhanPhongSach(@PathVariable int maPhong) {
        Map<String, Object> result = new HashMap<>();

        Optional<PhongVeSinhAssignment> maybeAssignment = cacheService.get(maPhong);
        if (maybeAssignment.isEmpty() || maybeAssignment.get().getDuongDanAnh() == null) {
            result.put("success", false);
            result.put("message", "Phòng này chưa có ảnh xác nhận vệ sinh.");
            return ResponseEntity.badRequest().body(result);
        }

        Optional<Phong> maybePhong = phongRepository.findById(maPhong);
        if (maybePhong.isEmpty()) {
            result.put("success", false);
            result.put("message", "Không tìm thấy phòng.");
            return ResponseEntity.badRequest().body(result);
        }

        Phong phong = maybePhong.get();
        phong.setTrangThai("Trong");
        phong.setNgayCapNhat(LocalDateTime.now());
        phongRepository.save(phong);

        xoaAnhCu(maybeAssignment.get());
        cacheService.remove(maPhong);

        result.put("success", true);
        result.put("trangThaiMoi", "Trống");
        return ResponseEntity.ok(result);
    }

    private NhanSu nhanSuHienTai(Authentication authentication) {
        if (authentication == null) return null;
        return nhanVienRepo.findByEmail(authentication.getName()).orElse(null);
    }
}

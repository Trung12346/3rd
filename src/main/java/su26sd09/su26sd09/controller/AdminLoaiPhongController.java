package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.service.LoaiPhongService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/nhan-su/admin/loai-phong")
public class AdminLoaiPhongController {

    @Autowired
    LoaiPhongService repo; // Đây là Service, không phải Repository

    @GetMapping
    public String index(Model model,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        Page<LoaiPhong> result = repo.searchPaged(null, null, null, null, pageable);

        model.addAttribute("loaiPhong", new LoaiPhong());
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("selectedAnhs", List.of());
        model.addAttribute("coPhongLienQuan", coPhongLienQuan(result.getContent()));
        model.addAttribute("title", "Thêm loại phòng");

        // THÊM CÁC DÒNG NÀY:
        model.addAttribute("keyword", null);
        model.addAttribute("minGia", null);
        model.addAttribute("maxGia", null);
        model.addAttribute("soKhach", null);

        return "admin/loai-phong-list";
    }

    @GetMapping("/edit/{id}")
    public String edit(Model model, @PathVariable("id") int id,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        // Gọi searchPaged của Service (đã có)
        Page<LoaiPhong> result = repo.searchPaged(null, null, null, null, pageable);

        model.addAttribute("loaiPhong", repo.findbyid(id));
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("selectedAnhs", repo.findAnhByLoaiPhong(id));
        model.addAttribute("coPhongLienQuan", coPhongLienQuan(result.getContent()));
        model.addAttribute("title", "Sửa loại phòng");
        return "admin/loai-phong-list";
    }

    @PostMapping("/save")
    public String save(RedirectAttributes redirect,
                       @Valid @ModelAttribute("loaiPhong") LoaiPhong l,
                       BindingResult b,
                       @RequestParam(value = "anhIds", required = false) List<UUID> anhIds) {

        if (b.hasErrors()) {
            redirect.addFlashAttribute("error", b.getFieldError().getDefaultMessage());
            return "redirect:/nhan-su/admin/loai-phong";
        }
        if (repo.CheckTrungLoai(l)) {
            redirect.addFlashAttribute("error", "tên loại phòng đã tồn tại");
            return "redirect:/nhan-su/admin/loai-phong";
        }

        repo.save(l, anhIds);
        redirect.addFlashAttribute("success", "lưu loại phòng thành công");
        return "redirect:/nhan-su/admin/loai-phong";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id, RedirectAttributes redirect) {
        String check = repo.checkReference(id);
        LoaiPhong lp = repo.findbyid(id);
        if (check != null) {
            redirect.addFlashAttribute("error", "loại phòng đang được sử dụng ở phòng " + check + " không thể thực hiện xóa ");
            return "redirect:/nhan-su/admin/loai-phong";
        }
        redirect.addFlashAttribute("success", "thực hiện xóa thành công loại phòng: " + lp.getId());
        repo.delete(lp);
        return "redirect:/nhan-su/admin/loai-phong";
    }

    @GetMapping("/tim-kiem")
    public String timKiem(Model model,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "minGia", required = false) String minGiaStr,
                          @RequestParam(value = "maxGia", required = false) String maxGiaStr,
                          @RequestParam(value = "soKhach", required = false) Integer soKhach,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "5") int size) {

        System.out.println("=== TIM KIEM ===");
        System.out.println("keyword raw: '" + keyword + "'");
        System.out.println("minGia raw: '" + minGiaStr + "'");
        System.out.println("maxGia raw: '" + maxGiaStr + "'");
        System.out.println("soKhach raw: " + soKhach);

        // Xử lý keyword: nếu rỗng thì set null
        String keywordValue = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        // Xử lý minGia: chỉ lấy nếu có giá trị > 0
        BigDecimal minGia = null;
        if (minGiaStr != null && !minGiaStr.trim().isEmpty()) {
            try {
                BigDecimal temp = new BigDecimal(minGiaStr.trim().replaceAll(",", ""));
                if (temp.compareTo(BigDecimal.ZERO) > 0) {
                    minGia = temp;
                }
            } catch (NumberFormatException e) {
                // Bỏ qua nếu không phải số
            }
        }

        // Xử lý maxGia: chỉ lấy nếu có giá trị > 0
        BigDecimal maxGia = null;
        if (maxGiaStr != null && !maxGiaStr.trim().isEmpty()) {
            try {
                BigDecimal temp = new BigDecimal(maxGiaStr.trim().replaceAll(",", ""));
                if (temp.compareTo(BigDecimal.ZERO) > 0) {
                    maxGia = temp;
                }
            } catch (NumberFormatException e) {
                // Bỏ qua nếu không phải số
            }
        }

        // Xử lý soKhach: chỉ lấy nếu có giá trị > 0
        Integer soKhachValue = (soKhach != null && soKhach > 0) ? soKhach : null;

        System.out.println("=== SAU XỬ LÝ ===");
        System.out.println("keyword: '" + keywordValue + "'");
        System.out.println("minGia: " + minGia);
        System.out.println("maxGia: " + maxGia);
        System.out.println("soKhach: " + soKhachValue);

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        Page<LoaiPhong> result = repo.searchLoaiPhongPagedNative(
                keywordValue, minGia, maxGia, soKhachValue, pageable
        );

        System.out.println("Total results: " + result.getTotalElements());
        result.getContent().forEach(lp -> System.out.println("  Found: " + lp.getTenLoai()));

        // THÊM VÀO MODEL ĐỂ GIỮ GIÁ TRỊ TRÊN FORM
        model.addAttribute("loaiPhong", new LoaiPhong());
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("selectedAnhs", List.of());
        model.addAttribute("coPhongLienQuan", coPhongLienQuan(result.getContent()));
        model.addAttribute("keyword", keyword); // Giữ nguyên giá trị nhập
        model.addAttribute("minGia", minGiaStr); // Giữ nguyên giá trị nhập
        model.addAttribute("maxGia", maxGiaStr); // Giữ nguyên giá trị nhập
        model.addAttribute("soKhach", soKhach); // Giữ nguyên giá trị nhập
        model.addAttribute("title", "Kết quả tìm kiếm");
        return "admin/loai-phong-list";
    }

    private Map<Integer, Boolean> coPhongLienQuan(List<LoaiPhong> loaiPhongs) {
        Map<Integer, Boolean> ketQua = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            ketQua.put(lp.getId(), repo.checkReference(lp.getId()) != null);
        }
        return ketQua;
    }
}
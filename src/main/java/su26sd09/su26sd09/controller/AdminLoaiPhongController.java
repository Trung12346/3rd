package su26sd09.su26sd09.controller;

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

@Controller
@RequestMapping("/nhan-su/admin/loai-phong")
public class AdminLoaiPhongController {

    @Autowired
    LoaiPhongService repo;

    @GetMapping
    public String index(Model model,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        Page<LoaiPhong> result = repo.searchPaged(null, null, null, null, pageable);

        model.addAttribute("loaiPhong", new LoaiPhong());
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("title", "Thêm loại phòng");
        return "admin/loai-phong-list";
    }

    @GetMapping("/edit/{id}")
    public String edit(Model model, @PathVariable("id") int id,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        Page<LoaiPhong> result = repo.searchPaged(null, null, null, null, pageable);

        model.addAttribute("loaiPhong", repo.findbyid(id));
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("title", "Sửa loại phòng");
        return "admin/loai-phong-list";
    }

    @PostMapping("/save")
    public String save(RedirectAttributes redirect, @ModelAttribute("loaiPhong") LoaiPhong l, BindingResult b) {
        if (b.hasErrors()) {
            redirect.addFlashAttribute("error", b.getFieldError().getDefaultMessage());
            return "redirect:/nhan-su/admin/loai-phong";
        }
        if (repo.CheckTrungLoai(l)) {
            redirect.addFlashAttribute("error", "tên loại phòng đã tồn tại");
            return "redirect:/nhan-su/admin/loai-phong";
        }
        repo.save(l);
        redirect.addFlashAttribute("success", "lưu loại phòng thành công");
        return "redirect:/nhan-su/admin/loai-phong";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id, RedirectAttributes redirect) {
        LoaiPhong p = repo.findbyid(id);
        if (p != null) {
            repo.delete(p);
            redirect.addFlashAttribute("success", "xóa loại phòng thành công");
        }
        return "redirect:/nhan-su/admin/loai-phong";
    }

    @GetMapping("/tim-kiem")
    public String timKiem(Model model,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "minGia", required = false) BigDecimal minGia,
                          @RequestParam(value = "maxGia", required = false) BigDecimal maxGia,
                          @RequestParam(value = "soKhach", required = false) Integer soKhach,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        Page<LoaiPhong> result = repo.searchPaged(keyword, minGia, maxGia, soKhach, pageable);

        model.addAttribute("loaiPhong", new LoaiPhong());
        model.addAttribute("loaiPhongs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("keyword", keyword);
        model.addAttribute("minGia", minGia);
        model.addAttribute("maxGia", maxGia);
        model.addAttribute("soKhach", soKhach);
        model.addAttribute("title", "Kết quả tìm kiếm");
        return "admin/loai-phong-list";
    }
}
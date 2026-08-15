package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.Anh;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.service.LoaiPhongService;
import su26sd09.su26sd09.repository.AnhRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping("/nhan-su/admin/loai-phong")
public class AdminLoaiPhongController {

    @Autowired
    LoaiPhongService repo;
    @Autowired
    AnhRepository anhrepo;

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
    public String save(RedirectAttributes redirect,
                       @Valid @ModelAttribute("loaiPhong") LoaiPhong l,
                       BindingResult b,
                       @RequestParam(value = "anhId", required = false) String anhId) {

        if (b.hasErrors()) {
            redirect.addFlashAttribute("error", b.getFieldError().getDefaultMessage());
            return "redirect:/nhan-su/admin/loai-phong";
        }
        if (repo.CheckTrungLoai(l)) {
            redirect.addFlashAttribute("error", "tên loại phòng đã tồn tại");
            return "redirect:/nhan-su/admin/loai-phong";
        }

        if (anhId != null && !anhId.isBlank()) {
            l.setMaAnh(anhrepo.getReferenceById(UUID.fromString(anhId)));
        } else {
            l.setMaAnh(null);
        }

        repo.save(l);
        redirect.addFlashAttribute("success", "lưu loại phòng thành công");
        return "redirect:/nhan-su/admin/loai-phong";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id,RedirectAttributes redirect){
        String check = repo.checkReference(id);
        LoaiPhong lp = repo.findbyid(id);
        if (check != null){
            redirect.addFlashAttribute("error","loại phòng đang được sử dụng ở phòng " + check +  " không thể thực hiện xóa ");
            return "redirect:/nhan-su/admin/loai-phong";
        }
        redirect.addFlashAttribute("success","thực hiện xóa thành công loại phòng: " + lp.id );
        repo.delete(lp);
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
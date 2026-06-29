package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.repository.DanhGiaRepo;

@Controller
@RequestMapping("/admin/loai-phong/{id}/danh-gia")
public class AdminDanhGiaController {
    @Autowired
    DanhGiaRepo dgRepo;

    @GetMapping
    public String get_0(
            Model model,
            @PathVariable("id") Integer id,
            @RequestParam(value = "noi-dung", required = false) String noiDung
    )
    {
        model.addAttribute("danhGias", dgRepo.findByLoaiPhong(id, noiDung));
        return "admin/danh-gia-list";
    }

    @PostMapping("/{dg-id}")
    public String post_0(
            @PathVariable("dg-id") Integer dgId,
            @PathVariable("id") Integer id,
            @RequestParam("phanHoi") String phanHoi,
            RedirectAttributes redirect
    )
    {
        DanhGia dg = dgRepo.findById(dgId).get();
        if(phanHoi == null || phanHoi.isBlank()) {
            redirect.addFlashAttribute("phError", "phản hồi trống");
            return String.format("redirect:/admin/loai-phong/%d/danh-gia", id);
        }
        dg.phanHoi = phanHoi;
        dgRepo.save(dg);
        redirect.addFlashAttribute("id", id);
        redirect.addFlashAttribute("phSuccess", "lưu thành công");
        return String.format("redirect:/admin/loai-phong/%d/danh-gia", id);
    }
}

package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.Dich_vu;
import su26sd09.su26sd09.service.DichVuService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/nhan-vien/dich-vu")
public class nhanVienQLDVController {
    @Autowired
    private DichVuService dichVuService;

    @GetMapping
    public String index(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "trangThai", defaultValue = "") String trangThai,
            Model model
    ) {
        Dich_vu dv = new Dich_vu();
        dv.setHoat_dong(true);

        loadFormAndList(model, dv, keyword, trangThai, "Thêm dịch vụ");
        return "nhan-vien/dich-vu-list";
    }

    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable("id") Integer id,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "trangThai", defaultValue = "") String trangThai,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Dich_vu dv = dichVuService.findById(id);
        if (dv == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy dịch vụ");
            return "redirect:/nhan-vien/dich-vu";
        }
        loadFormAndList(model, dv, keyword, trangThai, "Cập nhật dịch vụ");
        return "nhan-vien/dich-vu-list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Dich_vu dichVu, RedirectAttributes redirectAttributes) {
        dichVuService.save(dichVu);
        redirectAttributes.addFlashAttribute("success", "Lưu dịch vụ thành công");
        return "redirect:/nhan-vien/dich-vu";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        try {
            dichVuService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa dịch vụ thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa dịch vụ này vì đang được sử dụng trong hóa đơn/đặt phòng");
        }
        return "redirect:/nhan-vien/dich-vu";
    }

    private void loadFormAndList(Model model, Dich_vu dv, String keyword, String trangThai, String title) {
        List<Dich_vu> dichVus = dichVuService.search(keyword, trangThai);
        Map<Integer, Long> soLuongSuDung = dichVuService.soLuongSuDungTheoDichVu();
        Dich_vu topDichVu = dichVuService.dichVuDuocSuDungNhieuNhat();
        long topSoLuong = topDichVu != null ? soLuongSuDung.getOrDefault(topDichVu.getId(), 0L) : 0L;
        BigDecimal tongTien = dichVuService.tongTienDichVu();

        model.addAttribute("dichVu", dv);
        model.addAttribute("dichVus", dichVus);
        model.addAttribute("keyword", keyword);
        model.addAttribute("trangThai", trangThai);
        model.addAttribute("title", title);

        model.addAttribute("soLuongSuDung", soLuongSuDung);
        model.addAttribute("tongSoDichVu", dichVuService.countAll());
        model.addAttribute("tongDichVuHoatDong", dichVuService.countActive());
        model.addAttribute("topDichVu", topDichVu);
        model.addAttribute("topSoLuong", topSoLuong);
        model.addAttribute("tongTienDichVu", tongTien);
    }
}

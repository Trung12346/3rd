package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.PhongService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/phong")
public class AdminPhongController {

    @Autowired
    private PhongService phongService;

    @GetMapping
    public String index(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model
    ) {
        Phong phong = new Phong();
        phong.setHoatDong(true);
        phong.setTrangThai("Trong");

        loadFormAndList(model, phong, List.of(), keyword, "Thêm phòng");
        return "admin/phong-list";
    }

    @GetMapping("/create")
    public String create() {
        return "redirect:/admin/phong";
    }

    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable("id") int id,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Phong phong = phongService.findById(id);

        if (phong == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng");
            return "redirect:/admin/phong";
        }

        loadFormAndList(model, phong, phongService.findTienNghiIdsByPhong(id), keyword, "Cập nhật phòng");
        return "admin/phong-list";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute Phong phong,
            @RequestParam(name = "loaiPhongId") int loaiPhongId,
            @RequestParam(name = "tienNghiIds", required = false) List<Integer> tienNghiIds,
            RedirectAttributes redirectAttributes
    ) {
        phongService.save(phong, loaiPhongId, tienNghiIds);
        redirectAttributes.addFlashAttribute("success", "Lưu phòng thành công");
        return "redirect:/admin/phong";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        phongService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Xóa phòng thành công");
        return "redirect:/admin/phong";
    }

    private void loadFormAndList(
            Model model,
            Phong phong,
            List<Integer> selectedTienNghiIds,
            String keyword,
            String title
    ) {
        List<Phong> phongs = phongService.search(keyword);

        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
        for (Phong item : phongs) {
            tienNghiTheoPhong.put(item.getMaPhong(), phongService.findTenTienNghiByPhong(item.getMaPhong()));
        }

        model.addAttribute("phong", phong);
        model.addAttribute("phongs", phongs);
        model.addAttribute("loaiPhongs", phongService.findAllLoai());
        model.addAttribute("tienNghis", phongService.findAllTienNghi());
        model.addAttribute("selectedTienNghiIds", selectedTienNghiIds);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("keyword", keyword);
        model.addAttribute("title", title);
    }
}

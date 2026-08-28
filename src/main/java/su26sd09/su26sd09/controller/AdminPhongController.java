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
@RequestMapping("/nhan-su/admin/phong")
public class AdminPhongController {

    @Autowired
    private PhongService phongService;

    private static final int PAGE_SIZE = 10;

    @GetMapping
    public String index(
            @RequestParam(name = "soPhong", required = false, defaultValue = "") String soPhong,
            @RequestParam(name = "loaiPhongId", required = false) Integer loaiPhongId,
            @RequestParam(name = "soTang", required = false) Integer soTang,
            @RequestParam(name = "trangThai", required = false, defaultValue = "") String trangThai,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model
    ) {
        Phong phong = new Phong();
        phong.setHoatDong(true);
        phong.setTrangThai("Trong");

        loadFormAndList(model, phong, List.of(), soPhong, loaiPhongId, soTang, trangThai, page, "Thêm phòng");
        return "admin/phong-list";
    }

    @GetMapping("/create")
    public String create() {
        return "redirect:/nhan-su/admin/phong";
    }

    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable("id") int id,
            @RequestParam(name = "soPhong", required = false, defaultValue = "") String soPhong,
            @RequestParam(name = "loaiPhongId", required = false) Integer loaiPhongId,
            @RequestParam(name = "soTang", required = false) Integer soTang,
            @RequestParam(name = "trangThai", required = false, defaultValue = "") String trangThai,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Phong phong = phongService.findById(id);

        if (phong == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng");
            return "redirect:/nhan-su/admin/phong";
        }

        if (phongService.countGiaoDichChuaHoanTatByPhong(id) > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể sửa: phòng đang có giao dịch (đặt phòng/đang ở) chưa hoàn tất");
            return "redirect:/nhan-su/admin/phong";
        }

        loadFormAndList(model, phong, phongService.findTienNghiIdsByPhong(id),
                soPhong, loaiPhongId, soTang, trangThai, page, "Cập nhật phòng");
        return "admin/phong-list";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute Phong phong,
            @RequestParam(name = "loaiPhongId") int loaiPhongId,
            @RequestParam(name = "tienNghiIds", required = false) List<Integer> tienNghiIds,
            RedirectAttributes redirectAttributes
    ) {
        // Trang thai & Hoat dong khong con cho chinh sua tren form (an di) ->
        // luon ep ve mac dinh "Trong" / "Co" o phia server, khong phu thuoc
        // vao gia tri client gui len.
        phong.setTrangThai("Trong");
        phong.setHoatDong(true);

        for (Phong p : phongService.findAllPhongIncludingInactive()){
            if (p.getSoPhong().equals(phong.getSoPhong()) && p.getMaPhong() != phong.getMaPhong()){
                redirectAttributes.addFlashAttribute("error","số phòng này đã tồn tại ");
                return "redirect:/nhan-su/admin/phong";
            }
        }
        phongService.save(phong, loaiPhongId, tienNghiIds);
        redirectAttributes.addFlashAttribute("success", "Lưu phòng thành công");
        return "redirect:/nhan-su/admin/phong";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        long soGiaoDichChuaHoanTat = phongService.countGiaoDichChuaHoanTatByPhong(id);
        if (soGiaoDichChuaHoanTat > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa: phòng đang có giao dịch (đặt phòng/đang ở) chưa hoàn tất");
            return "redirect:/nhan-su/admin/phong";
        }
        phongService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Xóa phòng thành công");
        return "redirect:/nhan-su/admin/phong";
    }

    @PostMapping("/activate/{id}")
    public String activate(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        phongService.activate(id);
        redirectAttributes.addFlashAttribute("success", "Kích hoạt phòng thành công");
        return "redirect:/nhan-su/admin/phong";
    }

    private void loadFormAndList(
            Model model,
            Phong phong,
            List<Integer> selectedTienNghiIds,
            String soPhong,
            Integer loaiPhongId,
            Integer soTang,
            String trangThai,
            int page,
            String title
    ) {
        org.springframework.data.domain.Page<Phong> phongPage =
                phongService.searchFiltered(soPhong, loaiPhongId, soTang, trangThai, page, PAGE_SIZE);
        List<Phong> phongs = phongPage.getContent();

        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
        Map<Integer, Boolean> coGiaoDichChuaHoanTat = new HashMap<>();
        for (Phong item : phongs) {
            tienNghiTheoPhong.put(item.getMaPhong(), phongService.findTenTienNghiByPhong(item.getMaPhong()));
            coGiaoDichChuaHoanTat.put(item.getMaPhong(),
                    phongService.countGiaoDichChuaHoanTatByPhong(item.getMaPhong()) > 0);
        }

        model.addAttribute("phong", phong);
        model.addAttribute("phongs", phongs);
        model.addAttribute("loaiPhongs", phongService.findAllLoai());
        model.addAttribute("tienNghis", phongService.findAllTienNghi());
        model.addAttribute("selectedTienNghiIds", selectedTienNghiIds);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("coGiaoDichChuaHoanTat", coGiaoDichChuaHoanTat);

        model.addAttribute("soPhongFilter", soPhong);
        model.addAttribute("loaiPhongIdFilter", loaiPhongId);
        model.addAttribute("soTangFilter", soTang);
        model.addAttribute("trangThaiFilter", trangThai);

        model.addAttribute("currentPage", phongPage.getNumber());
        model.addAttribute("totalPages", phongPage.getTotalPages());
        model.addAttribute("totalItems", phongPage.getTotalElements());

        model.addAttribute("title", title);
    }
}

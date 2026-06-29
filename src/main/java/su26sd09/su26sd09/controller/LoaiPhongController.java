package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.PhongService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/loai-phong")
public class LoaiPhongController {

    @Autowired
    private PhongService phongService;

    @GetMapping
    public String index(Model model) {
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        loadLoaiPhongList(model, loaiPhongs);
        // Thêm ảnh cho các loại phòng
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            anhLoaiPhong.put(lp.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        return "loai-phong";
    }

    @GetMapping("/tim-kiem")
    public String timKiem(
            @RequestParam(name = "ngayNhan", required = false) String ngayNhan,
            @RequestParam(name = "ngayTra", required = false) String ngayTra,
            @RequestParam(name = "nguoiLon", required = false) Integer nguoiLon,
            @RequestParam(name = "treEm", required = false) Integer treEm,
            @RequestParam(name = "mucGia", required = false) String mucGia,
            Model model
    ) {
        List<LoaiPhong> loaiPhongs = phongService.searchLoaiPhong(mucGia, nguoiLon, treEm);
        loadLoaiPhongList(model, loaiPhongs);
        
        // Thêm ảnh cho các loại phòng
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            anhLoaiPhong.put(lp.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        
        model.addAttribute("ngayNhan", ngayNhan);
        model.addAttribute("ngayTra", ngayTra);
        model.addAttribute("nguoiLon", nguoiLon);
        model.addAttribute("treEm", treEm);
        model.addAttribute("mucGia", mucGia);
        return "loai-phong";
    }

    @GetMapping("/{id}")
    public String phongTheoLoai(
            @PathVariable("id") int id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        LoaiPhong loaiPhong = phongService.findLoaiPhongById(id);
        if (loaiPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy loại phòng");
            return "redirect:/loai-phong";
        }

        List<Phong> phongs = phongService.findPhongTheoLoai(id);
        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
        for (Phong phong : phongs) {

            tienNghiTheoPhong.put(phong.getMaPhong(), phongService.findTenTienNghiByPhong(phong.getMaPhong()));
        }

        // Lấy tất cả loại phòng cho carousel và dropdown menu
        List<LoaiPhong> tatCaLoaiPhong = phongService.findAllLoai();
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : tatCaLoaiPhong) {
            anhLoaiPhong.put(lp.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }

        model.addAttribute("loaiPhong", loaiPhong);
        model.addAttribute("phongs", phongs);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("loaiPhongs", tatCaLoaiPhong);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        return "phong-theo-loai";
    }

    private void loadLoaiPhongList(Model model, List<LoaiPhong> loaiPhongs) {
        Map<Integer, Long> soPhongTrongTheoLoai = new HashMap<>();
        for (LoaiPhong loaiPhong : loaiPhongs) {
            soPhongTrongTheoLoai.put(loaiPhong.getId(), phongService.countPhongTrongTheoLoai(loaiPhong.getId()));
        }

        model.addAttribute("loaiPhongs", loaiPhongs);
        model.addAttribute("soPhongTrongTheoLoai", soPhongTrongTheoLoai);
    }
}

package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.repository.KhachHangRepository;
import su26sd09.su26sd09.service.CustomerUserDetailsService;
import su26sd09.su26sd09.service.PhongService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequestMapping("/home")
@Controller
public class Home {

    @Autowired
    CustomerUserDetailsService repo;

    @Autowired
    KhachHangRepository UserRepo;

    @Autowired
    private PhongService phongService;

    @GetMapping("")
    public String home(Model model) {
        loadHomeData(model);
        return "index";
    }

    @GetMapping(value = "", params = {"tenPhong", "tenLoaiPhong", "minGia", "maxGia"})
    public String searchRooms(
            @RequestParam(name = "tenPhong", required = false) String tenPhong,
            @RequestParam(name = "tenLoaiPhong", required = false) String tenLoaiPhong,
            @RequestParam(name = "minGia", required = false) BigDecimal minGia,
            @RequestParam(name = "maxGia", required = false) BigDecimal maxGia,
            Model model
    ) {
        loadHomeData(model);
        model.addAttribute("tenPhong", tenPhong);
        model.addAttribute("tenLoaiPhong", tenLoaiPhong);
        model.addAttribute("minGia", minGia);
        model.addAttribute("maxGia", maxGia);
        model.addAttribute("dangTimKiem", true);

        if (minGia != null && minGia.compareTo(BigDecimal.ZERO) < 0) {
            model.addAttribute("homeSearchError", "Giá từ không được nhỏ hơn 0.");
            return "index";
        }
        if (maxGia != null && maxGia.compareTo(BigDecimal.ZERO) < 0) {
            model.addAttribute("homeSearchError", "Giá đến không được nhỏ hơn 0.");
            return "index";
        }
        if (minGia != null && maxGia != null && minGia.compareTo(maxGia) > 0) {
            model.addAttribute("homeSearchError", "Giá từ không được lớn hơn giá đến.");
            return "index";
        }

        List<Phong> phongsTimKiem = phongService.findAllPhong().stream()
                .filter(phong -> "Trong".equalsIgnoreCase(phong.getTrangThai()))
                .filter(phong -> matchTenPhong(phong, tenPhong))
                .filter(phong -> matchTenLoaiPhong(phong, tenLoaiPhong))
                .filter(phong -> phong.getGiaMoiDem() != null)
                .filter(phong -> minGia == null || phong.getGiaMoiDem().compareTo(minGia) >= 0)
                .filter(phong -> maxGia == null || phong.getGiaMoiDem().compareTo(maxGia) <= 0)
                .toList();

        model.addAttribute("phongsTimKiem", phongsTimKiem);
        return "index";
    }

    private void loadHomeData(Model model) {
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        Map<Integer, Long> soPhongTrongTheoLoai = new HashMap<>();
        Map<Integer, String> anhLoaiPhong = new HashMap<>();

        for (LoaiPhong loai : loaiPhongs) {
            soPhongTrongTheoLoai.put(loai.getId(), phongService.countPhongTrongTheoLoai(loai.getId()));
            anhLoaiPhong.put(loai.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }

        model.addAttribute("loaiPhongs", loaiPhongs);
        model.addAttribute("soPhongTrongTheoLoai", soPhongTrongTheoLoai);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
    }

    private boolean matchTenPhong(Phong phong, String tenPhong) {
        String keyword = normalize(tenPhong);
        if (keyword.isBlank()) {
            return true;
        }
        return containsNormalized(phong.getSoPhong(), keyword)
                || containsNormalized(phong.getMoTa(), keyword);
    }

    private boolean matchTenLoaiPhong(Phong phong, String tenLoaiPhong) {
        String keyword = normalize(tenLoaiPhong);
        if (keyword.isBlank()) {
            return true;
        }
        return phong.getLoaiPhong() != null
                && containsNormalized(phong.getLoaiPhong().getTenLoai(), keyword);
    }

    private boolean containsNormalized(String value, String keyword) {
        return value != null && normalize(value).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
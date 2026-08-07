package su26sd09.su26sd09.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.repository.PhongAnhRepository;
import su26sd09.su26sd09.service.PhongService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/loai-phong")
public class LoaiPhongController {

    private static final String ANH_MAC_DINH =
            "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80";

    @Autowired
    private PhongService phongService;

    @Autowired
    private PhongAnhRepository phongAnhRepository;

    @GetMapping
    public String index(Model model) {
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        loadLoaiPhongList(model, loaiPhongs);
        model.addAttribute("anhLoaiPhong", buildAnhLoaiPhong(loaiPhongs));
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
        model.addAttribute("ngayNhan", ngayNhan);
        model.addAttribute("ngayTra", ngayTra);
        model.addAttribute("nguoiLon", nguoiLon);
        model.addAttribute("treEm", treEm);
        model.addAttribute("mucGia", mucGia);

        LocalDateTime ngayNhanPhong = null;
        LocalDateTime ngayTraPhong = null;
        boolean coNgay = ngayNhan != null && !ngayNhan.isBlank() && ngayTra != null && !ngayTra.isBlank();

        if (coNgay) {
            try {
                ngayNhanPhong = LocalDate.parse(ngayNhan.trim()).atStartOfDay();
                ngayTraPhong = LocalDate.parse(ngayTra.trim()).atStartOfDay();
            } catch (DateTimeParseException e) {
                model.addAttribute("timKiemError", "Định dạng ngày không hợp lệ.");
                model.addAttribute("loaiPhongs", List.of());
                model.addAttribute("soPhongTrongTheoLoai", Map.of());
                model.addAttribute("anhLoaiPhong", Map.of());
                return "loai-phong-ket-qua";
            }
            if (!ngayTraPhong.isAfter(ngayNhanPhong)) {
                model.addAttribute("timKiemError", "Ngày trả phòng phải sau ngày nhận phòng.");
                model.addAttribute("loaiPhongs", List.of());
                model.addAttribute("soPhongTrongTheoLoai", Map.of());
                model.addAttribute("anhLoaiPhong", Map.of());
                return "loai-phong-ket-qua";
            }
        }

        PhongService.LoaiPhongSearchResult ketQua =
                phongService.searchLoaiPhongKhaDung(ngayNhanPhong, ngayTraPhong, nguoiLon, treEm, mucGia);

        model.addAttribute("loaiPhongs", ketQua.getLoaiPhongs());
        model.addAttribute("soPhongTrongTheoLoai", ketQua.getSoPhongKhaDungTheoLoai());
        model.addAttribute("anhLoaiPhong", buildAnhLoaiPhong(ketQua.getLoaiPhongs()));

        if (coNgay) {
            long soDem = java.time.temporal.ChronoUnit.DAYS.between(ngayNhanPhong.toLocalDate(), ngayTraPhong.toLocalDate());
            model.addAttribute("soDem", soDem);
        }

        return "loai-phong-ket-qua";
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

        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();

        HashMap<Integer, UUID> thumbAnhs = new HashMap<>();
        for (Phong p: phongs
        ) {
            Integer pid = p.getMaPhong();
            PhongAnh pa = phongAnhRepository.findByMaPhongFirst(p.getMaPhong());
            thumbAnhs.put(
                    pid,
                    pa != null ? pa.maAnh.maAnh : null
            );
        }

        List<LoaiPhong> tatCaLoaiPhong = phongService.findAllLoai();
        Map<Integer, String> anhLoaiPhong = buildAnhLoaiPhong(tatCaLoaiPhong);

        Map<Integer, RoomBookingGuardDTO> bookingGuardByPhong = phongService.buildRoomGuards(phongs);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Map<Integer, String> khoaLichJsonByPhong = new HashMap<>();

        for (Map.Entry<Integer, RoomBookingGuardDTO> entry : bookingGuardByPhong.entrySet()) {
            try {
                khoaLichJsonByPhong.put(
                        entry.getKey(),
                        mapper.writeValueAsString(entry.getValue().getDanhSachKhoaLich())
                );
            } catch (Exception e) {
                khoaLichJsonByPhong.put(entry.getKey(), "[]");
            }
        }
        model.addAttribute("khoaLichJsonByPhong", khoaLichJsonByPhong);

        model.addAttribute("loaiPhong", loaiPhong);
        model.addAttribute("thumbAnhs", thumbAnhs);
        model.addAttribute("phongs", phongs);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("loaiPhongs", tatCaLoaiPhong);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        model.addAttribute("bookingGuardByPhong", bookingGuardByPhong);
        model.addAttribute("gioNhanToiDaMacDinh", LocalTime.of(11,0));
        model.addAttribute("gioTraToiDaMacDinh", LocalTime.of(18,30));
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

    private Map<Integer, String> buildAnhLoaiPhong(List<LoaiPhong> loaiPhongs) {
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            if (lp.getMaAnh() != null) {
                anhLoaiPhong.put(lp.getId(), "/media/" + lp.getMaAnh().getMaAnh());
            } else {
                anhLoaiPhong.put(lp.getId(), ANH_MAC_DINH);
            }
        }
        return anhLoaiPhong;
    }
}
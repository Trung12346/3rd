package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import su26sd09.su26sd09.dto.LichPhongEventDTO;
import su26sd09.su26sd09.dto.LichPhongLoaiPhongDTO;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.LoaiPhongRepository;
import su26sd09.su26sd09.repository.PhongRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trang quan ly lich dat phong (property management calendar) - dung chung
 * cho ca STAFF va ADMIN. Day la 1 single page application: trang chi render
 * shell 1 lan, phan danh sach LoaiPhong va luoi lich thang duoc nap qua cac
 * API JSON ben duoi bang fetch() tren client.
 */
@Controller
@RequestMapping("/nhan-su/lich-phong")
public class LichPhongController {

    @Autowired
    private LoaiPhongRepository loaiPhongRepo;

    @Autowired
    private PhongRepository phongRepo;

    @Autowired
    private ChiTietDatPhongRepo chiTietDatPhongRepo;

    @GetMapping
    public String index() {
        return "nhan-vien/lich-phong";
    }

    @GetMapping("/api/loai-phong")
    @ResponseBody
    public List<LichPhongLoaiPhongDTO> apiLoaiPhong() {
        List<LoaiPhong> all = loaiPhongRepo.findAllByOrderByTenLoaiAsc();
        return all.stream()
                .map(lp -> new LichPhongLoaiPhongDTO(
                        lp.getId(),
                        lp.getTenLoai(),
                        lp.getSucChuaToiDa(),
                        lp.getGiaCoBan(),
                        lp.getMaAnh() != null ? lp.getMaAnh().getSrc() : null,
                        phongRepo.countByLoaiPhongIdAndHoatDongTrue(lp.getId())
                ))
                .toList();
    }

    @GetMapping("/api/loai-phong/{id}/bookings")
    @ResponseBody
    public List<LichPhongEventDTO> apiBookings(
            @PathVariable("id") int id,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atStartOfDay();

        List<ChiTietDatPhong> rows = chiTietDatPhongRepo.findForCalendar(id, startDt, endDt);

        return rows.stream()
                .map(c -> new LichPhongEventDTO(
                        c.getId(),
                        c.getD().getId(),
                        c.getP().getMaPhong(),
                        c.getP().getSoPhong(),
                        c.getP().getSoTang(),
                        c.getD().getNgaydatPhong(),
                        c.getD().getNgaytraPhong(),
                        c.getD().getTrangThai(),
                        c.getD().getHoten(),
                        c.getD().getSdt(),
                        c.getD().getEmail(),
                        c.getD().getSonguoiLon(),
                        c.getD().getSotreEm(),
                        c.getD().getMaTraCuu()
                ))
                .toList();
    }
}

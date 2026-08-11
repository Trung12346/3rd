package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.ChiTietDichVuService;
import su26sd09.su26sd09.service.DatPhongService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cho phep khach dat phong khong co tai khoan tra cuu lai don cua minh
 * chi bang ma tra cuu 6 ky tu hex duoc sinh tu dong luc dat phong.
 */
@Controller
@RequestMapping("/tra-cuu-don")
public class TraCuuDonController {

    @Autowired
    private DatPhongService datPhongService;

    @Autowired
    private ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    private ChiTietDichVuService ctdvService;

    @GetMapping
    public String showForm() {
        return "tra-cuu-don";
    }

    @PostMapping
    public String traCuu(
            @RequestParam("maTraCuu") String maTraCuu,
            Model model
    ) {
        DatPhong dp = datPhongService.findByMaTraCuu(maTraCuu);

        model.addAttribute("maTraCuuNhap", maTraCuu);

        if (dp == null) {
            model.addAttribute("traCuuError",
                    "Không tìm thấy đơn đặt phòng nào ứng với mã tra cứu này. Vui lòng kiểm tra lại.");
            return "tra-cuu-don";
        }

        List<ChiTietDatPhong> chiTietPhong = chiTietDatPhongService.findByDatPhongId(dp.getId());
        List<Chi_tiet_dich_vu> chiTietDichVu = ctdvService.findByDatPhongId(dp.getId());

        BigDecimal tienPhong = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : chiTietPhong) {
            if (ct.getGiaKhiDat() != null) {
                tienPhong = tienPhong.add(ct.getGiaKhiDat());
            }
        }

        BigDecimal tienDichVu = BigDecimal.ZERO;
        if (chiTietDichVu != null) {
            for (Chi_tiet_dich_vu dv : chiTietDichVu) {
                if (dv.getDonGia() != null) {
                    tienDichVu = tienDichVu.add(dv.getDonGia());
                }
            }
        }

        model.addAttribute("datPhong", dp);
        model.addAttribute("chiTietPhong", chiTietPhong);
        model.addAttribute("chiTietDichVu", chiTietDichVu);
        model.addAttribute("tienPhong", tienPhong);
        model.addAttribute("tienDichVu", tienDichVu);
        model.addAttribute("tongTien", tienPhong.add(tienDichVu));

        return "tra-cuu-don";
    }
}

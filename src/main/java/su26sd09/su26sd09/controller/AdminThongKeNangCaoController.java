package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.dto.AdvancedThongKeDTO;
import su26sd09.su26sd09.service.ThongKeService;

import java.time.LocalDate;
import java.util.List;

/**
 * Cac endpoint "Phan Tich Nang Cao" phuc vu trang Thong Ke Doanh Thu:
 * KPI chuyen sau (RevPAR, ADR, ty le huy, ty le khach quay lai), co cau doanh thu,
 * phuong thuc thanh toan, trang thai don dat phong, va cac bang xep hang top phong /
 * dich vu / khach hang. Duoc tach thanh controller rieng de khong dong cham
 * AdminThongKeDoanhThu.java hien co.
 */
@Controller
@RequestMapping("/nhan-su/admin/thong-ke")
public class AdminThongKeNangCaoController {

    @Autowired
    ThongKeService tks;

    @GetMapping("/phan-tich-nang-cao")
    public ResponseEntity<AdvancedThongKeDTO> phanTichNangCao(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    ) {
        return ResponseEntity.ok(tks.getAdvancedStats(tuNgay, denNgay));
    }

    @GetMapping("/top-phong")
    public ResponseEntity<List<Object[]>> topPhong(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    ) {
        return ResponseEntity.ok(tks.getTopPhong(tuNgay, denNgay));
    }

    @GetMapping("/top-dich-vu")
    public ResponseEntity<List<Object[]>> topDichVu(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    ) {
        return ResponseEntity.ok(tks.getTopDichVu(tuNgay, denNgay));
    }

    @GetMapping("/top-khach-hang")
    public ResponseEntity<List<Object[]>> topKhachHang(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    ) {
        return ResponseEntity.ok(tks.getTopKhachHang(tuNgay, denNgay));
    }
}

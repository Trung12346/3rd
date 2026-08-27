package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.constants.LichSuHoatDongConstants;
import su26sd09.su26sd09.entity.LichSuHoatDong;
import su26sd09.su26sd09.service.LichSuHoatDongService;

import java.time.LocalDate;

/**
 * Trang quan tri: xem nhat ky hoat dong (audit log) cua nhan su.
 * Chi ADMIN duoc truy cap (da khoa boi SecurityConfig: "/nhan-su/admin/**").
 */
@Controller
@RequestMapping("/nhan-su/admin/lich-su-hoat-dong")
public class AdminLichSuHoatDongController {

    @Autowired
    private LichSuHoatDongService lichSuHoatDongService;

    @GetMapping
    public String index(
            @RequestParam(required = false) String hoTenNv,
            @RequestParam(required = false) String loaiHanhDong,
            @RequestParam(required = false) String doiTuong,
            @RequestParam(required = false) Integer maDoiTuong,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tuNgay,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate denNgay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Page<LichSuHoatDong> logPage = lichSuHoatDongService.search(
                hoTenNv, loaiHanhDong, doiTuong, maDoiTuong, tuNgay, denNgay, page, size);

        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("logPage", logPage);

        model.addAttribute("hoTenNv", hoTenNv);
        model.addAttribute("loaiHanhDong", loaiHanhDong);
        model.addAttribute("doiTuong", doiTuong);
        model.addAttribute("maDoiTuong", maDoiTuong);
        model.addAttribute("tuNgay", tuNgay);
        model.addAttribute("denNgay", denNgay);

        model.addAttribute("danhSachLoaiHanhDong", LichSuHoatDongConstants.TAT_CA_LOAI_HANH_DONG);
        model.addAttribute("danhSachDoiTuong", lichSuHoatDongService.danhSachDoiTuong());

        return "admin/lich-su-hoat-dong-list";
    }
}

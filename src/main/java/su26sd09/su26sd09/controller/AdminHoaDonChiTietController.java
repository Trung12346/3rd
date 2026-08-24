package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.entity.ThanhToan;
import su26sd09.su26sd09.repository.ThanhToanRepo;
import su26sd09.su26sd09.service.HoaDonService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only invoice detail page (separate from the create/edit form served by
 * adminHoaDonController). Left as its own controller so the existing invoice
 * CRUD flow in adminHoaDonController.java stays untouched.
 */
@Controller
@RequestMapping("/nhan-su/hoa-don")
public class AdminHoaDonChiTietController {

    @Autowired
    HoaDonService hoaDonService;

    @Autowired
    ThanhToanRepo thanhToanRepo;

    @GetMapping("/chi-tiet/{id}")
    public String chiTiet(@PathVariable int id, Model model) {
        HoaDon hoaDon = hoaDonService.findById(id);
        if (hoaDon == null) {
            return "redirect:/nhan-su/admin/hoa-don";
        }

        DatPhong datPhong = hoaDon.getD();
        List<ChiTietDatPhong> phongList = datPhong != null && datPhong.getChiTietDatPhongs() != null
                ? datPhong.getChiTietDatPhongs()
                : List.of();
        List<Chi_tiet_dich_vu> dichVuList = datPhong != null && datPhong.getCtdv() != null
                ? datPhong.getCtdv()
                : List.of();
        List<ThanhToan> thanhToans = thanhToanRepo.findByH_IdOrderByNgaythanhToanAsc(id);

        // Phan tach lich su thanh toan (loaiGiaoDich = "Thu tien") vs hoan tien (loaiGiaoDich = "Hoan tien")
        List<ThanhToan> thanhToanList = new ArrayList<>();
        List<ThanhToan> hoanTienList = new ArrayList<>();
        for (ThanhToan t : thanhToans) {
            if (t != null && "Hoan tien".equalsIgnoreCase(t.getLoaiGiaoDich())) {
                hoanTienList.add(t);
            } else {
                thanhToanList.add(t);
            }
        }
        BigDecimal tongHoan = hoaDon.getDaHoanTra() != null ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;

        // Tinh tong phu phi ngoai gio (100k/loi) tu cac phong trong don
        BigDecimal tongPhuThu = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        // Tinh tong phu thu check-in som / check-out muon tu cac dich vu co loaiDv = "Phu thu"
        // (phan biet voi tongPhuThu o tren - day la phu phi phong). Day la dong tien rieng
        // se hien thi tren hoa don de minh bach, nghiep vu penalty.
        BigDecimal tongPhuThuCheckInSom = BigDecimal.ZERO;
        BigDecimal tongDichVuThuong = BigDecimal.ZERO;
        for (Chi_tiet_dich_vu ctdv : dichVuList) {
            if (ctdv == null || ctdv.getDonGia() == null) continue;
            String loai = ctdv.getDv() != null ? ctdv.getDv().getLoaiDv() : null;
            if ("Phu thu".equalsIgnoreCase(loai)) {
                tongPhuThuCheckInSom = tongPhuThuCheckInSom.add(ctdv.getDonGia());
            } else {
                tongDichVuThuong = tongDichVuThuong.add(ctdv.getDonGia());
            }
        }

        BigDecimal tongTien = hoaDon.getTongTien() != null ? hoaDon.getTongTien() : BigDecimal.ZERO;
        BigDecimal daThanhToan = hoaDon.getDaThanhToan() != null ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal conLai = tongTien.subtract(daThanhToan);

        String trangThaiThanhToanLabel;
        String trangThaiThanhToanClass;
        if (daThanhToan.compareTo(BigDecimal.ZERO) <= 0) {
            trangThaiThanhToanLabel = "Chưa thanh toán";
            trangThaiThanhToanClass = "warning";
        } else if (conLai.compareTo(BigDecimal.ZERO) > 0) {
            trangThaiThanhToanLabel = "Còn nợ";
            trangThaiThanhToanClass = "partial";
        } else {
            trangThaiThanhToanLabel = "Đã thanh toán đủ";
            trangThaiThanhToanClass = "active";
        }

        String trangThaiHoaDonLabel;
        String trangThaiHoaDonClass;
        switch(hoaDon.getTrangThai())
        {
            case "Da thanh toan" -> {
                trangThaiHoaDonLabel = "Đã thanh toán";
                trangThaiHoaDonClass = "active";
            }
            case "Da xuat" -> {
                trangThaiHoaDonLabel = "Đã xuất";
                trangThaiHoaDonClass = "active";
            }
            default -> {
                trangThaiHoaDonLabel = "Chờ thanh toán";
                trangThaiHoaDonClass = "partial";
            }
        }


        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("phongList", phongList);
        model.addAttribute("dichVuList", dichVuList);
        model.addAttribute("thanhToans", thanhToanList);
        model.addAttribute("hoanTienList", hoanTienList);
        model.addAttribute("tongHoan", tongHoan);
        model.addAttribute("conLai", conLai);
        model.addAttribute("tongPhuThu", tongPhuThu);
        model.addAttribute("tongPhuThuCheckInSom", tongPhuThuCheckInSom);
        model.addAttribute("tongDichVuThuong", tongDichVuThuong);
        model.addAttribute("trangThaiThanhToanLabel", trangThaiThanhToanLabel);
        model.addAttribute("trangThaiThanhToanClass", trangThaiThanhToanClass);
        model.addAttribute("trangThaiHoaDonLabel", trangThaiHoaDonLabel);
        model.addAttribute("trangThaiHoaDonClass", trangThaiHoaDonClass);
        model.addAttribute("title", "Chi Tiết Hóa Đơn #" + id);

        return "admin/hoa-don-chi-tiet";
    }
}

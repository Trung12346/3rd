package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/thanh-toan")
public class ThanhToanController {

    @Autowired
    VnpayService vnpayService;

    @Autowired
    ChiTietDichVuService ctdvService;

    @Autowired
    HoaDonService hoaDonService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    ThanhToanService thanhToanService;

    @Autowired
    DatPhongService datPhongService;

        @GetMapping("/dat-phong/{id}")
        public String submitTransaction(@PathVariable Integer id,Model model){
            DatPhong dp = datPhongService.findById(id);
            if(dp.getTrangThai().equals("Da xac nhan")){
                return "redirect:/home";
            }
            BigDecimal Totalamount = BigDecimal.ZERO;
            BigDecimal amountDv = BigDecimal.ZERO;
            BigDecimal amountPhong = BigDecimal.ZERO;
            BigDecimal ThueVat = new BigDecimal("0.10");

            List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
            List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);

            for(ChiTietDatPhong ctdp : chiTietDatPhongs){
                Totalamount = Totalamount.add(ctdp.getGiaKhiDat());
                amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
            }

            for(Chi_tiet_dich_vu ctdv: chiTietDichVus){
                amountDv = amountDv.add(ctdv.getDonGia());
            }

            Totalamount = Totalamount.add(amountDv);
            BigDecimal tienVat = Totalamount.multiply(ThueVat).setScale(2,RoundingMode.HALF_UP);
            Totalamount = Totalamount.add(tienVat);
            model.addAttribute("datPhong",dp);
            model.addAttribute("TongTien",amountPhong);
            model.addAttribute("TienDv",amountDv);
            model.addAttribute("TienVat",tienVat);
            model.addAttribute("TongCong",Totalamount);
            return "Thanh-Toan";
        }

    @PostMapping("/vnpay/{id}")
    public String submitVnpay(@PathVariable Integer id, HttpServletRequest request) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal amountDv = BigDecimal.ZERO;

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amount = amount.add(ctdp.getGiaKhiDat());
        }

        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        BigDecimal VATCD = new BigDecimal("0.10");
        BigDecimal tongTien = amount.add(amountDv);
        BigDecimal tienVat = tongTien.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        tongTien = tongTien.add(tienVat);

        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String vnpayUrl = vnpayService.createOrder(tongTien.intValue(), id, "ChuyenKhoan", baseUrl);

        return "redirect:" + vnpayUrl;
    }


    @PostMapping("/dat-phong/{id}")
    public String submitTienMat(@PathVariable Integer id,
                                @RequestParam String phuongThucThanhToan,
                                RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong");
            return "redirect:/home";
        }

        BigDecimal amountPhong = BigDecimal.ZERO;
        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }

        BigDecimal amountDv = BigDecimal.ZERO;
        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        BigDecimal VATCD = new BigDecimal("0.10");
        BigDecimal amountTongTien = amountPhong.add(amountDv);
        BigDecimal tienVat = amountTongTien.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        amountTongTien = amountTongTien.add(tienVat);

        dp.setTrangThai("Cho xac nhan");
        datPhongService.save(dp);

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(dp);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(BigDecimal.ZERO);
        hd.setTienVat(tienVat);
        hd.setTongTien(amountTongTien);
        hd.setDaThanhToan(BigDecimal.ZERO);
        hd.setGhiChu("Thanh toan tien mat tai quay, ma don: " + id);
        hoaDonService.save(hd);





        ThanhToan tt = new ThanhToan();
        tt.setH(hd);
        tt.setPhuongThuc("Tien Mat");
        tt.setSoTien(amountTongTien);
        tt.setTrangThai("Cho thanh toan");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setGichu("Chua thu tien, khach se thanh toan khi nhan phong");
        thanhToanService.save(tt);

        redirectAttributes.addFlashAttribute("success", "Da xac nhan dat phong. Vui long thanh toan tien mat khi den nhan phong.");
        return "redirect:/thanh-toan/thanh-cong/" + id;
    }


    @GetMapping("/thanh-cong/{id}")
    public String thanhToanThanhCong(@PathVariable Integer id, Model model) {
        DatPhong dp = datPhongService.findById(id);

        BigDecimal Totalamount = BigDecimal.ZERO;
        BigDecimal amountDv = BigDecimal.ZERO;
        BigDecimal amountPhong = BigDecimal.ZERO;
        BigDecimal ThueVat = new BigDecimal("0.10");

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);

        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }
        Totalamount = amountPhong.add(amountDv);
        BigDecimal tienVat = Totalamount.multiply(ThueVat).setScale(2, RoundingMode.HALF_UP);
        Totalamount = Totalamount.add(tienVat);
        model.addAttribute("datPhong", dp);
        model.addAttribute("TongTien", amountPhong);
        model.addAttribute("TienVat",tienVat);
        model.addAttribute("TienDv", amountDv);
        model.addAttribute("TongCong", Totalamount);

        return "thanh-toan-thanh-cong";
    }


}

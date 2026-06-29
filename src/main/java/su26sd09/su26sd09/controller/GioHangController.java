package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.NguoiDungService;
import su26sd09.su26sd09.service.PhongService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gio-hang")
public class GioHangController {

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    PhongService PhongService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    NguoiDungService nguoiDungService;

    @GetMapping("")
    public String GetDanhSachPhong(){
        return "gio-hang";
    }

    @PostMapping("/checkout")
    public String checkoutCart(
            @RequestParam("roomIds") List<Integer> roomIds,
            @RequestParam("ngayNhan") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime ngayNhan,
            @RequestParam("ngayTra")  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime ngayTra,
            @RequestParam("nguoiLon") Integer nguoiLon,
            @RequestParam("treEm")    Integer treEm,
            @RequestParam(value = "ma_cccd",required = false) String ma_cccd,
            @RequestParam Map<String,String> allParamsCCCD,
            RedirectAttributes redirectAttributes,
            Authentication authentication
    ) {
        authentication = SecurityContextHolder.getContext().getAuthentication();
        String email;
        if (authentication !=null && authentication.isAuthenticated()) {
            email = authentication.getName();
        }else{
            email = null;
        }
        NguoiDung n = nguoiDungService.findByEmail(email);
        Map<Integer, String> cccdTheoPhong = allParamsCCCD.entrySet().stream().
                filter(e -> e.getKey().startsWith("cccdPhong_")).
                collect(Collectors.toMap(
                        e-> Integer.parseInt(e.getKey().substring("cccdPhong_".length())),
                        Map.Entry::getValue
                ));
        DatPhong datPhong = new DatPhong();
        datPhong.setN(n);
        if(ma_cccd !=null) {
            datPhong.setMa_cccd(ma_cccd);
        }else{
            datPhong.setMa_cccd(null);
        }
        datPhong.setNgaydatPhong(ngayNhan);
        datPhong.setNgaytraPhong(ngayTra);
        datPhong.setSonguoiLon(nguoiLon);
        datPhong.setSotreEm(treEm);
        datPhong.setYeuCauThem(null);
        datPhong.setTrangThai("Chua thanh toan");
        datPhong.setNgayTao(LocalDateTime.now());
        datPhong.setNgayCapNhat(null);
        datPhong.setSdt(null);
        datPhongService.save(datPhong);
        List<Phong> ListPhong = new ArrayList<>();
        MathContext mc = new MathContext(4, RoundingMode.HALF_UP);
        for(int p : roomIds){
           System.out.println("cac phong dat la: "+p);
           ListPhong.add(PhongService.findPhongById(p));
       }
        int resThue  = ngayTra.getDayOfYear() - ngayNhan.getDayOfYear();
        BigDecimal amount = BigDecimal.ZERO;
        for(Phong p : ListPhong){
           if (p == null){
               System.out.println("Null");
           }
            List<BigDecimal> price = new ArrayList<>();
            price.add(p.getGiaMoiDem());
            for (BigDecimal i : price){
                amount = amount.add(i);
            }
           System.out.println("Total "+amount);
       }
        amount = amount.multiply(BigDecimal.valueOf(resThue));
        System.out.println(amount);
        for(Phong p : ListPhong){
            BigDecimal amount2 = p.getGiaMoiDem();
            amount2 = amount2.multiply(BigDecimal.valueOf(resThue));
            ChiTietDatPhong chiTietDatPhong = new ChiTietDatPhong();
            chiTietDatPhong.setP(p);
            chiTietDatPhong.setGiaMoiDem(p.getGiaMoiDem());
            chiTietDatPhong.setMa_cccd(cccdTheoPhong.get(p.getMaPhong()));
            System.out.println("ma_cccd cua phong: "+p.getMaPhong() + "la: "+cccdTheoPhong.get(p.getMaPhong()));
            chiTietDatPhong.setGiaKhiDat(amount2);
            chiTietDatPhong.setD(datPhong);
            System.out.println("Cac phong: "+p.getSoPhong()+"Gia la: "+amount2);

            chiTietDatPhongService.save(chiTietDatPhong);
        }
        return "redirect:/phong/dat-phong/xac-nhan/"+datPhong.getId();
    }

}

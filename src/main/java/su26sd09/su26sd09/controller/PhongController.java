package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Controller
@RequestMapping("/phong")
public class PhongController {

    @Autowired
    private PhongService phongService;

    @Autowired
    private DanhGiaService danhGiaService;

    @Autowired
    private ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    private DatPhongService datphongservice;

    @Autowired
    private DichVuService dichVuService;

    @Autowired
    private ChiTietDichVuService ctdvService;

    @Autowired
    private NguoiDungService nguoiDungService;

    @Autowired
    private NhanVienService nhanVienService;

    @GetMapping
    public String index(Model model) {
        // Lấy tất cả phòng
        List<Phong> phongs = phongService.findAllPhong();
        
        // Lấy tiện nghi cho từng phòng
        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
        Map<Integer, String> tenLoaiTheoPhong = new HashMap<>();
        for (Phong phong : phongs) {
            tienNghiTheoPhong.put(phong.getMaPhong(), phongService.findTenTienNghiByPhong(phong.getMaPhong()));
            if (phong.getLoaiPhong() != null) {
                tenLoaiTheoPhong.put(phong.getMaPhong(), phong.getLoaiPhong().getTenLoai());
            }
        }
        
        // Lấy tất cả loại phòng cho dropdown menu
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        
        model.addAttribute("phongs", phongs);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("tenLoaiTheoPhong", tenLoaiTheoPhong);
        model.addAttribute("loaiPhongs", loaiPhongs);
        
        return "rooms";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable("id") int id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Phong phong = phongService.findPhongById(id);
        if (phong == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng");
            return "redirect:/phong";
        }

        LoaiPhong loaiPhong = phong.getLoaiPhong();
        List<String> tienNghi = phongService.findTenTienNghiByPhong(phong.getMaPhong());
        List<DanhGia> danhGias = danhGiaService.findDaDuyetByPhong(phong.getMaPhong());
        double diemTrungBinh = danhGias.stream()
                .mapToInt(DanhGia::getDiemDanhGia)
                .average()
                .orElse(0);
        
        // Lấy tất cả loại phòng cho dropdown menu và carousel
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            anhLoaiPhong.put(lp.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }

        model.addAttribute("phong", phong);
        model.addAttribute("loaiPhong", loaiPhong);
        model.addAttribute("tienNghi", tienNghi);
        model.addAttribute("danhGias", danhGias);
        model.addAttribute("tongDanhGia", danhGias.size());
        model.addAttribute("diemTrungBinh", diemTrungBinh);
        model.addAttribute("loaiPhongs", loaiPhongs);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        
        return "room-detail";
    }

    @GetMapping("/dat-phong/xac-nhan/{id}")
    public String ConfirmOrder(@PathVariable int id,Model model){
        DatPhong dp =  datphongservice.findById(id);
        if (dp.getTrangThai().equals("Da xac nhan")){
            return "redirect:/home";
        }
        BigDecimal resthue = BigDecimal.valueOf(dp.getNgaytraPhong().getDayOfYear() - dp.getNgaydatPhong().getDayOfYear());
        List<ChiTietDatPhong> listCt = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> listctdv = ctdvService.findByDatPhongId(id);
        BigDecimal amountDv = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal amountP = BigDecimal.ZERO;
        if (listctdv != null) {
            for (Chi_tiet_dich_vu dv : listctdv) {
                amountDv = amountDv.add(dv.getDonGia());

            }
        }
        for(ChiTietDatPhong ct : listCt){
            amountP = amountP.add(ct.getGiaKhiDat());
            System.out.println("Chi tiet phong dang dat: "+ct.getP().getSoPhong());
            amount = amount.add(ct.getGiaKhiDat());

            System.out.println("Amount: "+amount);

        }
        System.out.println("AmountDv: "+amountDv);
        amount = amount.add(amountDv);
        model.addAttribute("TienDv",amountDv);

        model.addAttribute("TongTien",amount);
        model.addAttribute("TienPhong",amountP);
        model.addAttribute("datPhong",dp);
        model.addAttribute("chiTietDatPhongList",listCt);
        model.addAttribute("nightCount",resthue);
        model.addAttribute("dichVuList",dichVuService.findAll());

        return "dat-phong-xac-nhan";
    }

    @PostMapping("/dat-phong/xac-nhan/{id}")
    public String ConfirmDV(@PathVariable int id,
                            @RequestParam(value = "dichVuIds", required = false) List<Integer> dichvuid,
                            @RequestParam Map<String, String> allParams) {

        DatPhong dp = datphongservice.findById(id);

        if (dichvuid != null) {
            for (Integer maDichVu : dichvuid) {
                Dich_vu dv = dichVuService.findById(maDichVu);

                String slStr = allParams.get("soLuong_" + maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;

                String ngayStr = allParams.get("ngaySuDung_" + maDichVu);
                LocalDateTime ngaySuDung = (ngayStr != null && !ngayStr.isBlank())
                        ? LocalDateTime.parse(ngayStr)
                        : LocalDateTime.now();

                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setSoluong(sl);
                ct.setDatPhong(dp);
                ct.setDv(dv);
                ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(sl)));
                ct.setNgay_su_dung(ngaySuDung);
                ctdvService.save(ct);
                System.out.println("Gia dich vu: "+dv.getGia().multiply(BigDecimal.valueOf(sl)));

            }
        }

        if (dp.getN() != null && dp.getN().getVaiTro().getTenVaiTro().equals("ROLE_EMPLOYEE")){
            return "redirect:/thanh-toan/dat-phong/"+dp.getId();
        }else{
            return "redirect:/phong/dat-phong/thong-tin-khach/"+dp.getId();
        }
    }

    @GetMapping("/dat-phong/thong-tin-khach/{id}")
    public String ConfirmCustomerInfor(@PathVariable int id, Model model){

        DatPhong dp = datphongservice.findById(id);
        if (dp.getTrangThai().equals("Da xac nhan")){
            return "redirect:/home";
        }
        List<ChiTietDatPhong> listCt = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> listctdv = ctdvService.findByDatPhongId(id);
        BigDecimal amountDv = BigDecimal.ZERO;
       model.addAttribute("datPhong",dp);
        System.out.println("Debug dat phong Ngay nhan phong: "+dp.getNgaydatPhong());
       model.addAttribute("nightCount",5);
       model.addAttribute("chiTietDatPhongList",listCt);
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal ThueVat = new BigDecimal("0.10");


        BigDecimal resThue = BigDecimal.valueOf(dp.getNgaytraPhong().getDayOfYear() - dp.getNgaydatPhong().getDayOfYear());
        for(Chi_tiet_dich_vu dv : listctdv){
            amountDv = amountDv.add(dv.getDonGia());
        }
        model.addAttribute("TienDv",amountDv);
        for (ChiTietDatPhong chiTietDatPhong : listCt){
            amount = amount.add(chiTietDatPhong.getGiaKhiDat());
            System.out.println("So tien: "+chiTietDatPhong.getGiaKhiDat() + "Amount: "+amount );

            System.out.println("In for each loops: "+chiTietDatPhong.getGiaMoiDem());

        }
        BigDecimal TotalAmount = amount.add(amountDv);
        BigDecimal TienVat = TotalAmount.multiply(ThueVat).setScale(2, RoundingMode.HALF_UP);
        TotalAmount = TotalAmount.add(TienVat);
        System.out.println("Amount: "+ amount);
        model.addAttribute("TienVat",TienVat);
        model.addAttribute("TienPhong",amount);
        model.addAttribute("TongTien",TotalAmount);
        return "dat-phong-thong-tin-khach";
    }

    @PostMapping("/dat-phong/thong-tin-khach/{id}")
    public String SaveXacThucThongTin(Model model,
                                      @PathVariable int id,
                                      @RequestParam("hoTen") String hoten,
                                      @RequestParam("email") String email,
                                      @RequestParam("sdt")String sodienthoai,

                                      @RequestParam("yeuCauThem") String yeucauthem,
                                      Authentication authentication) {
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal amountdv = BigDecimal.ZERO;
            DatPhong dp = datphongservice.findById(id);
            if(dp ==null) {
                return "dat-phong-thong-tin-khach";
            }
            List<Nhanvien> listNv = nhanVienService.findAll();
            Stream<Nhanvien> ListnvLeTan = listNv.stream().filter(nv -> nv.getBoPhan().equalsIgnoreCase("lễ tân"));
            
            List<ChiTietDatPhong> listCtdp = chiTietDatPhongService.findByDatPhongId(id);
            List<Chi_tiet_dich_vu> listCtdv = ctdvService.findByDatPhongId(id);
            for(ChiTietDatPhong ctdp : listCtdp){
                amount = amount.add(ctdp.getGiaKhiDat());
            }
            for(Chi_tiet_dich_vu ctdv : listCtdv){
                amountdv = amountdv.add(ctdv.getDonGia());
            }
            amount = amount.add(amountdv);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailSearch = null;
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            emailSearch = auth.getName();
        } else {
            for (Nhanvien nv : ListnvLeTan.toList()) {
                emailSearch = nv.getN().getEmail();
            }
        }

        NguoiDung n = nguoiDungService.findByEmail(emailSearch);

        boolean isNvDp = n.getVaiTro() != null && "ROLE_STAFF".equals(n.getVaiTro().toString());

        if (isNvDp) {
            Nhanvien nv = nhanVienService.findByMaNguoiDung(n.getMaNguoiDung());
            dp.setNv(nv != null ? nv : nhanVienService.findByMaNguoiDung(nguoiDungService.findByEmail(emailSearch).getMaNguoiDung()));
        } else {
            dp.setNv(nhanVienService.findByMaNguoiDung(nguoiDungService.findByEmail(emailSearch).getMaNguoiDung()));
        }
        System.out.println("Amount Xac nhan thong tin khach hang: "+amount);
        System.out.println("Amount dich vu xac nhan thong tin khach hang: "+amountdv);
            dp.setHoten(hoten);
            dp.setEmail(email);
            dp.setSdt(sodienthoai);
            dp.setYeuCauThem(yeucauthem);


            datphongservice.save(dp);

            return "redirect:/thanh-toan/dat-phong/"+dp.getId();
    }
    @PostMapping("/dat-phong/quick")
    public String quickBooking(@RequestParam Integer maLoaiPhong,
                               @RequestParam Integer maPhong,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime ngayNhan,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime ngayTra,
                               @RequestParam Integer nguoiLon,
                               @RequestParam Integer treEm,

                               @RequestParam(required = false) String yeuCauThem,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        System.out.println("vao Controller");
        Phong phong = phongService.findById(maPhong);
        if (phong == null || !"Trong".equals(phong.getTrangThai())) {
            redirectAttributes.addFlashAttribute("bookingError", "Phòng không khả dụng, vui lòng chọn phòng khác.");
            return "redirect:/loai-phong/" + maLoaiPhong;
        }

        DatPhong dp = new DatPhong();
        dp.setNgaydatPhong(ngayNhan);
        dp.setNgaytraPhong(ngayTra);
        dp.setSonguoiLon(nguoiLon);
        dp.setSotreEm(treEm);

        dp.setYeuCauThem(yeuCauThem);
        dp.setNgayTao(LocalDateTime.now());
        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (isLoggedIn) {

            NguoiDung nd = nguoiDungService.findByEmail(authentication.getName());
            if(nd !=null) {
                dp.setHoten(nd.getHoTen());
                dp.setEmail(nd.getEmail());
                dp.setSdt(nd.getSoDienThoai());

                dp.setN(nd);
            }
        }

        dp.setTrangThai("Chua thanh toan");
        DatPhong savedDp = datphongservice.save(dp);

        ChiTietDatPhong ctdp = new ChiTietDatPhong();
        ctdp.setD(savedDp);
        ctdp.setP(phong);
        ctdp.setGiaMoiDem(phong.getGiaMoiDem());
        ctdp.setGiaKhiDat(phong.getGiaMoiDem().multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(ngayNhan,ngayTra))));

        chiTietDatPhongService.save(ctdp);

        phong.setTrangThai("Trong");
        phongService.save1(phong);

        return "redirect:/phong/dat-phong/xac-nhan/" + savedDp.getId();
    }


}

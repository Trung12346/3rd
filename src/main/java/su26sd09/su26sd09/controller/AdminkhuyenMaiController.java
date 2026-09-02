package su26sd09.su26sd09.controller;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.service.NhanVienService;
import su26sd09.su26sd09.service.UserService;
import su26sd09.su26sd09.service.khuyenMaiService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/nhan-su/admin/khuyen-mai")
public class AdminkhuyenMaiController {

    @Autowired
    UserService nguoiDungRepo;
    @Autowired
    NhanVienService nvRepo;
    @Autowired
    khuyenMaiService repo;


    @GetMapping
    public String index(
            @RequestParam(required = false) String promoCode,
            @RequestParam(required = false) String moTa,
            @RequestParam(required = false) String loaiGiam,
            @RequestParam(required = false) BigDecimal giatriGiam,
            @RequestParam(required = false) LocalDate ngayBatDau,
            @RequestParam(required = false) LocalDate ngayKetThuc,
            @RequestParam(required = false) Boolean hoatDong,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        promoCode = StringUtils.hasText(promoCode) ? promoCode : null;
        moTa      = StringUtils.hasText(moTa) ? moTa : null;
        loaiGiam  = StringUtils.hasText(loaiGiam) ? loaiGiam : null;
        Page<KhuyenMai> khuyenMais = repo.search(
                promoCode,
                moTa,
                loaiGiam,
                giatriGiam,
                ngayBatDau,
                ngayKetThuc,
                hoatDong,
                page,
                size);
        System.out.println("promoCode = " + promoCode);
        System.out.println("page=" + khuyenMais.getNumber()
                + " totalPages=" + khuyenMais.getTotalPages()
                + " totalElements=" + khuyenMais.getTotalElements());

        model.addAttribute("khuyenMais", khuyenMais);
        model.addAttribute("khuyenMai", new KhuyenMai());

        model.addAttribute("promoCode", promoCode);
        model.addAttribute("moTa", moTa);
        model.addAttribute("loaiGiam", loaiGiam);
        model.addAttribute("giatriGiam", giatriGiam);
        model.addAttribute("ngayBatDau", ngayBatDau);
        model.addAttribute("ngayKetThuc", ngayKetThuc);
        model.addAttribute("hoatDong", hoatDong);

        return "admin/khuyen-mai-list";
    }



    @PostMapping("/lock-or-unlock/{id}")
    public String lockOrUnlock(
            @PathVariable("id") int id,
            RedirectAttributes redirect) {

        KhuyenMai km = repo.findbyId(id);

        if (km == null) {
            redirect.addFlashAttribute("error", "Không tìm thấy khuyến mãi");
            return "redirect:/nhan-su/admin/khuyen-mai";
        }

        LocalDate today = LocalDate.now();

        // Đang KHÓA -> muốn MỞ KHÓA
        if (!km.hoatDong) {

            // Chỉ được mở khi:
            // today >= ngày bắt đầu
            // và today <= ngày kết thúc
            boolean thoiGianHopLe =
                    !today.isBefore(km.ngayBatDau)
                            && !today.isAfter(km.ngayKetThuc);

            if (!thoiGianHopLe) {
                redirect.addFlashAttribute(
                        "error",
                        "Không thể mở khóa khuyến mãi vì thời gian hiện tại không hợp lệ"
                );

                return "redirect:/nhan-su/admin/khuyen-mai";
            }
        }

        // Nếu đang MỞ -> KHÓA
        // luôn cho phép khóa, không cần kiểm tra thời gian

        String logError = repo.ValidUpdateKhuyenMai(km);

        if (logError != null
                && !logError.trim().isEmpty()
                && !"null".equalsIgnoreCase(logError)) {

            redirect.addFlashAttribute("error", logError);
            return "redirect:/nhan-su/admin/khuyen-mai";
        }

        km.setHoatDong(!km.hoatDong);
        repo.save(km);

        redirect.addFlashAttribute(
                "success",
                km.hoatDong
                        ? "Mở khóa khuyến mãi thành công"
                        : "Khóa khuyến mãi thành công"
        );

        return "redirect:/nhan-su/admin/khuyen-mai";
    }


    @PostMapping("/save")
    public String saveKhuyenMai(RedirectAttributes redirect, Model model, Principal p, @Valid @ModelAttribute("khuyenMai") KhuyenMai m, BindingResult r){
        if(r.hasErrors() ){
            redirect.addFlashAttribute("error",r.getFieldError().getDefaultMessage());
            return "redirect:/nhan-su/admin/khuyen-mai";
        }

        else if(m.ngayKetThuc.isBefore(m.ngayBatDau) || m.ngayKetThuc.equals(m.ngayBatDau)){
            redirect.addFlashAttribute("error","ngày kết thúc không phải sau ngày bắt đầu ít nhất 1 ngày");
            return "redirect:/nhan-su/admin/khuyen-mai";
        }
        if(m.giatriGiam.compareTo(BigDecimal.ZERO) <= 0){
            redirect.addFlashAttribute("error","giá trị giảm phải lớn hơn 0");
            return "redirect:/nhan-su/admin/khuyen-mai";
        }
        if ((m.ngayBatDau.isBefore(LocalDate.now()) || m.ngayBatDau.equals(LocalDate.now())) && m.ngayKetThuc.isAfter(LocalDate.now())){
            m.setHoatDong(true);
        }else{
            m.setHoatDong(false);
        }
        if (m.id == 0){
            for (NhanSu ng : nvRepo.findAlladmin()){
                if (ng.getEmail().equalsIgnoreCase(p.getName())){
                    m.setNhanSu(ng);
                    System.out.println(m.getNhanSu().getEmail());
                }
            }
        }
        if (m.giatriGiam.compareTo(BigDecimal.valueOf(99.0)) > 0 && m.loaiGiam.equalsIgnoreCase("PERCENT")){
            redirect.addFlashAttribute("error","voucher giảm theo phần trăm tối đa là 99%");
            return"redirect:/nhan-su/admin/khuyen-mai";
        }
        if (m.giatriGiam.floatValue() > m.giaToiThieuDuocGiam.floatValue() * 99/100 && m.loaiGiam.equalsIgnoreCase("AMOUNT")){
            redirect.addFlashAttribute("error","voucher giảm theo giá cụ thể không hợp lệ: giá tối thiểu phải lớn hơn giá trị giảm");
            return"redirect:/nhan-su/admin/khuyen-mai";
        }
        if (m.hoatDong != false && ( m.ngayBatDau.isAfter(LocalDate.now()) || m.ngayKetThuc.equals(LocalDate.now()) || LocalDate.now().isAfter(m.ngayKetThuc))){
            redirect.addFlashAttribute("error","trạng thái không hợp lệ với mốc ngày đã chỉ định");
            return "redirect:/nhan-su/admin/khuyen-mai";
        }if (m.hoatDong != true && ( m.ngayBatDau.isBefore(LocalDate.now()) && LocalDate.now().isBefore(m.ngayKetThuc))){
            redirect.addFlashAttribute("error","trạng thái không hợp lệ với mốc ngày đã chỉ định");
            return "redirect:/nhan-su/admin/khuyen-mai";
        }
        String TimKhuyenMaiDaSuDung = repo.ValidUpdateKhuyenMai(m);
        if (!TimKhuyenMaiDaSuDung.equals("null") && !TimKhuyenMaiDaSuDung.equals("")){
            redirect.addFlashAttribute("error",TimKhuyenMaiDaSuDung);
            return "redirect:/nhan-su/admin/khuyen-mai";
        }


        if(m.id == 0){
            redirect.addFlashAttribute("success","Luu khuyen mai thanh cong");

        }else{
            redirect.addFlashAttribute("success","Cap nhat khuyen mai thanh cong");
        }
        repo.save(m);


        return "redirect:/nhan-su/admin/khuyen-mai";
    }


    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Page<KhuyenMai> ds = repo.search(
                null, null, null, null,
                null, null, null,
                page, size);

        model.addAttribute("khuyenMais", ds);
        model.addAttribute("khuyenMai", repo.findbyId(id));

        return "admin/khuyen-mai-list";
    }


    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id,RedirectAttributes redirect){
        if (repo.doesExitsInDatPhong(id) == false) {
            repo.delete(repo.findbyId(id));
        } else {
            redirect.addFlashAttribute("error", "xóa không thành công: khuyến mãi đã được sử dụng");
        }
        return "redirect:/nhan-su/admin/khuyen-mai";
    }

}

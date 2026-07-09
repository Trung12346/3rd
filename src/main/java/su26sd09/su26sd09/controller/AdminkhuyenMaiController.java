package su26sd09.su26sd09.controller;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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


    @PostMapping("/delete/{id}")
    public  String deleteKhuyenMai(@PathVariable("id") int id, Principal p){
        repo.delete(repo.findbyId(id));


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
            redirect.addFlashAttribute("error","voucher giảm theo giá cụ thể không được bằng giá tối thiểu có thể giảm");
            return"redirect:/nhan-su/admin/khuyen-mai";
        }
        if (m.hoatDong != false && (!m.ngayBatDau.equals(LocalDate.now()) || !m.ngayKetThuc.equals(LocalDate.now()))){
            redirect.addFlashAttribute("error","trạng thái không hợp lệ với mốc ngày đã chỉ định");
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


}

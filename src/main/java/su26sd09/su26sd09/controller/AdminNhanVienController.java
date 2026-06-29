package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.Nhanvien;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.service.NhanVienService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/nhan-vien")
public class AdminNhanVienController {

    @Autowired
    NhanVienService repo;
    @Autowired
    UserService NguoiDungRepo;
    @Autowired
    VaiTroRepo vaiTroRepo;
    @Autowired
    NhanVienRepo nvrepo;



    public Boolean CheckRole(String email){
        String role = "";

        for (NguoiDung n : NguoiDungRepo.getAll()){
            if(n.getEmail().equals(email)) {
                if (n.getVaiTro().getTenVaiTro().equals("ROLE_ADMIN")) {
                    role = n.getVaiTro().getTenVaiTro();
                }
            }
        }

        if(role == null || role.isEmpty() || !role.equals("ROLE_ADMIN")){
            return false;
        }
        if(role.equals("ROLE_ADMIN")){
            return true;
        }
        return false;
    }


    @GetMapping
    public String index(Model model){
        Nhanvien nv = new Nhanvien();
        nv.setN(new NguoiDung());
        model.addAttribute("nhanViens",repo.findAll());
        model.addAttribute("nhanVien",nv);
        model.addAttribute("vaiTros",vaiTroRepo.findAll());

        model.addAttribute("nguoiDungs",repo.ListAdd());
        return "admin/nhan-vien-list";
    }


    @PostMapping("/lock/{id}")
    public String deleteNhanVien(Principal P, @PathVariable("id") int id,RedirectAttributes redirect){


        repo.lock(repo.findbyid(id));
        redirect.addFlashAttribute("success","khóa nhân viên thành công");


        return "redirect:/admin/nhan-vien";

    }


    @PostMapping("/save")
    public String saveNhanVien(@Valid Nhanvien nv, BindingResult r,  Principal p, RedirectAttributes redirect,@RequestParam(value = "matKhaumoi",required = false) String matKhaumoi,
                               @RequestParam(value = "maNguoiDung",required = false) Integer maNguoiDung){
        if(CheckRole(p.getName())){
            PasswordEncoder encoder = new BCryptPasswordEncoder();

            if (maNguoiDung != null && (nv.getId() == 0 && repo.IsNhanVienTonTai(maNguoiDung) == false)){
                if ((nv.getBoPhan() != null && !nv.getBoPhan().isBlank()) && maNguoiDung != null ){

                    nv.setN(NguoiDungRepo.Getbyid(maNguoiDung));
                    repo.save(nv);
                    redirect.addFlashAttribute("success","thêm nhân viên thành công");
                    return "redirect:/admin/nhan-vien";
                }

            }
            else if((nv.getBoPhan() != null && !nv.getBoPhan().isBlank()) &&  maNguoiDung == null && nv.id == 0){


                if (r.hasErrors()){
                    redirect.addFlashAttribute("error",r.getFieldError().getDefaultMessage());
                    return "redirect:/admin/nhan-vien";
                }

                if(NguoiDungRepo.checkEmail(nv.n.getEmail(), nv.n.getMaNguoiDung()) ){
                    redirect.addFlashAttribute("error","email đã tồn tại");
                    return "redirect:/admin/nhan-vien";
                }

                if ( nv.n.getVaiTro().getTenVaiTro().equals("ROLE_ADMIN") ||nv.n.getVaiTro().getTenVaiTro().equals("ROLE_GUEST")){
                    redirect.addFlashAttribute("error","tài khoản  khác vai trò STAFF(nhân viên) không thể làm nhân viên ");
                    return "redirect:/admin/nhan-vien";
                }

                NguoiDungRepo.save(nv.n);


                repo.save(nv);
                redirect.addFlashAttribute("success","thêm nhân viên thành công");
            }
            else if(nv.boPhan == null || nv.boPhan.isBlank()){
                redirect.addFlashAttribute("error","bộ phận không được để trống");
            }
            if (maNguoiDung != null && nv.id != 0){

                if (repo.TrungNv(maNguoiDung,nv.id) == true){
                    redirect.addFlashAttribute("error","vui lòng chọn mã nhân viên không trùng với nhân viên khác");
                    return "redirect:/admin/nhan-vien";
                }

                if (!r.hasErrors() ){
                    NguoiDung nguoidung = NguoiDungRepo.Getbyid(maNguoiDung);

                    String oldEmail = nguoidung.getEmail();

                    nguoidung.setNgayCapNhat(LocalDateTime.now());
                    nguoidung.setDiaChi(nv.n.getDiaChi());
                    nguoidung.setMatKhau_hash(nv.n.getMatKhau_hash());

                    if (!matKhaumoi.isEmpty())nguoidung.setMatKhau_hash(encoder.encode(matKhaumoi));

                    nguoidung.setEmail(nv.n.getEmail());
                    nguoidung.setVaiTro(nv.n.getVaiTro());
                    nguoidung.setTrangThai(nv.n.isTrangThai());
                    nguoidung.setHoTen(nv.n.getHoTen());
                    nguoidung.setSoDienThoai(nv.n.getSoDienThoai());


                    nv.setN(nguoidung);



                    if( (NguoiDungRepo.checkEmail(nv.n.getEmail(), nv.n.getMaNguoiDung()) && !nv.n.getEmail().equals(oldEmail)
                    )  ){
                        System.out.println("TRUNG UNIQUE");
                        redirect.addFlashAttribute("error","email đã tồn tại");
                        return "redirect:/admin/nhan-vien";
                    }
                    if ( nv.n.getVaiTro().getTenVaiTro().equals("ROLE_ADMIN") ||nv.n.getVaiTro().getTenVaiTro().equals("ROLE_GUEST")){
                        redirect.addFlashAttribute("error","tài khoản khác vai trò STAFF(nhân viên) không thể làm nhân viên");
                        return "redirect:/admin/nhan-vien";
                    }
                    NguoiDungRepo.save(nv.n);

                    repo.save(nv);

                    redirect.addFlashAttribute("success","cập nhật nhân viên thành công");
                    return "redirect:/admin/nhan-vien";

                }else if(r.hasErrors() ){
                    redirect.addFlashAttribute("error",r.getFieldError().getDefaultMessage());
                }else if(maNguoiDung == null){
                    redirect.addFlashAttribute("error","vui lòng chọn nhân viên");
                }
            }


        }
        return "redirect:/admin/nhan-vien";
    }


    @GetMapping("/edit/{id}")
    public String editNhanVien(Model model,Principal p,@PathVariable("id") int id){
        if(CheckRole(p.getName())){
            model.addAttribute("nhanViens",repo.findAll());
            model.addAttribute("nhanVien",repo.findbyid(id));
            model.addAttribute("nguoiDungs",repo.ListAdd());
            model.addAttribute("vaiTros",vaiTroRepo.findAll());

            return "admin/nhan-vien-list";
        }
        return "redirect:/home";
    }


    @GetMapping("/search")
    public String searchNhanVien(Model model, Principal p ,@RequestParam("keyword") String name , RedirectAttributes redirect){
        if (CheckRole(p.getName())){
            Nhanvien nv = new Nhanvien();
            nv.setN(new NguoiDung());
            model.addAttribute("nhanViens",repo.findByName(name));
            model.addAttribute("nhanVien",nv);
            model.addAttribute("nguoiDungs",repo.ListAdd());
            model.addAttribute("vaiTros",vaiTroRepo.findAll());
            redirect.addFlashAttribute("success","tổng số tìm được = " + repo.findByName(name).size());
            return "admin/nhan-vien-list";
        }
        return "redirect:/admin/nhan-vien";
    }
}

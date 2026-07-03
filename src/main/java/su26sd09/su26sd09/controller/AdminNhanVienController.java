package su26sd09.su26sd09.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.entity.VaiTro;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.service.NhanVienService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

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






    @GetMapping
    public String index(
            @RequestParam(required = false) String hoTen,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sdt,
            @RequestParam(required = false) String maCCCD,
            @RequestParam(required = false) String boPhan,
            @RequestParam(required = false) Boolean trangThai,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {



        Page<NhanSu> nhanViensPage = repo.search(
                hoTen, email, sdt, maCCCD, boPhan, trangThai, page, size);

        model.addAttribute("nhanViens", nhanViensPage.getContent());
        model.addAttribute("nhanViensPage", nhanViensPage);
        model.addAttribute("nhanVien", new NhanSu());
        model.addAttribute("vaiTros", vaiTroRepo.findAll());

        model.addAttribute("hoTen", hoTen);
        model.addAttribute("email", email);
        model.addAttribute("sdt", sdt);
        model.addAttribute("maCCCD", maCCCD);
        model.addAttribute("boPhan", boPhan);
        model.addAttribute("trangThai", trangThai);

        return "admin/nhan-vien-list";
    }


    @PostMapping("/lock/{id}")
    public String deleteNhanVien(Principal P, @PathVariable("id") int id,RedirectAttributes redirect){


        repo.lock(repo.findbyid(id));
        redirect.addFlashAttribute("success","khóa nhân viên thành công");


        return "redirect:/admin/nhan-vien";

    }


    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/save")
    public String saveNhanVien(@Valid NhanSu nv, BindingResult bindingResult,
                               Principal principal, RedirectAttributes redirect,
                               @RequestParam(value = "matKhaumoi", required = false) String matKhauMoi
                               ) {




        VaiTro roleStaff = vaiTroRepo.findbyname("ROLE_STAFF");
        System.out.println("hello"+roleStaff.getTenVaiTro());

        for (FieldError fe : bindingResult.getFieldErrors()) {
            if (fe.getField().equals("matKhau_hash") && matKhauMoi != null && !matKhauMoi.isBlank() || (matKhauMoi != null && !matKhauMoi.isBlank())) {
                nv.setMat_khau_hash(passwordEncoder.encode(matKhauMoi));
            } else if (fe.getField().equals("vaitro") && roleStaff != null) {
                nv.setVaitro(roleStaff);
            } else {
                redirect.addFlashAttribute("error", fe.getDefaultMessage());
                return "redirect:/admin/nhan-vien";
            }
        }

       String result = LuuNhanVien(roleStaff,nv,redirect);
          if (result != null){
              return result;
          }

        redirect.addFlashAttribute("success", "cập nhật dữ liệu thành công");
        return "redirect:/admin/nhan-vien";
    }

    private String LuuNhanVien(VaiTro roleStaff,  NhanSu nv, RedirectAttributes redirect) {

        if (Period.between(nv.getNgaySinh(), LocalDate.now()).getYears() < 18 || Period.between(nv.getNgaySinh(),LocalDate.now()).getYears() > 70){
            redirect.addFlashAttribute("error","vui lòng xem lại trường dữ liệu nhân viên(nhân viên phải từ 18 tuổi trở lên)");
            return "redirect:/admin/nhan-vien";
        }
        if (nv.getId() == null){

            if (repo.checkTrungCccd(nv.maCCCD,0)){
                redirect.addFlashAttribute("error","mã căn cước công dân này đã tồn tại ở nhân viên khác");
                return "redirect:/admin/nhan-vien";
            }
            if (repo.checkSodienThoai(nv.getSdt(),0)){
                redirect.addFlashAttribute("error","số điện thoại này đã tồn tại ở nhân viên khác");
                return "redirect:/admin/nhan-vien";
            }
        }else{
            if (repo.checkTrungCccd(nv.maCCCD,nv.id)){
                redirect.addFlashAttribute("error","mã căn cước công dân này đã tồn tại ở nhân viên khác");
                return "redirect:/admin/nhan-vien";
            }
            if (repo.checkSodienThoai(nv.getSdt(),nv.id)){
                redirect.addFlashAttribute("error","số điện thoại này đã tồn tại ở nhân viên khác");
                return "redirect:/admin/nhan-vien";
            }
        }

        redirect.addFlashAttribute("success", "cập nhật dữ liệu thành công");
        repo.save(nv);
        return "redirect:/admin/nhan-vien";
    }




    @GetMapping("/edit/{id}")
    public String editNhanVien( @RequestParam(required = false) String hoTen,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String sdt,
                                @RequestParam(required = false) String maCCCD,
                                @RequestParam(required = false) String boPhan,
                                @RequestParam(required = false) Boolean trangThai,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Model model,Principal p,@PathVariable("id") int id){

        model.addAttribute("nhanViens",
                repo.search(null,null,null,null,null,null,page,size));

            model.addAttribute("nhanVien",repo.findbyid(id));

            model.addAttribute("vaiTros",vaiTroRepo.findAll());

            model.addAttribute("hoTen",hoTen);
            model.addAttribute("email",email);
            model.addAttribute("sdt",sdt);
            model.addAttribute("maCCCD",maCCCD);
            model.addAttribute("boPhan",boPhan);
            model.addAttribute("trangThai",trangThai);


            return "admin/nhan-vien-list";
        }





}

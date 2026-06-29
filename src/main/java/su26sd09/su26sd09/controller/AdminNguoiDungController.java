package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/nguoi-dung")
public class AdminNguoiDungController {

    @Autowired
    private UserService userService;
    @Autowired
    private VaiTroRepo repo;
    @Autowired
    private DatPhongService datPhongrepo;

    public Boolean CheckRole(String email){
        String role = "";

        for (NguoiDung n : userService.getAll()){
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
    public String index(
            Principal p, @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model
    ) {
        if (CheckRole(p.getName())) {

            NguoiDung nguoiDung = new NguoiDung();
            model.addAttribute("nguoiDung",nguoiDung);
            model.addAttribute("nguoiDungs",userService.getAll());
            model.addAttribute("vaiTros",repo.findAll());
    }
        return "admin/nguoi-dung-list";
    }
    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable("id") int id,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model,
            RedirectAttributes redirectAttributes, Principal p
    ) {
       if(CheckRole(p.getName())){
           NguoiDung nguoiDung = userService.Getbyid(id);
           model.addAttribute("nguoiDung",nguoiDung);
           model.addAttribute("nguoiDungs",userService.getAll());
           model.addAttribute("vaiTros",repo.findAll());
               if (nguoiDung == null) {
                   redirectAttributes.addFlashAttribute("error", "Khong tim thay nguoi dung");
                   return "redirect:/admin/nguoi-dung";
               }


       }
        return "admin/nguoi-dung-list";
    }

    @PostMapping("/save")
    public String save(
            @Valid NguoiDung nguoiDung, BindingResult r,
            RedirectAttributes redirect
    , Principal p,@RequestParam("matKhaumoi") String matKhaumoi) {
         PasswordEncoder e = new BCryptPasswordEncoder();
        if (CheckRole(p.getName())){

            if (nguoiDung.getMaNguoiDung() == null){
                for (NguoiDung s : userService.getAll()){


                        if (!s.getEmail().equals(nguoiDung.getEmail() ) && userService.checkEmail(nguoiDung.getEmail(),nguoiDung.getMaNguoiDung())){
                            redirect.addFlashAttribute("error"," email này đã tồn tại");
                            return "redirect:/admin/nguoi-dung";
                        
                    }

                }
            }
            if (!matKhaumoi.isEmpty()){
                nguoiDung.setMatKhau_hash(e.encode(matKhaumoi));
            }
            if(nguoiDung.getMatKhau_hash() == null || nguoiDung.getMatKhau_hash().isBlank()){
                redirect.addFlashAttribute("error","mật khẩu không được để trống");
                return "redirect:/admin/nguoi-dung";
            }
            if(r.hasErrors()){
                redirect.addFlashAttribute("error",r.getFieldError().getDefaultMessage());
                return "redirect:/admin/nguoi-dung";
            }


            if ( nguoiDung.getMaNguoiDung() != null){


                nguoiDung.setNgayCapNhat(LocalDateTime.now());
                for (NguoiDung s : userService.getAll()){
                          if (s.getMaNguoiDung().equals(nguoiDung.getMaNguoiDung())){

                              if (s.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF") && !nguoiDung.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF")){
                                      if (datPhongrepo.FindbyNguoiDung(s.getMaNguoiDung()) != null){
                                          redirect.addFlashAttribute("error","không thể cập nhật: người dùng có vai trò nhân viên có đơn đặt phòng khả dụng");
                                          return "redirect:/admin/nguoi-dung";
                                      }

                              }
                              if ( (!s.getEmail().equals(nguoiDung.getEmail() )|| userService.checkEmail(nguoiDung.getEmail(),nguoiDung.getMaNguoiDung()))){
                                 redirect.addFlashAttribute("error"," email này đã tồn tại");
                                 return "redirect:/admin/nguoi-dung";
                              }
                          }
                }
                userService.save(nguoiDung);
                redirect.addFlashAttribute("success", "Cap nhat nguoi dung thanh cong");
            }else{
                userService.save(nguoiDung);
                redirect.addFlashAttribute("success", "luu nguoi dung thanh cong");
            }
        }

        return "redirect:/admin/nguoi-dung";
    }

    @PostMapping("/lock/{id}")
    public String delete(Principal p,@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        if (CheckRole(p.getName())){
            userService.setTrangThai(id, false);
            redirectAttributes.addFlashAttribute("success", "Khoa nguoi dung thanh cong");
        }
        return "redirect:/admin/nguoi-dung";
    }

    @GetMapping("/search")
    public String search(RedirectAttributes r,@RequestParam("keyword") String keyword,Principal p,Model model){
        if (CheckRole(p.getName())){
        if(userService.TimKiemTheoTen(keyword).size() >= 0){
            model.addAttribute("nguoiDung",new NguoiDung());
            model.addAttribute("nguoiDungs",userService.search(keyword));
            model.addAttribute("vaiTros",repo.findAll());
            r.addFlashAttribute("success","tìm thành công");
        }else{
            model.addAttribute("nguoiDung",new NguoiDung());
            model.addAttribute("nguoiDungs",userService.getAll());
            model.addAttribute("vaiTros",repo.findAll());
            r.addFlashAttribute("error","không tìm thấy tên trên yêu cầu");
        }
        }
        return "admin/nguoi-dung-list";
    }
}

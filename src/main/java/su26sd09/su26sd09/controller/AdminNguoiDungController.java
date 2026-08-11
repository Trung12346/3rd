package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/nhan-su/admin/nguoi-dung")
public class AdminNguoiDungController {

    @Autowired
    private UserService userService;
    @Autowired
    private VaiTroRepo repo;
    @Autowired
    private DatPhongService datPhongrepo;



    @GetMapping
    public String index(
            Principal p, @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model
    ) {


            KhachHang nguoiDung = new KhachHang();
            model.addAttribute("nguoiDung",nguoiDung);
            model.addAttribute("nguoiDungs",userService.getAll());
            model.addAttribute("vaiTros",repo.findAll());

        return "admin/nguoi-dung-list";
    }
    @GetMapping("/edit/{id}")
    public String edit(
            @PathVariable("id") int id,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Model model,
            RedirectAttributes redirectAttributes, Principal p
    ) {
           KhachHang nguoiDung = userService.Getbyid(id);
           model.addAttribute("nguoiDung",nguoiDung);
           model.addAttribute("nguoiDungs",userService.getAll());
           model.addAttribute("vaiTros",repo.findAll());
               if (nguoiDung == null) {
                   redirectAttributes.addFlashAttribute("error", "Khong tim thay nguoi dung");
                   return "redirect:/nhan-su/admin/nguoi-dung";
               }



        return "admin/nguoi-dung-list";
    }

    @PostMapping("/save")
    public String save(
            @Valid KhachHang nguoiDung, BindingResult r,
            RedirectAttributes redirect
            , Principal p, @RequestParam(value = "matKhaumoi",required = false) String matKhaumoi) {
         PasswordEncoder e = new BCryptPasswordEncoder();

            if (nguoiDung.getMa_khach_hang() == null){
                for (KhachHang s : userService.getAll()){


                        if (  userService.checkEmail(nguoiDung.getEmail() , nguoiDung.getMa_khach_hang())){
                            redirect.addFlashAttribute("error"," email này đã tồn tại");
                            return "redirect:/nhan-su/admin/nguoi-dung";

                    }

                }
            }
            if (matKhaumoi != null && !matKhaumoi.isBlank()){
                nguoiDung.setMatKhau_hash(e.encode(matKhaumoi));
            }
                for (FieldError fe : r.getFieldErrors()){
                    if (fe.getField().equals("matKhau_hash") && matKhaumoi != null && !matKhaumoi.isBlank()){
                        nguoiDung.setMatKhau_hash(e.encode(matKhaumoi));
                    }else{
                        redirect.addFlashAttribute("error",fe.getDefaultMessage());
                        return "redirect:/nhan-su/admin/nguoi-dung";
                    }

            }
        System.out.println("ABC"+nguoiDung.getMatKhau_hash());


            if ( nguoiDung.getMa_khach_hang() != null){


                nguoiDung.setNgayCapNhat(LocalDateTime.now());
                for (KhachHang s : userService.getAll()){
                          if (s.getMa_khach_hang().equals(nguoiDung.getMa_khach_hang())){

                              if (s.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF") && !nguoiDung.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF")){
                                      if (datPhongrepo.FindbyNguoiDung(s.getMa_khach_hang()) != null){
                                          redirect.addFlashAttribute("error","không thể cập nhật: người dùng có vai trò nhân viên có đơn đặt phòng khả dụng");
                                          return "redirect:/nhan-su/admin/nguoi-dung";
                                      }

                              }
                              if ( (!s.getEmail().equals(nguoiDung.getEmail() )|| userService.checkEmail(nguoiDung.getEmail(),nguoiDung.getMa_khach_hang()))){
                                 redirect.addFlashAttribute("error"," email này đã tồn tại");
                                 return "redirect:/nhan-su/admin/nguoi-dung";
                              }

                          }
                          if ((s.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF") &&  s.getSoDienThoai().equals(nguoiDung.getSoDienThoai()) )
                                  && (!s.getMa_khach_hang().equals(nguoiDung.getMa_khach_hang()) && nguoiDung.getVaiTro().getTenVaiTro().equalsIgnoreCase("ROLE_STAFF"))){
                              redirect.addFlashAttribute("error","số điện thoại của nhân viên này đã được sử dụng bởi nhân viên khác (thông tin liên lạc của nhân viên không được trùng nhau)");
                              return "redirect:/nhan-su/admin/nguoi-dung";
                          }
                }
                userService.save(nguoiDung);
                redirect.addFlashAttribute("success", "Cap nhat nguoi dung thanh cong");
            }else{
                userService.save(nguoiDung);
                redirect.addFlashAttribute("success", "luu nguoi dung thanh cong");
            }


        return "redirect:/nhan-su/admin/nguoi-dung";
    }

    @PostMapping("/lock/{id}")
    public String delete(Principal p,@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
            userService.setTrangThai(id, false);
            redirectAttributes.addFlashAttribute("success", "Khoa nguoi dung thanh cong");

        return "redirect:/nhan-su/admin/nguoi-dung";
    }

    @GetMapping("/search")
    public String search(RedirectAttributes r,@RequestParam("keyword") String keyword,Principal p,Model model){
        if(userService.TimKiemTheoTen(keyword).size() >= 0){
            model.addAttribute("nguoiDung",new KhachHang());
            model.addAttribute("nguoiDungs",userService.search(keyword));
            model.addAttribute("vaiTros",repo.findAll());
            r.addFlashAttribute("success","tìm thành công");
        }else{
            model.addAttribute("nguoiDung",new KhachHang());
            model.addAttribute("nguoiDungs",userService.getAll());
            model.addAttribute("vaiTros",repo.findAll());
            r.addFlashAttribute("error","không tìm thấy tên trên yêu cầu");
        }

        return "admin/nguoi-dung-list";
    }
}

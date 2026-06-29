package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.service.UserService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/khach-hang")
public class AdminKhachHangController {
    @Autowired
    private NguoiDungRepository ndRepo;
    @Autowired
    private VaiTroRepo vtRepo;
    @Autowired
    private UserService userService;

    @GetMapping
    public String get_0(Model model,
                        @RequestParam(value = "keyword", required = false) String ten)
    {
        model.addAttribute("keyword", ten);
        model.addAttribute("khachHangs", ndRepo.findAllKhach(ten));
        model.addAttribute("khachHang", new NguoiDung());
        model.addAttribute("vaiTros", vtRepo.findAll());

        return "admin/khach-hang-list";
    }

    @GetMapping("/edit/{id}")
    public String get_1(Model model, @PathVariable("id") Integer id)
    {
        model.addAttribute("khachHangs", ndRepo.findAllKhach(null));
        model.addAttribute("khachHang", ndRepo.findById(id));
        model.addAttribute("vaiTros", vtRepo.findAll());

        return "admin/khach-hang-list";
    }

    @PostMapping("/save")
    public String post_0(
            @Valid NguoiDung nguoiDung,
            BindingResult r,
            RedirectAttributes redirect,
            @RequestParam(value = "matKhauMoi", required = false) String matKhauMoi
    )
    {
        PasswordEncoder e = new BCryptPasswordEncoder();

        if(userService.checkSoDienThoai(nguoiDung.getSoDienThoai(), nguoiDung.getMaNguoiDung()) ||
                userService.checkEmail(nguoiDung.getEmail(), nguoiDung.getMaNguoiDung())
        )
        {
            redirect.addFlashAttribute("error","số điện thoại hoặc email này đã tốn tại");
            return "redirect:/admin/khach-hang";
        }
        if(nguoiDung.getMaNguoiDung() != null)
        {
            nguoiDung.setMatKhau_hash(ndRepo.findById(nguoiDung.getMaNguoiDung()).get().getMatKhau_hash());
        }
        if(matKhauMoi != null && !matKhauMoi.isBlank())
        {
            nguoiDung.setMatKhau_hash(e.encode(matKhauMoi));
        }
        if(r.hasErrors())
        {
            redirect.addFlashAttribute("error",r.getFieldError().getDefaultMessage());
            return "redirect:/admin/khach-hang";
        }
//        if(nguoiDung.getMatKhau_hash().isEmpty())
//        {
//            redirect.addFlashAttribute("error","mật khẩu không được để trống");
//            return "redirect:/admin/khach-hang";
//        }

//        if (nguoiDung.getMaNguoiDung() != null)
//        {
//            nguoiDung.setNgayCapNhat(LocalDateTime.now());
//            for (NguoiDung s : userService.getAll()){
//                if ((!s.getSoDienThoai().equals(nguoiDung.getSoDienThoai()) && userService.checkSoDienThoai(nguoiDung.getSoDienThoai())) ||
//                        (!s.getEmail().equals(nguoiDung.getEmail()) && userService.checkEmail(nguoiDung.getEmail())))
//                {
//                    redirect.addFlashAttribute("error","số điện thoại hoặc email này đã tồn tại");
//
//                    return "redirect:/admin/khach-hang";
//
//                }
//            }
//
//            userService.save(nguoiDung);
//            redirect.addFlashAttribute("success", "Cap nhat nguoi dung thanh cong");
//        } else {
            userService.save(nguoiDung);
            redirect.addFlashAttribute("success", "Luu nguoi dung thanh cong");
//        }

        return "redirect:/admin/khach-hang";
    }

    @PostMapping("/lock/{id}")
    public String post_1(
            Principal p,
            @PathVariable("id") Integer id,
            RedirectAttributes redirect
    )
    {
        userService.setTrangThai(id, false);
        redirect.addFlashAttribute("success", "Khoa nguoi dung thanh cong");

        return "redirect:/admin/khach-hang";
    }
}

package su26sd09.su26sd09.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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



    public boolean CheckRole(String email){
        return NguoiDungRepo.existsByEmailAndVaiTro_TenVaiTro(email, "ROLE_ADMIN");
    }


    @GetMapping
    public String index(Model model){
        NhanSu nv = new NhanSu();
        model.addAttribute("nhanViens",repo.findAll());
        model.addAttribute("nhanVien",nv);
        model.addAttribute("vaiTros",vaiTroRepo.findAll());


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
                               @RequestParam(value = "matKhaumoi", required = false) String matKhauMoi,
                               @RequestParam(value = "maNguoiDung", required = false) Integer maNguoiDung) {

        // Guard clause — không bọc toàn bộ trong if
        if (!CheckRole(principal.getName())) {
            return "redirect:/admin/nhan-vien";
        }

        boolean isNew = nv.getId() == 0;

        VaiTro roleStaff = vaiTroRepo.findbyname("ROLE_STAFF");
        if (isNew && maNguoiDung != null) {
            return themNhanVienTuNguoiDungCoSan(nv, maNguoiDung, redirect,roleStaff);
        }

        for (FieldError fe : bindingResult.getFieldErrors()) {
            if (fe.getField().equals("matKhau_hash") && matKhauMoi != null && !matKhauMoi.isBlank()) {
                nv.setMat_khau_hash(passwordEncoder.encode(matKhauMoi));
            } else if (fe.getField().equals("n.vaiTro") && roleStaff != null) {
                nv.setVaitro(roleStaff);
            } else {
                redirect.addFlashAttribute("error", fe.getDefaultMessage());
                return "redirect:/admin/nhan-vien";
            }
        }

        if (isNew) {
            return themNhanVienMoi(roleStaff,nv, redirect);
        }

        if (maNguoiDung != null) {
            return capNhatNhanVien(nv, maNguoiDung,roleStaff, matKhauMoi, redirect);
        }

        redirect.addFlashAttribute("error", "vui lòng chọn nhân viên");
        return "redirect:/admin/nhan-vien";
    }


    private String themNhanVienTuNguoiDungCoSan(NhanSu nv, Integer maNguoiDung,
                                                RedirectAttributes redirect, VaiTro v) {
        if (repo.IsNhanVienTonTai(maNguoiDung)) {
            redirect.addFlashAttribute("error", "nhân viên này đã tồn tại");
            return "redirect:/admin/nhan-vien";
        }
        if (nv.getMaCCCD() == null || nv.maCCCD.isBlank()) {
            redirect.addFlashAttribute("error", "mã căn cước công dân không được để trống");
            return "redirect:/admin/nhan-vien";
        }
        if (nv.getBoPhan() == null || nv.getBoPhan().isBlank()) {
            redirect.addFlashAttribute("error", "bộ phận không được để trống");
            return "redirect:/admin/nhan-vien";
        }

        KhachHang nguoiDung = NguoiDungRepo.Getbyid(maNguoiDung);
        if (nguoiDung == null) {
            redirect.addFlashAttribute("error", "không tìm thấy tài khoản người dùng");
            return "redirect:/admin/nhan-vien";
        }
        String oldEmail = nguoiDung.getEmail();

        if (nv.getHoten() != null && !nv.getHoten().isBlank()) {
            nv.setHoten(nv.getHoten());
        }
        if (nv.getDia_chi() != null && !nv.getDia_chi().isBlank()) {
            nguoiDung.setDiaChi(nv.getDia_chi());
        }
        if (nv.getSdt() != null && !nv.getSdt().isBlank()) {
            nguoiDung.setSoDienThoai(nv.getSdt());
        }
        if (nv.getEmail() != null && !nv.getEmail().isBlank()) {
            nguoiDung.setEmail(nv.getEmail());
        }
        nguoiDung.setNgayCapNhat(LocalDateTime.now());
        nguoiDung.setVaiTro(v);

        if (!nguoiDung.getEmail().equals(oldEmail)
                && NguoiDungRepo.checkEmail(nguoiDung.getEmail(), nguoiDung.getMa_khach_hang())) {
            redirect.addFlashAttribute("error", "email đã tồn tại");
            return "redirect:/admin/nhan-vien";
        }
        if (nvrepo.existsBySdt(nguoiDung.getSoDienThoai())) {
            redirect.addFlashAttribute("error", "số điện thoại đã được đăng ký");
            return "redirect:/admin/nhan-vien";
        }
        if (nvrepo.existsByMaCCCD(nv.getMaCCCD())) {
            redirect.addFlashAttribute("error", "mã CCCD đã được đăng ký");
            return "redirect:/admin/nhan-vien";
        }
         if (nvrepo.CheckAge(nv.ngaySinh) == false){
             redirect.addFlashAttribute("error","ngày sinh không hợp lệ( nhân viên có tuổi dưới 18)");
             return "redirect:/admin/nhan-vien";
         }

        NguoiDungRepo.save(nguoiDung);
        repo.save(nv);
        redirect.addFlashAttribute("success", "thêm nhân viên thành công");
        return "redirect:/admin/nhan-vien";
    }

    private String themNhanVienMoi(VaiTro v, NhanSu nv, RedirectAttributes redirect) {
        if (nv.getBoPhan() == null || nv.getBoPhan().isBlank()) {
            return "redirect:/admin/nhan-vien";
        }
        if (NguoiDungRepo.checkEmail(nv.getEmail(), nv.getId())) {
            redirect.addFlashAttribute("error", "email đã tồn tại");
            return "redirect:/admin/nhan-vien";
        }
        if (nvrepo.existsBySdt(nv.getSdt())){
            redirect.addFlashAttribute("error","số điện thoại đã được đăng ký ");
            return "redirect:/admin/nhan-vien";
        }
        if (repo.checkTrungCccd(nv.getMaCCCD(), nv.getId())) {
            redirect.addFlashAttribute("error", "mã CCCD đã được đăng ký");
            return "redirect:/admin/nhan-vien";
        }
        if (repo.CheckAge(nv.ngaySinh) == false){
            redirect.addFlashAttribute("error","ngày sinh không hợp lệ (nhân viên dưới 18 tuổi)");
            return "redirect:/admin/nhan-vien";
        }
        nv.setVaitro(v);

        repo.save(nv);
        redirect.addFlashAttribute("success", "thêm nhân viên thành công");
        return "redirect:/admin/nhan-vien"; // fix: thiếu return trong code gốc
    }
    @Transactional
    public String capNhatNhanVien(NhanSu nv, Integer maNguoiDung,
                                  VaiTro v, String matKhauMoi, RedirectAttributes redirect) {
        if (repo.TrungNv(maNguoiDung, nv.getId())) { // fix: bỏ == true
            redirect.addFlashAttribute("error", "vui lòng chọn mã nhân viên không trùng với nhân viên khác");
            return "redirect:/admin/nhan-vien";
        }

        KhachHang nguoiDung = NguoiDungRepo.Getbyid(maNguoiDung);
        String oldEmail = nguoiDung.getEmail();

        nguoiDung.setNgayCapNhat(LocalDateTime.now());
        nguoiDung.setHoTen(nv.getHoten());
        nguoiDung.setDiaChi(nv.getDia_chi());
        nguoiDung.setSoDienThoai(nv.getSdt());
        nguoiDung.setEmail(nv.getEmail());
        nguoiDung.setTrangThai(nv.isTrang_thai());
        nguoiDung.setVaiTro(v);

        // Password: đổi nếu có matKhauMoi, giữ nguyên nếu không
        if (matKhauMoi != null && !matKhauMoi.isEmpty()) {
            nguoiDung.setMatKhau_hash(passwordEncoder.encode(matKhauMoi));
        } else {
            nguoiDung.setMatKhau_hash(nv.getMat_khau_hash());
        }



        // fix: check email thay đổi TRƯỚC khi query DB (short-circuit)
        if (!nv.getEmail().equals(oldEmail)
                && NguoiDungRepo.checkEmail(nv.getEmail(), nv.getId())) {
            redirect.addFlashAttribute("error", "email đã tồn tại");
            return "redirect:/admin/nhan-vien";
        }
        if (nvrepo.existsBySdt(nv.getSdt())){
            redirect.addFlashAttribute("error","số điện thoại đã được đăng ký ");
            return "redirect:/admin/nhan-vien";
        }
        if (repo.checkTrungCccd(nv.getMaCCCD(), nv.getId())) {
            redirect.addFlashAttribute("error", "mã CCCD đã được đăng ký");
            return "redirect:/admin/nhan-vien";
        }
        if (repo.CheckAge(nv.ngaySinh) == false){
            redirect.addFlashAttribute("error","ngày sinh không hợp lệ (nhân viên dưới 18 tuổi)");
            return "redirect:/admin/nhan-vien";
        }
        repo.save(nv);
        redirect.addFlashAttribute("success", "cập nhật nhân viên thành công");
        return "redirect:/admin/nhan-vien";
    }


    @GetMapping("/edit/{id}")
    public String editNhanVien(Model model,Principal p,@PathVariable("id") int id){
        if(CheckRole(p.getName())){
            model.addAttribute("nhanViens",repo.findAll());
            model.addAttribute("nhanVien",repo.findbyid(id));
            model.addAttribute("vaiTros",vaiTroRepo.findAll());

            return "admin/nhan-vien-list";
        }
        return "redirect:/home";
    }


    @GetMapping("/search")
    public String searchNhanVien(Model model, Principal p ,@RequestParam("keyword") String name , RedirectAttributes redirect){
        if (CheckRole(p.getName())){
            NhanSu nv = new NhanSu();
            model.addAttribute("nhanViens",repo.findByName(name));
            model.addAttribute("nhanVien",nv);
            model.addAttribute("vaiTros",vaiTroRepo.findAll());
            redirect.addFlashAttribute("success","tổng số tìm được = " + repo.findByName(name).size());
            return "admin/nhan-vien-list";
        }
        return "redirect:/admin/nhan-vien";
    }
}

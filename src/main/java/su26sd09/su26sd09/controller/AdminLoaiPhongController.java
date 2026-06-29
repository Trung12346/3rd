package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.service.LoaiPhongService;

@Controller
@RequestMapping("/admin/loai-phong")
public class AdminLoaiPhongController {

    @Autowired
    LoaiPhongService repo;

    @GetMapping
    public String index(Model model){

        model.addAttribute("loaiPhong",new LoaiPhong());
        model.addAttribute("loaiPhongs",repo.findAll());
        return "admin/loai-phong-list";
    }

    @GetMapping("/edit/{id}")
    public String edit(Model model,@PathVariable("id") int id){
        model.addAttribute("loaiPhong",repo.findbyid(id));
        model.addAttribute("loaiPhongs",repo.findAll());
        return "admin/loai-phong-list";
    }


    @PostMapping("/save")
    public String save(RedirectAttributes redirect, @ModelAttribute("loaiPhong") LoaiPhong l , BindingResult b){
        if (b.hasErrors()){
            redirect.addFlashAttribute("error",b.getFieldError().getDefaultMessage());
            return "redirect:/admin/loai-phong";
        }
        if (repo.CheckTrungLoai(l)){
            redirect.addFlashAttribute("error","tên loại phòng đã tồn tại");
            return "redirect:/admin/loai-phong";
        }
        repo.save(l);
        return "redirect:/admin/loai-phong";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") int id){
        repo.delete(repo.findbyid(id));
        return "redirect:/admin/loai-phong";
    }


    @GetMapping("/tim-kiem")
    public String timKiem(Model model,@RequestParam("keyword") String keyword){
        if (keyword != null){
            model.addAttribute("loaiPhong",new LoaiPhong());
            model.addAttribute("loaiPhongs",repo.findbyName(keyword));
            model.addAttribute("success","tìm kiếm thành công");
            return "admin/loai-phong-list";
        }
        model.addAttribute("error","không tìm thấy kết quả khả dụng");
        return "redirect:/admin/loai-phong";
    }

}
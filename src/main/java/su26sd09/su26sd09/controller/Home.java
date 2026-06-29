package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.service.CustomerUserDetailsService;
import su26sd09.su26sd09.service.PhongService;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/home")
@Controller
public class Home {

     @Autowired
    CustomerUserDetailsService repo;
    @Autowired
    NguoiDungRepository UserRepo;
    @Autowired
    private PhongService phongService;

    @GetMapping("")
    public String home(Model model){
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        Map<Integer, Long> soPhongTrongTheoLoai = new HashMap<>();
        Map<Integer, String> anhLoaiPhong = new HashMap<>();

        for (LoaiPhong loai : loaiPhongs) {
            soPhongTrongTheoLoai.put(loai.getId(), phongService.countPhongTrongTheoLoai(loai.getId()));
            anhLoaiPhong.put(loai.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
        }

        model.addAttribute("loaiPhongs", loaiPhongs);
        model.addAttribute("soPhongTrongTheoLoai", soPhongTrongTheoLoai);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);

        return "index";
    }
}

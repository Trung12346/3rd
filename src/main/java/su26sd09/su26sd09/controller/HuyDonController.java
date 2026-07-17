package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.KetQuaHuyDonDTO;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.HuyDonService;

@Controller
public class HuyDonController {

    @Autowired
    HuyDonService huyDonService;

    @Autowired
    DatPhongService datPhongService;

    @PostMapping("/dat-phong/{id}/huy")
    public String huy(@PathVariable Integer id,
                      RedirectAttributes redirectAttributes,
                      Authentication auth) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null || dp.getN() == null
                || !dp.getN().getEmail().equalsIgnoreCase(auth.getName())) {
            redirectAttributes.addFlashAttribute("thongBao", "Ban khong co quyen huy don nay");
            return "redirect:/gio-hang";
        }

        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);
        redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());
        return "redirect:/gio-hang";
    }
}
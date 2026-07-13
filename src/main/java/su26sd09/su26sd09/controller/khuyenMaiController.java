package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.service.khuyenMaiService;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/khuyen-mai")
public class khuyenMaiController {

    @Autowired
    private khuyenMaiService svc;

    @GetMapping
    public String index(@RequestParam(value = "q", required = false) String q, Model model) {

        List<KhuyenMai> khuyenMais = svc.findAllActive()
                .filter(km -> km.ngayKetThuc == null || !km.ngayKetThuc.isBefore(LocalDate.now()))
                .collect(Collectors.toList());

        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            khuyenMais = khuyenMais.stream()
                    .filter(km -> km.promoCode != null && km.promoCode.toLowerCase().contains(needle))
                    .collect(Collectors.toList());
        }

        model.addAttribute("khuyenMais", khuyenMais);
        model.addAttribute("q", q);
        return "customer-khuyen-mai";
    }
}

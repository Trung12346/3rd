package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.entity.GiayTo;
import su26sd09.su26sd09.repository.GiayToRepo;

/**
 * Trang tra cuu giay to phap ly (CCCD / Ho chieu) cua khach.
 * Chi tra cuu (read-only) - khong tao/sua/xoa; viec tao giay to van thuc hien
 * o modal "Them giay to" tai So do phong khi check-in.
 *
 * Truy cap: ADMIN va nhan vien Le Tan (link trong sidebar chi hien voi
 * Le Tan - xem cms-sidebar.html / CmsSidebarAccessAdvice); nhan vien bo phan
 * khac (vd Ve Sinh) khong thay link nay.
 */
@Controller
@RequestMapping("/nhan-su")
public class NhanVienGiayToController {

    @Autowired
    private GiayToRepo giayToRepo;

    private static final int PAGE_SIZE = 10;

    @GetMapping("/giay-to")
    public String index(
            @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(name = "loaiGiayTo", required = false, defaultValue = "") String loaiGiayTo,
            @RequestParam(name = "quocTich", required = false, defaultValue = "") String quocTich,
            @RequestParam(name = "maDatPhong", required = false) Integer maDatPhong,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<GiayTo> giayToPage = giayToRepo.searchFiltered(
                keyword, loaiGiayTo, quocTich, maDatPhong, pageable);

        model.addAttribute("giayTos", giayToPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("loaiGiayTo", loaiGiayTo);
        model.addAttribute("quocTich", quocTich);
        model.addAttribute("maDatPhong", maDatPhong);

        model.addAttribute("currentPage", giayToPage.getNumber());
        model.addAttribute("totalPages", giayToPage.getTotalPages());
        model.addAttribute("totalItems", giayToPage.getTotalElements());
        model.addAttribute("pageSize", PAGE_SIZE);

        return "nhan-vien/giay-to-list";
    }
}

package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.service.NhanVienService;

/**
 * Bơm các cờ (flag) quyết định hiển thị của cms-sidebar dựa trên vai trò và
 * bộ phận của nhân viên đang đăng nhập:
 *
 * - Nhân viên (ROLE_STAFF) thuộc bộ phận "Lễ tân": chỉ được thấy các link
 *   /nhan-su/so-do-phong, /nhan-su/lich-phong, /nhan-su/admin/hoan-tien
 *   trong khu vực Nghiệp vụ của sidebar.
 * - Nhân viên (ROLE_STAFF) thuộc bộ phận "Vệ Sinh": ẩn toàn bộ cms-sidebar.
 */
@ControllerAdvice
public class CmsSidebarAccessAdvice {

    @Autowired
    private NhanVienRepo nhanVienRepo;

    @Autowired
    private NhanVienService nhanVienService;

    @ModelAttribute
    public void addCmsSidebarFlags(Model model) {
        boolean leTanRestricted = false;
        boolean sidebarHidden = false;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            boolean laStaff = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_STAFF"));

            if (laStaff) {
                NhanSu nv = nhanVienRepo.findByEmail(authentication.getName()).orElse(null);
                if (nv != null) {
                    String boPhan = nv.getBoPhan();
                    leTanRestricted = nhanVienService.laBoPhanLeTan(boPhan);
                    sidebarHidden = nhanVienService.laBoPhanVeSinh(boPhan);
                }
            }
        }

        model.addAttribute("cmsSidebarHidden", sidebarHidden);
        model.addAttribute("cmsSidebarLeTanRestricted", leTanRestricted);
    }
}

package su26sd09.su26sd09.controller;

import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.CustomerReviewRequest;
import su26sd09.su26sd09.dto.CustomerReviewViewDTO;
import su26sd09.su26sd09.dto.RoomReviewReplyRequest;
import su26sd09.su26sd09.dto.RoomReviewRequest;
import su26sd09.su26sd09.dto.RoomReviewViewDTO;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.DanhGiaRepo;
import su26sd09.su26sd09.repository.DatPhongRepo;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.service.ReviewService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Controller
public class DanhGiaController {

    private static final Set<String> REPLY_AUTHORITIES =
            Set.of("ROLE_STAFF", "ROLE_ADMIN", "ROLE_EMPLOYEE");

    private final DanhGiaRepo danhGiaRepo;
    private final DatPhongRepo datPhongRepo;
    private final NguoiDungRepository nguoiDungRepository;
    private final ReviewService reviewService;

    public DanhGiaController(DanhGiaRepo danhGiaRepo,
                             DatPhongRepo datPhongRepo,
                             NguoiDungRepository nguoiDungRepository,
                             ReviewService reviewService) {
        this.danhGiaRepo = danhGiaRepo;
        this.datPhongRepo = datPhongRepo;
        this.nguoiDungRepository = nguoiDungRepository;
        this.reviewService = reviewService;
    }

    @GetMapping("/admin/loai-phong/{id}/danh-gia")
    public String adminDanhGiaList(
            Model model,
            @PathVariable("id") Integer id,
            @RequestParam(value = "noi-dung", required = false) String noiDung
    ) {
        model.addAttribute("danhGias", danhGiaRepo.findByLoaiPhong(id, noiDung));
        return "admin/danh-gia-list";
    }

    @PostMapping("/admin/loai-phong/{id}/danh-gia/{dg-id}")
    public String adminSavePhanHoi(
            @PathVariable("dg-id") Integer dgId,
            @PathVariable("id") Integer id,
            @RequestParam("phanHoi") String phanHoi,
            RedirectAttributes redirect
    ) {
        DanhGia dg = danhGiaRepo.findById(dgId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá #" + dgId));

        if (phanHoi == null || phanHoi.isBlank()) {
            redirect.addFlashAttribute("phError", "phản hồi trống");
            return String.format("redirect:/admin/loai-phong/%d/danh-gia", id);
        }
        dg.phanHoi = phanHoi;
        danhGiaRepo.save(dg);
        redirect.addFlashAttribute("id", id);
        redirect.addFlashAttribute("phSuccess", "lưu thành công");
        return String.format("redirect:/admin/loai-phong/%d/danh-gia", id);
    }

    @GetMapping("/home/reviews")
    public String customerReviewPage(Model model) {
        addCustomerReviewModel(model);
        return "customer-reviews-page";
    }

    @GetMapping("/home/reviews/fragment")
    public String customerReviewFragment(Model model) {
        addCustomerReviewModel(model);
        return "fragments/customer-reviews :: customerReviews";
    }

    @GetMapping("/home/reviews/data")
    @ResponseBody
    public List<CustomerReviewViewDTO> customerReviewData() {
        return findApprovedReviews();
    }

    @PostMapping("/home/reviews")
    public String submitCustomerReview(
            @Valid @ModelAttribute CustomerReviewRequest request,
            BindingResult bindingResult,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) {
            redirectAttributes.addFlashAttribute("reviewError", "Vui lòng đăng nhập để gửi đánh giá.");
            return "redirect:/Login";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("reviewError", firstError(bindingResult));
            return "redirect:/home/reviews";
        }

        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(principal.getName());
        if (nguoiDung == null) {
            redirectAttributes.addFlashAttribute("reviewError", "Không tìm thấy tài khoản đăng nhập.");
            return "redirect:/Login";
        }

        DatPhong datPhong = null;
        if (request.getMaDatPhong() != null) {
            datPhong = datPhongRepo.findById(request.getMaDatPhong()).orElse(null);
            if (!isBookingOwner(datPhong, nguoiDung)) {
                redirectAttributes.addFlashAttribute("reviewError", "Mã đặt phòng không hợp lệ với tài khoản hiện tại.");
                return "redirect:/home/reviews";
            }
        }

        DanhGia danhGia = new DanhGia();
        danhGia.setN(nguoiDung);
        danhGia.setD(datPhong);
        danhGia.setDiemDanhGia(Math.max(1, Math.min(5, request.getDiemDanhGia())));
        danhGia.setNoiDung(request.getNoiDung().trim());
        danhGia.setDaDuyet(false); // chờ admin duyệt
        danhGia.setNgayTao(LocalDateTime.now());
        danhGiaRepo.save(danhGia);

        redirectAttributes.addFlashAttribute("reviewSuccess", "Cảm ơn bạn! Đánh giá đã được gửi và đang chờ duyệt.");
        return "redirect:/home/reviews";
    }

    @GetMapping("/phong/reviews/rooms/{maPhong}/fragment")
    public String roomReviewsFragment(@PathVariable int maPhong, Model model) {
        model.addAttribute("maPhong", maPhong);
        model.addAttribute("roomReviews", reviewService.findApprovedReviewsByRoom(maPhong));
        return "fragments/room-reviews :: roomReviews";
    }

    @GetMapping("/phong/reviews/rooms/{maPhong}/data")
    @ResponseBody
    public List<RoomReviewViewDTO> roomReviewsData(@PathVariable int maPhong) {
        return reviewService.findApprovedReviewsByRoom(maPhong);
    }

    @PostMapping({"/phong/reviews/rooms", "/phong/reviews/rooms/"})
    public String submitRoomReviewMissingRoom(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("roomReviewDenied", true);
        redirectAttributes.addFlashAttribute("roomReviewError",
                "Không xác định được phòng cần đánh giá. Vui lòng mở lại trang chi tiết phòng rồi thử lại.");
        return "redirect:/phong";
    }

    @PostMapping("/phong/reviews/rooms/{maPhong}")
    public String submitRoomReview(
            @PathVariable int maPhong,
            @Valid @ModelAttribute RoomReviewRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (!isLoggedIn(authentication)) {
            redirectAttributes.addFlashAttribute("roomReviewError", "Vui lòng đăng nhập để gửi đánh giá.");
            return "redirect:/Login";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("roomReviewError", firstError(bindingResult));
            return redirectToRoom(maPhong);
        }
        try {
            reviewService.createRoomReview(maPhong, authentication.getName(), request);
            redirectAttributes.addFlashAttribute("roomReviewSuccess", "Cảm ơn bạn! Đánh giá đã được gửi thành công.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("roomReviewDenied", true);
            redirectAttributes.addFlashAttribute("roomReviewError", ex.getMessage());
        }
        return redirectToRoom(maPhong);
    }

    @PostMapping("/phong/reviews/{reviewId}/reply")
    public String replyRoomReview(
            @PathVariable int reviewId,
            @Valid @ModelAttribute RoomReviewReplyRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Integer maPhong = request.getMaPhong();
        if (maPhong == null) {
            maPhong = reviewService.findRoomIdByReviewId(reviewId);
        }

        if (!canReply(authentication)) {
            redirectAttributes.addFlashAttribute("roomReviewError", "Bạn không có quyền phản hồi đánh giá.");
            return maPhong != null ? redirectToRoom(maPhong) : "redirect:/phong";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("roomReviewError", firstError(bindingResult));
            return maPhong != null ? redirectToRoom(maPhong) : "redirect:/phong";
        }
        try {
            reviewService.replyToReview(reviewId, request);
            redirectAttributes.addFlashAttribute("roomReviewSuccess", "Đã lưu phản hồi đánh giá.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("roomReviewError", ex.getMessage());
        }
        return maPhong != null ? redirectToRoom(maPhong) : "redirect:/phong";
    }

    private void addCustomerReviewModel(Model model) {
        if (!model.containsAttribute("customerReviews")) {
            model.addAttribute("customerReviews", findApprovedReviews());
        }
        if (!model.containsAttribute("reviewForm")) {
            model.addAttribute("reviewForm", new CustomerReviewRequest());
        }
    }

    private List<CustomerReviewViewDTO> findApprovedReviews() {
        return danhGiaRepo.findAll()
                .stream()
                .filter(DanhGia::isDaDuyet)
                .sorted(Comparator.comparing(DanhGia::getNgayTao,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(CustomerReviewViewDTO::fromEntity)
                .toList();
    }

    private boolean isBookingOwner(DatPhong datPhong, NguoiDung nguoiDung) {
        return datPhong != null
                && datPhong.getN() != null
                && datPhong.getN().getMaNguoiDung() != null
                && datPhong.getN().getMaNguoiDung().equals(nguoiDung.getMaNguoiDung());
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean canReply(Authentication authentication) {
        return isLoggedIn(authentication)
                && authentication.getAuthorities().stream()
                .anyMatch(a -> REPLY_AUTHORITIES.contains(a.getAuthority()));
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Dữ liệu chưa hợp lệ.");
    }

    private String redirectToRoom(int maPhong) {
        return "redirect:/phong/" + maPhong + "#roomReviews";
    }
}
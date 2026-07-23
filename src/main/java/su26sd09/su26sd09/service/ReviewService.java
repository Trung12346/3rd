package su26sd09.su26sd09.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.dto.RoomReviewEligibilityDTO;
import su26sd09.su26sd09.dto.RoomReviewReplyRequest;
import su26sd09.su26sd09.dto.RoomReviewRequest;
import su26sd09.su26sd09.dto.RoomReviewViewDTO;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.DanhGiaRepo;
import su26sd09.su26sd09.repository.DatPhongRepo;
import su26sd09.su26sd09.repository.KhachHangRepository;
import su26sd09.su26sd09.repository.PhongRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewService {

    private static final Pattern ROOM_MARKER_PATTERN = Pattern.compile("^\\[ROOM:(\\d+)]\\s*");

    private final DanhGiaRepo danhGiaRepo;
    private final DatPhongRepo datPhongRepo;
    private final ChiTietDatPhongRepo chiTietDatPhongRepo;
    private final KhachHangRepository nguoiDungRepository;
    private final PhongRepository phongRepository;

    public ReviewService(DanhGiaRepo danhGiaRepo,
                         DatPhongRepo datPhongRepo,
                         ChiTietDatPhongRepo chiTietDatPhongRepo,
                         KhachHangRepository nguoiDungRepository,
                         PhongRepository phongRepository) {
        this.danhGiaRepo = danhGiaRepo;
        this.datPhongRepo = datPhongRepo;
        this.chiTietDatPhongRepo = chiTietDatPhongRepo;
        this.nguoiDungRepository = nguoiDungRepository;
        this.phongRepository = phongRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomReviewViewDTO> findApprovedReviewsByRoom(int maPhong) {
        assertRoomExists(maPhong);

        Map<Integer, DanhGia> reviews = new LinkedHashMap<>();
        danhGiaRepo.findDaDuyetByPhong(maPhong).forEach(danhGia -> reviews.put(danhGia.getId(), danhGia));
        danhGiaRepo.findAll()
                .stream()
                .filter(DanhGia::isDaDuyet)
                .filter(danhGia -> hasRoomMarker(danhGia, maPhong))
                .forEach(danhGia -> reviews.putIfAbsent(danhGia.getId(), danhGia));

        return reviews.values()
                .stream()
                .sorted(Comparator.comparing(DanhGia::getNgayTao, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(danhGia -> RoomReviewViewDTO.fromEntity(danhGia, maPhong))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomReviewEligibilityDTO getEligibility(int maPhong, String email) {
        assertRoomExists(maPhong);

        if (email == null || email.isBlank()) {
            return RoomReviewEligibilityDTO.disabled("Vui lòng đăng nhập để viết đánh giá cho phòng này.");
        }

        KhachHang nguoiDung = nguoiDungRepository.findByEmail(email).orElse(null);
        if (nguoiDung == null) {
            return RoomReviewEligibilityDTO.disabled("Không tìm thấy tài khoản đăng nhập.");
        }

        DatPhong newestBooking = findNewestBooking(nguoiDung, maPhong);
        if (newestBooking == null || !newestBooking.getTrangThai().equals("Da tra phong")) {
            return RoomReviewEligibilityDTO.disabled("Bạn chưa từng đặt phòng này nên không thể viết đánh giá.");
        }

        if (danhGiaRepo.existsByDatPhongId(newestBooking.getId())) {
            return RoomReviewEligibilityDTO.disabled(
                    "Bạn đã đánh giá cho lượt đặt phòng gần nhất của mình rồi. Mỗi lượt đặt phòng chỉ được đánh giá 1 lần.");
        }

        return RoomReviewEligibilityDTO.enabled(newestBooking.getId());
    }

    @Transactional
    public DanhGia createRoomReview(int maPhong, String email, RoomReviewRequest request) {
        assertRoomExists(maPhong);

        KhachHang nguoiDung = findUserByEmail(email);

        // Không tin tưởng ma_dat_phong client gửi lên: luôn tự xác định lượt đặt
        // phòng gần nhất của khách hàng cho phòng này ở phía server.
        DatPhong datPhong = findNewestBooking(nguoiDung, maPhong);
        if (datPhong == null) {
            throw new IllegalArgumentException("Bạn chưa từng đặt phòng này nên không thể viết đánh giá.");
        }
        if (isCanceledBooking(datPhong)) {
            throw new IllegalArgumentException("Lượt đặt phòng gần nhất của bạn đã bị hủy nên không thể đánh giá.");
        }
        if (danhGiaRepo.existsByDatPhongId(datPhong.getId())) {
            throw new IllegalArgumentException(
                    "Bạn đã đánh giá cho lượt đặt phòng gần nhất của mình rồi. Mỗi lượt đặt phòng chỉ được đánh giá 1 lần.");
        }

        DanhGia danhGia = new DanhGia();
        danhGia.setN(nguoiDung);
        danhGia.setD(datPhong);
        danhGia.setDiemDanhGia(Math.max(1, Math.min(5, request.getDiemDanhGia())));
        danhGia.setNoiDung(request.getNoiDung() == null ? "" : request.getNoiDung().trim());
        danhGia.setDaDuyet(true);
        danhGia.setNgayTao(LocalDateTime.now());

        try {
            return danhGiaRepo.save(danhGia);
        } catch (DataIntegrityViolationException ex) {
            // Phòng khi có 2 request submit gần như đồng thời cho cùng 1 lượt đặt phòng
            // (race condition) -> unique index ở DB sẽ chặn, ta trả về lỗi rõ ràng thay vì 500.
            throw new IllegalArgumentException(
                    "Lượt đặt phòng này vừa được đánh giá (có thể do gửi trùng). Vui lòng tải lại trang.");
        }
    }

    private DatPhong findNewestBooking(KhachHang nguoiDung, int maPhong) {
        return datPhongRepo.findBookingsForCustomerAndRoom(maPhong, nguoiDung.getMa_khach_hang(), nguoiDung.getEmail())
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public DanhGia replyToReview(int reviewId, RoomReviewReplyRequest request) {
        DanhGia danhGia = danhGiaRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá cần phản hồi."));

        danhGia.setPhanHoi(request.getPhanHoi().trim());
        return danhGiaRepo.save(danhGia);
    }

    @Transactional(readOnly = true)
    public Integer findRoomIdByReviewId(int reviewId) {
        DanhGia danhGia = danhGiaRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));

        return resolveRoomId(danhGia);
    }

    private Integer resolveRoomId(DanhGia danhGia) {
        if (danhGia.getD() == null || danhGia.getD().getId() == null) {
            return parseRoomMarker(danhGia).orElse(null);
        }

        return datPhongRepo.findPhongByDatPhongId(danhGia.getD().getId())
                .stream()
                .findFirst()
                .map(phong -> phong.getMaPhong())
                .orElseGet(() -> parseRoomMarker(danhGia).orElse(null));
    }

    private void assertRoomExists(int maPhong) {
        if (!phongRepository.existsById(maPhong)) {
            throw new IllegalArgumentException("Không tìm thấy phòng.");
        }
    }

    private KhachHang findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi đánh giá.");
        }

        KhachHang nguoiDung = nguoiDungRepository.findByEmail(email).orElse(null);
        if (nguoiDung == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập.");
        }
        return nguoiDung;
    }

    private boolean isCanceledBooking(DatPhong datPhong) {
        return datPhong != null && "Da huy".equals(datPhong.getTrangThai());
    }

    private boolean hasRoomMarker(DanhGia danhGia, int maPhong) {
        if (danhGia != null && danhGia.getD() != null && danhGia.getD().getId() != null) {
            return false;
        }

        return parseRoomMarker(danhGia)
                .map(roomId -> roomId == maPhong)
                .orElse(false);
    }

    private Optional<Integer> parseRoomMarker(DanhGia danhGia) {
        if (danhGia == null || danhGia.getNoiDung() == null) {
            return Optional.empty();
        }

        Matcher matcher = ROOM_MARKER_PATTERN.matcher(danhGia.getNoiDung());
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String roomMarker(int maPhong) {
        return "[ROOM:" + maPhong + "]";
    }

    // Vẫn giữ helper roomMarker() vì ROOM_MARKER_PATTERN/parseRoomMarker/hasRoomMarker
    // được dùng để đọc các review cũ (đã lưu trước khi có ràng buộc 1-review/booking).
}

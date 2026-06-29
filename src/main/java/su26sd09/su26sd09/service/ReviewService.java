package su26sd09.su26sd09.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.dto.RoomReviewReplyRequest;
import su26sd09.su26sd09.dto.RoomReviewRequest;
import su26sd09.su26sd09.dto.RoomReviewViewDTO;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.DanhGiaRepo;
import su26sd09.su26sd09.repository.DatPhongRepo;
import su26sd09.su26sd09.repository.NguoiDungRepository;
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
    private final NguoiDungRepository nguoiDungRepository;
    private final PhongRepository phongRepository;

    public ReviewService(DanhGiaRepo danhGiaRepo,
                         DatPhongRepo datPhongRepo,
                         ChiTietDatPhongRepo chiTietDatPhongRepo,
                         NguoiDungRepository nguoiDungRepository,
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

    @Transactional
    public DanhGia createRoomReview(int maPhong, String email, RoomReviewRequest request) {
        assertRoomExists(maPhong);

        NguoiDung nguoiDung = findUserByEmail(email);
        DatPhong datPhong = resolveBookingForRoom(request.getMaDatPhong(), nguoiDung, maPhong);

        DanhGia danhGia = new DanhGia();
        danhGia.setN(nguoiDung);
        danhGia.setD(datPhong);
        danhGia.setDiemDanhGia(Math.max(1, Math.min(5, request.getDiemDanhGia())));
        danhGia.setNoiDung(toStoredContent(maPhong, datPhong != null, request.getNoiDung()));
        danhGia.setDaDuyet(true);
        danhGia.setNgayTao(LocalDateTime.now());

        return danhGiaRepo.save(danhGia);
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

    private NguoiDung findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi đánh giá.");
        }

        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        if (nguoiDung == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập.");
        }
        return nguoiDung;
    }

    private DatPhong resolveBookingForRoom(Integer maDatPhong, NguoiDung nguoiDung, int maPhong) {
        if (maDatPhong != null) {
            DatPhong datPhong = datPhongRepo.findById(maDatPhong)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã đặt phòng."));

            if (!isBookingOwner(datPhong, nguoiDung)) {
                throw new IllegalArgumentException("Mã đặt phòng không thuộc tài khoản hiện tại.");
            }
            if (!bookingContainsRoom(datPhong, maPhong)) {
                throw new IllegalArgumentException("Mã đặt phòng không thuộc phòng này.");
            }
            if (isCanceledBooking(datPhong)) {
                throw new IllegalArgumentException("Mã đặt phòng đã hủy nên không thể đánh giá phòng này.");
            }
            return datPhong;
        }

        return datPhongRepo.FindByNguoiDung(nguoiDung.getMaNguoiDung())
                .stream()
                .filter(datPhong -> bookingContainsRoom(datPhong, maPhong))
                .filter(datPhong -> !isCanceledBooking(datPhong))
                .findFirst()
                .orElse(null);
    }

    private boolean isCanceledBooking(DatPhong datPhong) {
        return datPhong != null && "Da huy".equals(datPhong.getTrangThai());
    }

    private boolean isBookingOwner(DatPhong datPhong, NguoiDung nguoiDung) {
        return datPhong != null
                && datPhong.getN() != null
                && datPhong.getN().getMaNguoiDung() != null
                && datPhong.getN().getMaNguoiDung().equals(nguoiDung.getMaNguoiDung());
    }

    private boolean bookingContainsRoom(DatPhong datPhong, int maPhong) {
        if (datPhong == null || datPhong.getId() == null) {
            return false;
        }

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongRepo.findByDatPhongId(datPhong.getId());
        return chiTietDatPhongs.stream()
                .anyMatch(chiTiet -> chiTiet.getP() != null && chiTiet.getP().getMaPhong() == maPhong);
    }

    String toStoredContent(int maPhong, boolean hasBooking, String noiDung) {
        String cleanContent = noiDung == null ? "" : noiDung.trim();
        if (hasBooking) {
            return cleanContent;
        }
        return roomMarker(maPhong) + " " + cleanContent;
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
}

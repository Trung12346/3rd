package su26sd09.su26sd09.dto;

import java.time.LocalDateTime;

/**
 * Projection (chi doc) cho 1 dong ket qua cua DatPhongRepo.findLichBiKhoaTheoLoaiTrongKhoang:
 * 1 dot giu cho (booking dang hieu luc) cham vao 1 phong cu the thuoc 1 loai
 * phong, trong 1 khoang thoi gian dang xet. Dung de tinh "ngay het phong theo
 * loai" o PhongService, KHONG dung lai cho muc dich nao khac.
 */
public interface LichPhongTheoLoaiProjection {
    Integer getMaPhong();
    LocalDateTime getNgayNhan();
    LocalDateTime getNgayTra();
}

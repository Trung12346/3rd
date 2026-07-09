package su26sd09.su26sd09.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ràng buộc đặt phòng cho 1 phòng, suy ra từ trạng thái phòng (Phong.trangThai) +
 * đơn DatPhong gần nhất còn liên quan ("Da nhan phong" / "Da tra phong").
 *
 * Logic:
 * - Phòng "Trong" -> coTheDat = true, không có ràng buộc.
 * - Phòng "Dang su dung" + đơn "Da nhan phong" -> coTheDat = true nhưng
 *   [ngayBatDauKhoa, ngayKetThucKhoa] là khoảng ngày bị gạch chéo (đã có khách ở).
 * - Phòng "Dang su dung" + đơn "Da tra phong" -> coTheDat = true, áp dụng ràng buộc
 *   giờ nhận [gioNhanToiThieu, gioNhanToiDa] và giờ trả <= gioTraToiDa;
 *   nếu khách chọn ngoài khoảng sẽ cộng thêm phuPhiNgoaiGioVND.
 * - Các trường hợp khác -> coTheDat = false.
 */
public class RoomBookingGuardDTO {

    /** Trạng thái đơn DatPhong gần nhất còn liên quan tới phòng. Có thể null. */
    private final String trangThaiDonGanNhat;

    /** Ngày bắt đầu khoảng bị gạch chéo (chỉ dùng khi Da nhan phong). */
    private final LocalDateTime ngayBatDauKhoa;

    /** Ngày kết thúc khoảng bị gạch chéo (chỉ dùng khi Da nhan phong). */
    private final LocalDateTime ngayKetThucKhoa;

    /** Trạng thái phòng hiện tại (Trong / Dang su dung / ...). */
    private final String trangThaiPhong;

    private final LocalTime gioNhanToiThieu;
    private final LocalTime gioNhanToiDa;
    private final LocalTime gioTraToiDa;

    private final BigDecimal phuPhiNgoaiGioVND;

    private final boolean coTheDat;

    public RoomBookingGuardDTO(String trangThaiPhong,
                               String trangThaiDonGanNhat,
                               LocalDateTime ngayBatDauKhoa,
                               LocalDateTime ngayKetThucKhoa,
                               LocalTime gioNhanToiThieu,
                               LocalTime gioNhanToiDa,
                               LocalTime gioTraToiDa,
                               BigDecimal phuPhiNgoaiGioVND,
                               boolean coTheDat) {
        this.trangThaiPhong = trangThaiPhong;
        this.trangThaiDonGanNhat = trangThaiDonGanNhat;
        this.ngayBatDauKhoa = ngayBatDauKhoa;
        this.ngayKetThucKhoa = ngayKetThucKhoa;
        this.gioNhanToiThieu = gioNhanToiThieu;
        this.gioNhanToiDa = gioNhanToiDa;
        this.gioTraToiDa = gioTraToiDa;
        this.phuPhiNgoaiGioVND = phuPhiNgoaiGioVND;
        this.coTheDat = coTheDat;
    }

    /** Trả về guard mặc định cho phòng "Trong" — cho phép đặt, không ràng buộc. */
    public static RoomBookingGuardDTO empty(String trangThaiPhong) {
        return new RoomBookingGuardDTO(
                trangThaiPhong, null, null, null,
                LocalTime.of(8, 30), LocalTime.of(11, 0), LocalTime.of(18, 30),
                new BigDecimal("100000"),
                true
        );
    }

    public String getTrangThaiPhong() {
        return trangThaiPhong;
    }

    public String getTrangThaiDonGanNhat() {
        return trangThaiDonGanNhat;
    }

    public LocalDateTime getNgayBatDauKhoa() {
        return ngayBatDauKhoa;
    }

    public LocalDateTime getNgayKetThucKhoa() {
        return ngayKetThucKhoa;
    }

    public LocalTime getGioNhanToiThieu() {
        return gioNhanToiThieu;
    }

    public LocalTime getGioNhanToiDa() {
        return gioNhanToiDa;
    }

    public LocalTime getGioTraToiDa() {
        return gioTraToiDa;
    }

    public BigDecimal getPhuPhiNgoaiGioVND() {
        return phuPhiNgoaiGioVND;
    }

    public boolean isCoTheDat() {
        return coTheDat;
    }
}

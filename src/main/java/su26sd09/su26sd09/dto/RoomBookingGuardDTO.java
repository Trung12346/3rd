package su26sd09.su26sd09.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * Ràng buộc đặt phòng cho 1 phòng.
 *
 * Logic mới (Cách 1 - validate theo từng đơn):
 * - danhSachKhoaLich chứa TOÀN BỘ khoảng ngày đang bị giữ chỗ bởi các đơn
 *   "Cho xac nhan" / "Da xac nhan" / "Da nhan phong" (không gộp lại thành 1
 *   khoảng min-max, tránh chặn nhầm khoảng trống giữa 2 đơn).
 * - coTheDat chỉ = false khi phòng không tồn tại; KHÔNG còn phụ thuộc vào
 *   Phong.trangThai nữa — việc có đặt được hay không hoàn toàn dựa vào
 *   overlap ngày với danhSachKhoaLich.
 * - trangThaiDonGanNhat + giờ nhận/trả vẫn dùng để tính phụ phí ngoài giờ.
 */
public class RoomBookingGuardDTO {

    private final String trangThaiPhong;
    private final String trangThaiDonGanNhat;
    private final List<KhoangNgayBiKhoaDTO> danhSachKhoaLich;

    private final LocalTime gioNhanToiThieu;
    private final LocalTime gioNhanToiDa;
    private final LocalTime gioTraToiDa;
    private final BigDecimal phuPhiNgoaiGioVND;
    private final boolean coTheDat;

    public RoomBookingGuardDTO(String trangThaiPhong,
                               String trangThaiDonGanNhat,
                               List<KhoangNgayBiKhoaDTO> danhSachKhoaLich,
                               LocalTime gioNhanToiThieu,
                               LocalTime gioNhanToiDa,
                               LocalTime gioTraToiDa,
                               BigDecimal phuPhiNgoaiGioVND,
                               boolean coTheDat) {
        this.trangThaiPhong = trangThaiPhong;
        this.trangThaiDonGanNhat = trangThaiDonGanNhat;
        this.danhSachKhoaLich = danhSachKhoaLich != null ? danhSachKhoaLich : Collections.emptyList();
        this.gioNhanToiThieu = gioNhanToiThieu;
        this.gioNhanToiDa = gioNhanToiDa;
        this.gioTraToiDa = gioTraToiDa;
        this.phuPhiNgoaiGioVND = phuPhiNgoaiGioVND;
        this.coTheDat = coTheDat;
    }

    /** Guard rỗng — không có đơn nào đang giữ chỗ, cho phép đặt. */
    public static RoomBookingGuardDTO empty(String trangThaiPhong) {
        return new RoomBookingGuardDTO(
                trangThaiPhong, null, Collections.emptyList(),
                LocalTime.of(8, 30), LocalTime.of(11, 0), LocalTime.of(18, 30),
                new BigDecimal("100000"),
                true
        );
    }

    public String getTrangThaiPhong() { return trangThaiPhong; }
    public String getTrangThaiDonGanNhat() { return trangThaiDonGanNhat; }
    public List<KhoangNgayBiKhoaDTO> getDanhSachKhoaLich() { return danhSachKhoaLich; }
    public LocalTime getGioNhanToiThieu() { return gioNhanToiThieu; }
    public LocalTime getGioNhanToiDa() { return gioNhanToiDa; }
    public LocalTime getGioTraToiDa() { return gioTraToiDa; }
    public BigDecimal getPhuPhiNgoaiGioVND() { return phuPhiNgoaiGioVND; }
    public boolean isCoTheDat() { return coTheDat; }
}
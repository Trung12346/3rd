package su26sd09.su26sd09.dto;

import java.time.LocalDateTime;

/** Đại diện cho 1 khoảng ngày đang bị khóa bởi 1 đơn DatPhong cụ thể. */
public class KhoangNgayBiKhoaDTO {

    private final Integer maDatPhong;
    private final LocalDateTime ngayBatDau;
    private final LocalDateTime ngayKetThuc;
    private final String trangThaiDon;

    public KhoangNgayBiKhoaDTO(Integer maDatPhong, LocalDateTime ngayBatDau,
                               LocalDateTime ngayKetThuc, String trangThaiDon) {
        this.maDatPhong = maDatPhong;
        this.ngayBatDau = ngayBatDau;
        this.ngayKetThuc = ngayKetThuc;
        this.trangThaiDon = trangThaiDon;
    }

    public Integer getMaDatPhong() { return maDatPhong; }
    public LocalDateTime getNgayBatDau() { return ngayBatDau; }
    public LocalDateTime getNgayKetThuc() { return ngayKetThuc; }
    public String getTrangThaiDon() { return trangThaiDon; }
}
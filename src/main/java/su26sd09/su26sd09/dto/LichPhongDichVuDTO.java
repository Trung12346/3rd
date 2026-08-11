package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1 dong dich vu (Chi_tiet_dich_vu) gan voi 1 DatPhong, dung de hien thi
 * trong panel chi tiet dat phong o trang Lich Dat Phong.
 */
@AllArgsConstructor
@Getter
public class LichPhongDichVuDTO {
    private String tenDichVu;
    private String donVi;
    private int soLuong;
    private BigDecimal donGia;
    private BigDecimal thanhTien;
    private LocalDateTime ngaySuDung;
    private String ghiChu;
}

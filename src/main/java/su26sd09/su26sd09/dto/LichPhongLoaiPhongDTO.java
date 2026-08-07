package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Tom tat 1 LoaiPhong de hien thi trong danh sach ben trai cua
 * trang Lich Dat Phong (property management calendar).
 */
@AllArgsConstructor
@Getter
public class LichPhongLoaiPhongDTO {
    private int id;
    private String tenLoai;
    private int sucChuaToiDa;
    private BigDecimal giaCoBan;
    private String anhUrl;
    private long soLuongPhong;
}

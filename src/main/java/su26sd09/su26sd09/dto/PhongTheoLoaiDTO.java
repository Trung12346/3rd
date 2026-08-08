package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PhongTheoLoaiDTO {
    private final int maPhong;
    private final String soPhong;
    private final int soTang;
    private final String trangThai;   // "Trong", "Dang su dung", "Da dat truoc"...
    private final boolean khaDung;
    private final BigDecimal giaMoiDem;
}

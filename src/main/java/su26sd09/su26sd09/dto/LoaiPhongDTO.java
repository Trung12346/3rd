package su26sd09.su26sd09.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class LoaiPhongDTO {
    private final int loaiPhongId;
    private final String tenLoai;
    private final BigDecimal giaCoBan;
    private final List<PhongTheoLoaiDTO> phongList;

}

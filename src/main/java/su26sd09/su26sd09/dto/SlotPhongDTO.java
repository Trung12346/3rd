package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.TienNghi;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class SlotPhongDTO {
    private final Integer ctdpId;
    private final Phong phongDaGan;
    private final boolean sanSangBanGiao;
    private final String cccd;
    private final List<LoaiPhongDTO> loaiPhongOptions;
    private final BigDecimal giaKhiDat;
    private final List<TienNghi> tienNghiList;
}

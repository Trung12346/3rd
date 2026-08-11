package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TomTatDto {
    private final long soDem;
    private final BigDecimal tienPhong;
    private final BigDecimal tienDichVu;
    private final BigDecimal tienGiam;
    private final BigDecimal tienVat;
    private final BigDecimal tongTien;
    private final BigDecimal daCoc;
}

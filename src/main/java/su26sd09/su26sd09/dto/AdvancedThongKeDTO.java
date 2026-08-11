package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Payload for the "Phan Tich Nang Cao" (deep analysis) section of the
 * revenue statistics page. Aggregates KPIs and breakdown series that go
 * beyond the base totals already served by the original statistics endpoints.
 */
@AllArgsConstructor
@Getter
public class AdvancedThongKeDTO {
    // KPI nang cao
    Double revPar;                 // Doanh thu phong / tong so dem phong co the ban
    Double adr;                    // Doanh thu phong / so dem phong da ban (Average Daily Rate)
    Double tyLeHuy;                // % don dat bi huy trong ky
    Double tyLeKhachQuayLai;       // % khach co tu 2 don dat tro len trong ky

    // Co cau doanh thu (tu hoa_don)
    Double doanhThuPhong;
    Double doanhThuDichVu;
    Double tienGiam;
    Double tienVat;

    // Doanh thu ghi nhan (hoa don, accrual) vs doanh thu thuc thu (thanh toan, cash-basis)
    Double doanhThuGhiNhan;
    Double doanhThuThucThu;
    Integer soGiaoDichThanhCong;
    Double tyLeThuTien;         // % doanhThuThucThu / doanhThuGhiNhan

    // Phuong thuc thanh toan
    List<String> paymentLabels;
    List<Double> paymentValues;

    // Trang thai don dat phong
    List<String> statusLabels;
    List<Integer> statusValues;
}

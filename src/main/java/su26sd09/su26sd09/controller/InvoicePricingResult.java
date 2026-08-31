package su26sd09.su26sd09.dto;

import java.math.BigDecimal;

/**
 * Kết quả tính hóa đơn trả về bởi InvoicePricingService.recalculateInvoice()
 * và InvoicePricingService.previewInvoice(). Gộp toàn bộ các số tiền liên
 * quan vào một chỗ để tránh phải truyền/trả rời rạc từng BigDecimal như
 * trước đây (Map<String, BigDecimal> ở NhanVienCheckoutController...).
 */
public class InvoicePricingResult {

    private final BigDecimal tienPhong;
    private final BigDecimal tienDichVu;
    private final BigDecimal tienGiam;
    private final BigDecimal tienVat;
    private final BigDecimal tongTien;

    public InvoicePricingResult(BigDecimal tienPhong, BigDecimal tienDichVu,
                                 BigDecimal tienGiam, BigDecimal tienVat, BigDecimal tongTien) {
        this.tienPhong = tienPhong;
        this.tienDichVu = tienDichVu;
        this.tienGiam = tienGiam;
        this.tienVat = tienVat;
        this.tongTien = tongTien;
    }

    public BigDecimal getTienPhong() {
        return tienPhong;
    }

    public BigDecimal getTienDichVu() {
        return tienDichVu;
    }

    public BigDecimal getTienGiam() {
        return tienGiam;
    }

    public BigDecimal getTienVat() {
        return tienVat;
    }

    public BigDecimal getTongTien() {
        return tongTien;
    }

    /** Tổng trước giảm giá và VAT (tienPhong + tienDichVu). Tiện cho hiển thị. */
    public BigDecimal getTongTruocGiam() {
        return tienPhong.add(tienDichVu);
    }

    /** Tổng sau giảm giá, trước VAT. */
    public BigDecimal getTongSauGiam() {
        return getTongTruocGiam().subtract(tienGiam);
    }
}

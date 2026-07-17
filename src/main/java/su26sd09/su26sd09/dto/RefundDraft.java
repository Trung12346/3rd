package su26sd09.su26sd09.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lưu tạm thông tin admin nhập trước khi redirect sang VNPay,
 * dùng để callback xử lý khi VNPay redirect về.
 * Được lưu trong HttpSession.
 */
public class RefundDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer maHoaDon;
    private BigDecimal soTienHoan;
    private String stkNhanHoan;
    private String tenNganHang;
    private String ghiChu;
    private String emailNhanVienXuLy;
    private LocalDateTime thoiGianTao;

    public RefundDraft() {
    }

    public RefundDraft(Integer maHoaDon, BigDecimal soTienHoan, String stkNhanHoan,
                       String tenNganHang, String ghiChu, String emailNhanVienXuLy) {
        this.maHoaDon = maHoaDon;
        this.soTienHoan = soTienHoan;
        this.stkNhanHoan = stkNhanHoan;
        this.tenNganHang = tenNganHang;
        this.ghiChu = ghiChu;
        this.emailNhanVienXuLy = emailNhanVienXuLy;
        this.thoiGianTao = LocalDateTime.now();
    }

    public Integer getMaHoaDon() { return maHoaDon; }
    public void setMaHoaDon(Integer maHoaDon) { this.maHoaDon = maHoaDon; }

    public BigDecimal getSoTienHoan() { return soTienHoan; }
    public void setSoTienHoan(BigDecimal soTienHoan) { this.soTienHoan = soTienHoan; }

    public String getStkNhanHoan() { return stkNhanHoan; }
    public void setStkNhanHoan(String stkNhanHoan) { this.stkNhanHoan = stkNhanHoan; }

    public String getTenNganHang() { return tenNganHang; }
    public void setTenNganHang(String tenNganHang) { this.tenNganHang = tenNganHang; }

    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }

    public String getEmailNhanVienXuLy() { return emailNhanVienXuLy; }
    public void setEmailNhanVienXuLy(String emailNhanVienXuLy) { this.emailNhanVienXuLy = emailNhanVienXuLy; }

    public LocalDateTime getThoiGianTao() { return thoiGianTao; }
    public void setThoiGianTao(LocalDateTime thoiGianTao) { this.thoiGianTao = thoiGianTao; }
}

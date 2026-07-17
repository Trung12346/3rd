package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "thanh_toan")
public class ThanhToan {
    @Id
    @Column(name = "ma_thanh_toan")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;

    @ManyToOne
    @JoinColumn(name = "ma_hoa_don")
    public HoaDon h;

    @Column(name = "phuong_thuc")
    public String phuongThuc;

    @Column(name = "so_tien")
    public BigDecimal soTien;

    @Column(name = "trang_thai")
    public String trangThai;

    @Column(name = "ma_giao_dich")
    public String magiaodich;

    @Column(name = "loai_giao_dich")
    public String loaiGiaoDich; // "Thu tien" / "Hoan tien"

    @Column(name = "stk_nhan_hoan")
    public String stkNhanHoan;

    @Column(name = "ten_nganhang_nhan_hoan")
    public String tenNganHangNhanHoan;

    @ManyToOne
    @JoinColumn(name = "ma_nhan_vien",referencedColumnName = "ma_nhan_su")
    public NhanSu nv;

    @Column(name = "Ngay_thanh_toan")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngaythanhToan;

    @Column(name = "ghi_chu")
    public String gichu;

}

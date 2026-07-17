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
@Table(name = "hoa_don")
public class HoaDon {
    @Id
    @Column(name = "ma_hoa_don")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne
    @JoinColumn(name = "ma_dat_phong")
    public DatPhong d;

    @ManyToOne
    @JoinColumn(name = "ma_khuyen_mai")
    public KhuyenMai k;


    @ManyToOne
    @JoinColumn (name = "ma_nhan_vien_xuat",referencedColumnName = "ma_nhan_su")
    public NhanSu n;

    @Column(name = "tien_phong",precision = 14,scale = 2)
    public BigDecimal tienPhong;

    @Column(name = "tien_dich_vu",precision = 14,scale = 2)
    public BigDecimal tienDichVu;

    @Column(name = "tien_giam",precision = 14,scale = 2)
    public BigDecimal tienGiam;

    @Column(name = "tien_vat",precision = 14,scale = 2)
    public BigDecimal tienVat;

    @Column(name = "tong_tien",precision = 14,scale = 2)
    public BigDecimal tongTien;

    @Column(name = "da_thanh_toan",precision = 14,scale = 2)
    public BigDecimal daThanhToan;

    @Column(name = "ghi_chu")
    public String ghiChu;

    @Column(name = "so_tien_hoan", precision = 14, scale = 2)
    public BigDecimal soTienHoan;

    @Column(name = "ty_le_hoan", precision = 5, scale = 2)
    public BigDecimal tyLeHoan;

    @Column(name = "da_hoan_tra", precision = 14, scale = 2)
    public BigDecimal daHoanTra;

    @Column(name = "trang_thai_hoan_tien")
    public String trangThaiHoanTien;

    @Column(name = "ngay_yeu_cau_hoan")
    public LocalDateTime ngayYeuCauHoan;



    @Column(name = "ngay_xuat")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngayXuat;

    @Column(name = "ngay_cap_nhat")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngayCapNhat;

}

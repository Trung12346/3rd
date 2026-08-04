package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "dat_phong")
public class DatPhong {


    @Id
    @Column(name = "ma_dat_phong")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne
    @JoinColumn(name = "ma_khach")
    public KhachHang n;

    @JoinColumn(name = "ma_nhan_vien")
    @ManyToOne
    public NhanSu nv;

    @ManyToOne
    @JoinColumn(name = "ma_khuyen_mai",referencedColumnName = "ma_khuyen_mai")
    private KhuyenMai km;

    @Column(name = "ngay_nhan_phong")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngaydatPhong;

    @Column(name = "ngay_tra_phong")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngaytraPhong;

    @Column(name = "so_nguoi_lon")
    public int songuoiLon;

    @Column(name = "so_tre_em")
    public int sotreEm;

    @Column(name = "yeu_cau_them")
    public String yeuCauThem;

    @Column(name = "trang_thai")
    public String trangThai;

    @Column(name = "ho_ten")
    public String hoten;

    @Column(name = "email")
    public String email;

    @Column(name = "so_dien_thoai")
    public String sdt;



    @Column(name = "ngay_tao")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngayTao;

    @Column(name = "ngay_cap_nhat")
    @DateTimeFormat(pattern = "yyyy-MM-dd hh:MM:ss")
    public LocalDateTime ngayCapNhat;

    @Column(name = "ma_cccd")
    public String ma_cccd;

    // Ma tra cuu 6 ky tu hex (VD: "A3F9C1"), duoc sinh tu dong cho don dat phong
    // cua khach khong co tai khoan (n == null) de ho co the tra cuu don sau nay
    // ma khong can dang nhap.
    @Column(name = "ma_tra_cuu", length = 6, unique = true)
    public String maTraCuu;

    @OneToMany(mappedBy = "d",fetch = FetchType.EAGER)
    private List<ChiTietDatPhong> chiTietDatPhongs;

    @OneToMany(mappedBy = "datPhong",cascade = CascadeType.ALL)
    private List<Chi_tiet_dich_vu> ctdv;

}

package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "giay_to")
public class GiayTo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_scan")
    public Integer id;

    @ManyToOne
    @JoinColumn(name = "ma_chi_tiet_dat_phong")
    public ChiTietDatPhong chiTietDatPhong;

    @Column(name = "co_dai_dien")
    public Boolean coDaiDien;

    @Column(name = "loai_giay_to")
    public String loaiGiayTo;

    @Column(name = "ho_ten")
    public String hoTen;

    @Column(name = "so_dinh_danh")
    public String soDinhDanh;

    @Column(name = "ngay_sinh")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate ngaySinh;

    @Column(name = "gioi_tinh")
    public String gioiTinh;

    @Column(name = "quoc_tich")
    public String quocTich;

    @Column(name = "que_quan")
    public String queQuan;

    @Column(name = "noi_thuong_tru")
    public String noiThuongTru;

    @Column(name = "noi_cu_tru")
    public String noiCuTru;

    @Column(name = "noi_tam_tru")
    public String noiTamTru;

    @Column(name = "noi_luu_tru")
    public String noiLuuTru;

    @Column(name = "gia_tri_den")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate giaTriDen;

    @Column(name = "ngay_cap")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate ngayCap;

    @Column(name = "quoc_gia_cap_phat")
    public String quocGiaCapPhat;
}

package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "danh_gia")
public class DanhGia {
    @Id
    @Column(name = "ma_danh_gia")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;

    @ManyToOne
    @JoinColumn(name = "ma_nguoi_dung")
    public NguoiDung n;

    @ManyToOne
    @JoinColumn(name = "ma_dat_phong")
    public DatPhong d;

    @Column(name = "diem_danh_gia")
    public int diemDanhGia;

    @Column(name= "noi_dung")
    public String noiDung;

    @Column(name = "phan_hoi")
    public String phanHoi;

    @Column(name = "da_duyet")
    public boolean daDuyet;

    @Column(name = "ngay_tao")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDateTime ngayTao;

}

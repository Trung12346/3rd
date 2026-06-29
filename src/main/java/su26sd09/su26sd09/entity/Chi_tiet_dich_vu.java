package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "chi_tiet_dich_vu")
public class Chi_tiet_dich_vu {
    @Id
    @Column(name = "ma_chi_tiet_dv")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "so_luong")
    private Integer soluong;

    @Column(name = "ngay_su_dung")
    private LocalDateTime ngay_su_dung;

    @Column(name = "ghi_chu")
    private String ghichu;

    @Column(name = "don_gia")
    private BigDecimal donGia;

    @ManyToOne
    @JoinColumn(name = "ma_dat_phong",referencedColumnName = "ma_dat_phong")
    private DatPhong datPhong;


    @ManyToOne
    @JoinColumn(name = "ma_dich_vu",referencedColumnName = "ma_dich_vu")
    private Dich_vu dv;
}

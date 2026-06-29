package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chi_tiet_dat_phong")
public class ChiTietDatPhong {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_chi_tiet")
    private int id;


    @ManyToOne
    @JoinColumn(name = "ma_phong")
    private Phong p;

    @Column(name ="gia_moi_dem")
    private BigDecimal giaMoiDem;

    @Column(name = "gia_khi_dat")
    private BigDecimal giaKhiDat;

    @Column(name = "ma_cccd")
    private String ma_cccd;

    @ManyToOne
    @JoinColumn(name = "ma_dat_phong",referencedColumnName = "ma_dat_phong")
    private DatPhong d;


}

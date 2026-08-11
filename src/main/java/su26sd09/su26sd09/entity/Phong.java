package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "phong")
public class Phong {
    @Id
    @Column(name = "ma_phong")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int maPhong;

    @ManyToOne
    @JoinColumn(name = "ma_loai_phong", referencedColumnName = "ma_loai_phong")
    private LoaiPhong loaiPhong;


    @Column(name = "so_phong", nullable = false, length = 10)
    private String soPhong;

    @Column(name = "so_tang", nullable = false)
    private int soTang;

    @Column(name = "gia_moi_dem", nullable = false, precision = 12, scale = 2)
    private BigDecimal giaMoiDem;

    @Column(name = "trang_thai", nullable = false, length = 50)
    private String trangThai;

    @Column(name = "mo_ta", length = 500)
    private String moTa;

//    @Column(name = "suc_chua")
//    private Integer sucChua;

    @Column(name = "hoat_dong", nullable = false)
    private boolean hoatDong;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao;

    @Column(name = "ngay_cap_nhat")
    private LocalDateTime ngayCapNhat;


}

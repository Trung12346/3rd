package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "khuyen_mai")
public class KhuyenMai {
    @Id
    @Column(name = "ma_khuyen_mai")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;

    @ManyToOne
    @JoinColumn(name = "ma_nguoi_tao")
    public NguoiDung n;

    @OneToMany(mappedBy = "km",cascade = CascadeType.ALL)
    private List<DatPhong> dp;

    @Column(name = "code_khuyen_mai")
    @NotBlank(message = "code khuyến mãi không được để trống")
    public String promoCode;

    @Column(name = "mo_ta")
    @NotBlank(message = "mô tả không được để trống")
    public String moTa;

    @Column(name = "loai_giam")
    @NotBlank(message = "loại giảm không được để trống ")
    public String loaiGiam;

    @Column(name = "gia_tri_giam",precision = 12,scale = 2)
    @NotNull(message = "giá trị giảm không được để trống")
    public BigDecimal giatriGiam;

    @Column(name = "ngay_bat_dau")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate ngayBatDau;

    @Column(name = "ngay_ket_thuc")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate ngayKetThuc;

    @Column(name = "hoat_dong")
    @NotNull(message = "hoạt động không được để trống")
    public boolean hoatDong;

    @Column(name = "ngay_tao",updatable = false,insertable = false)
    public LocalDateTime ngayTao;



}

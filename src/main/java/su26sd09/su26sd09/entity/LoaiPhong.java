package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "loai_phong")
public class LoaiPhong {
    @Id
    @Column(name = "ma_loai_phong")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;

    @Column(name = "ten_loai")
    @NotBlank(message = "tên loại không được để trống")
    public String tenLoai;

    @Column(name = "suc_chua_toi_da")
    @NotNull(message = "sức chứa không được để trống")
    public int sucChuaToiDa;

    @Column(name = "gia_co_ban")
    @NotNull(message = "giá cơ bản không được để trống")
    public BigDecimal giaCoBan;

    @Column(name = "mo_ta")
    @NotBlank(message = "mô tả không được để trống")
    public String mota;

}
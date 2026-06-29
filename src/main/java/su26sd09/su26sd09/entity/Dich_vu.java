package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dich_vu")
public class Dich_vu {
    @Id
    @Column(name = "ma_dich_vu")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ten_dich_vu")
    private String ten_dich_vu;

    @Column(name = "gia")
    private BigDecimal gia;

    @Column(name = "don_vi")
    private String donVi;

    @Column(name = "hoat_dong")
    private Boolean hoat_dong;

    @OneToMany(mappedBy = "dv",cascade = CascadeType.ALL)
    private List<Chi_tiet_dich_vu> chiTietDichVus;
}

package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "admin")
public class Admin {
    @Id
    @Column(name = "ma_admin")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ma_admin;

    @Column(name = "ho_ten")
    private String hoten;

    @Column(name = "email")
    private String email;

    @Column(name = "mat_khau_hash")
    private String mat_khau_hash;

    @Column(name = "trang_thai")
    private boolean trang_thai;

    @Column(name = "ngay_tao")
    private LocalDateTime ngayTao;

    @OneToMany(mappedBy = "admin",cascade = CascadeType.ALL)
    private List<KhuyenMai> kmList;

    @ManyToOne
    @JoinColumn( name = "ma_vai_tro",referencedColumnName = "ma_vai_tro")
    private VaiTro vaiTro;
}

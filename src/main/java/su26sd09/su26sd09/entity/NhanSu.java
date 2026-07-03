package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "nhan_su")
public class NhanSu {
    @Id
    @Column(name = "ma_nhan_su")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;



    @Column(name = "ho_ten")
    private String hoten;

    @Column(name = "dia_chi")
    private String dia_chi;

    @Column(name = "email")
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",message = "email không đúng định dạng")
    private String email;

    @Column(name = "so_dien_thoai")
    @Pattern(regexp = "^(03|05|07|08|09)\\d{8}$",message = "số điện thoại không đúng định dạng")
    private String sdt;

    @Column(name = "mat_khau_hash")
    private String mat_khau_hash;

    @Column(name = "trang_thai")
    private boolean trang_thai;

    @OneToMany(mappedBy = "nv",cascade = CascadeType.ALL)
    private List<ThanhToan> thanhToans;

    @ManyToOne
    @JoinColumn(name = "ma_vai_tro",referencedColumnName = "ma_vai_tro")
    @NotNull(message = "vai trò không được để trống")
    private VaiTro vaitro;

    @Column(name = "bo_phan")
    @NotBlank(message = "bộ phận không được để trống")
    public String boPhan;

    @Column(name = "ngay_sinh")
    @NotNull(message = "ngày sinh không được để trống")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public LocalDate ngaySinh;

    @Column(name = "ma_cccd")
    @NotBlank(message = "mã căn cước công dân không được để trống")
    @Pattern(regexp = "^[0-9]{3}[0-9]{1}[0-9]{2}[0-9]{6}$", message = "Mã căn cước công dân sai định dạng")
    public String maCCCD;



}

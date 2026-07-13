package su26sd09.su26sd09.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Entity
@Getter
@Setter
@Table(name = "khach_hang")
public class KhachHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_khach_hang")
    private Integer ma_khach_hang;



    @Column(name = "ho_ten", nullable = false, length = 150)
    @NotBlank(message = "họ tên không được trống")
    private String hoTen;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    @NotBlank(message = "email không được trống")
    private String email;

    @Column(name = "mat_khau_hash", nullable = false, length = 255)
    @NotBlank(message = "mật khẩu không được để trống")
    private String matKhau_hash;

    @Column(name = "so_dien_thoai", length = 20, unique = true)
//    @NotBlank(message = "số điện thoại không được trống")
    @Pattern(regexp = "^(?:\\+84|0)(3|5|7|8|9)\\d{8}$", message = "số điện thoại sai định dạng")
    private String soDienThoai;

    @Column(name = "dia_chi", length = 300)
    private String diaChi;

    @Column(name = "trang_thai")
    @NotNull(message = "trạng thái không được trống")
    private boolean trangThai = false;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "ngay_tao", insertable = false, updatable = false)
    private LocalDateTime ngayTao;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "ngay_cap_nhat",insertable = false)
    private LocalDateTime ngayCapNhat;



    @ManyToOne
    @JoinColumn(name = "ma_vai_tro",referencedColumnName = "ma_vai_tro")
    @NotNull(message = "vai trò không được để trống")
    private VaiTro vaiTro;
}

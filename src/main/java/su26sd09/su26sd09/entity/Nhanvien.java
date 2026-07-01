package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "nhan_vien")
public class Nhanvien {
    @Id
    @Column(name = "ma_nhan_vien")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;

    @ManyToOne
    @JoinColumn(name = "ma_nguoi_dung",referencedColumnName = "ma_nguoi_dung")
    @NotNull(message = "vui lòng chọn tài khoản tương ứng")
    @Valid
    public NguoiDung n;

    @OneToMany(mappedBy = "nv",cascade = CascadeType.ALL)
    private List<ThanhToan> thanhToans;

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

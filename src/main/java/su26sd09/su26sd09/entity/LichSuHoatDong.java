package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Nhat ky hoat dong (audit log) cua nhan su trong he thong.
 * Moi dong ghi lai: ai (ma_nhan_su) da lam gi (loai_hanh_dong) tren doi tuong nao
 * (doi_tuong + ma_doi_tuong) va luc nao (thoi_gian).
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "lich_su_hoat_dong")
public class LichSuHoatDong {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ma_nhan_su", referencedColumnName = "ma_nhan_su")
    @NotNull(message = "nhân sự thực hiện không được để trống")
    private NhanSu nhanSu;

    @Column(name = "loai_hanh_dong", length = 50)
    @NotBlank(message = "loại hành động không được để trống")
    private String loaiHanhDong; // CHECK_IN, CHECK_OUT, HOAN_TIEN, THU_TIEN, ...

    @Column(name = "doi_tuong", length = 50)
    private String doiTuong; // "DatPhong", "HoaDon", "ThanhToan", ...

    @Column(name = "ma_doi_tuong")
    private Integer maDoiTuong;

    @Column(name = "thoi_gian")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @NotNull(message = "thời gian không được để trống")
    private LocalDateTime thoiGian;

    @Column(name = "ghi_chu", columnDefinition = "NVARCHAR(MAX)")
    private String ghiChu;
}

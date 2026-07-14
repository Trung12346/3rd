package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "dich_vu")
public class Dich_vu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dich_vu")
    private Integer id;

    @Column(name = "ten_dich_vu", nullable = false, length = 255)
    private String ten_dich_vu;

    @Column(name = "gia", nullable = false, precision = 18, scale = 2)
    private BigDecimal gia;

    @Column(name = "don_vi", length = 50)
    private String donVi;

    @Column(name = "hoat_dong", nullable = false)
    private boolean hoatDong = true;

    @Column(name = "loai_dich_vu", length = 50)
    private String loaiDv;

    @Column(name = "gio_bat_dau")
    private LocalTime gioBatDau;   // NULL = không giới hạn giờ (phục vụ cả ngày)

    @Column(name = "gio_ket_thuc")
    private LocalTime gioKetThuc;  // NULL = không giới hạn giờ

    public Dich_vu() {
    }

    // ===== Getters & Setters =====

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTen_dich_vu() {
        return ten_dich_vu;
    }

    public void setTen_dich_vu(String ten_dich_vu) {
        this.ten_dich_vu = ten_dich_vu;
    }

    public BigDecimal getGia() {
        return gia;
    }

    public void setGia(BigDecimal gia) {
        this.gia = gia;
    }

    public String getDonVi() {
        return donVi;
    }

    public void setDonVi(String donVi) {
        this.donVi = donVi;
    }

    public boolean isHoatDong() {
        return hoatDong;
    }

    public void setHoatDong(boolean hoatDong) {
        this.hoatDong = hoatDong;
    }

    public String getLoaiDv() {
        return loaiDv;
    }

    public void setLoaiDv(String loaiDv) {
        this.loaiDv = loaiDv;
    }

    public LocalTime getGioBatDau() {
        return gioBatDau;
    }

    public void setGioBatDau(LocalTime gioBatDau) {
        this.gioBatDau = gioBatDau;
    }

    public LocalTime getGioKetThuc() {
        return gioKetThuc;
    }

    public void setGioKetThuc(LocalTime gioKetThuc) {
        this.gioKetThuc = gioKetThuc;
    }
}
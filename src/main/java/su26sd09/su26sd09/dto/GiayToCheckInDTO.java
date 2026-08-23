package su26sd09.su26sd09.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Du lieu 1 "Document box" (giay to tuy than) duoc nhap trong modal
 * "Them giay to" truoc khi thuc su nhan phong o So do phong.
 * Moi phan tu gan voi 1 phong (chiTietId = ma_chi_tiet_dat_phong).
 */
@Getter
@Setter
public class GiayToCheckInDTO {
    private Integer chiTietId;
    private Boolean coDaiDien;
    private String loaiGiayTo; // "CCCD" hoac "Ho chieu"
    private String hoTen;
    private String soDinhDanh;
    private LocalDate ngaySinh;
    private String gioiTinh;
    private String quocTich;
    private String queQuan;
    private String noiThuongTru;
    private String noiCuTru;
    private String noiTamTru;
    private String noiLuuTru;
    private LocalDate giaTriDen;
    private LocalDate ngayCap;
    private String quocGiaCapPhat;
}

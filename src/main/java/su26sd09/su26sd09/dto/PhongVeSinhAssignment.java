package su26sd09.su26sd09.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bản ghi phân công dọn phòng, dùng làm cache (được engine phân công tự động
 * đọc/ghi ra file JSON ở project root). Không map vào bảng CSDL nào — đây chỉ
 * là trạng thái tạm thời trong lúc phòng đang ở trạng thái "Dang don", sẽ bị
 * xoá khỏi cache khi lễ tân xác nhận phòng sạch (phòng chuyển về "Trong").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhongVeSinhAssignment {

    public static final String DA_GAN = "DA_GAN";       // vừa được engine gán, đang chờ dọn
    public static final String DA_UPLOAD = "DA_UPLOAD"; // nhân viên đã upload ảnh, chờ lễ tân xác nhận

    private int maPhong;
    private String soPhong;
    private int maNhanVien;
    private String tenNhanVien;
    private String trangThai;      // DA_GAN | DA_UPLOAD
    private String duongDanAnh;    // tên file ảnh trong media/ve-sinh, null nếu chưa upload
    private String thoiGianGan;    // ISO-8601
    private String thoiGianUpload; // ISO-8601, null nếu chưa upload

    public PhongVeSinhAssignment() {
    }

    public int getMaPhong() { return maPhong; }
    public void setMaPhong(int maPhong) { this.maPhong = maPhong; }

    public String getSoPhong() { return soPhong; }
    public void setSoPhong(String soPhong) { this.soPhong = soPhong; }

    public int getMaNhanVien() { return maNhanVien; }
    public void setMaNhanVien(int maNhanVien) { this.maNhanVien = maNhanVien; }

    public String getTenNhanVien() { return tenNhanVien; }
    public void setTenNhanVien(String tenNhanVien) { this.tenNhanVien = tenNhanVien; }

    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }

    public String getDuongDanAnh() { return duongDanAnh; }
    public void setDuongDanAnh(String duongDanAnh) { this.duongDanAnh = duongDanAnh; }

    public String getThoiGianGan() { return thoiGianGan; }
    public void setThoiGianGan(String thoiGianGan) { this.thoiGianGan = thoiGianGan; }

    public String getThoiGianUpload() { return thoiGianUpload; }
    public void setThoiGianUpload(String thoiGianUpload) { this.thoiGianUpload = thoiGianUpload; }
}

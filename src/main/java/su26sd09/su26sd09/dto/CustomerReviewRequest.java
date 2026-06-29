package su26sd09.su26sd09.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomerReviewRequest {

    private Integer maDatPhong;

    @Min(value = 1, message = "Vui lòng chọn từ 1 đến 5 sao")
    @Max(value = 5, message = "Vui lòng chọn từ 1 đến 5 sao")
    private int diemDanhGia;

    @NotBlank(message = "Nội dung đánh giá không được để trống")
    @Size(max = 1000, message = "Nội dung đánh giá tối đa 1000 ký tự")
    private String noiDung;

    public Integer getMaDatPhong() {
        return maDatPhong;
    }

    public void setMaDatPhong(Integer maDatPhong) {
        this.maDatPhong = maDatPhong;
    }

    public int getDiemDanhGia() {
        return diemDanhGia;
    }

    public void setDiemDanhGia(int diemDanhGia) {
        this.diemDanhGia = diemDanhGia;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public void setNoiDung(String noiDung) {
        this.noiDung = noiDung;
    }
}

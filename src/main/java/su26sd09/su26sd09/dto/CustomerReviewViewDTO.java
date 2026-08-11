package su26sd09.su26sd09.dto;

import su26sd09.su26sd09.entity.DanhGia;

import java.time.format.DateTimeFormatter;

public class CustomerReviewViewDTO {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Integer id;
    private final String tenNguoiDung;
    private final int diemDanhGia;
    private final String noiDung;
    private final String ngayTao;
    private final String phanHoi;

    public CustomerReviewViewDTO(Integer id, String tenNguoiDung, int diemDanhGia, String noiDung, String ngayTao, String phanHoi) {
        this.id = id;
        this.tenNguoiDung = tenNguoiDung;
        this.diemDanhGia = diemDanhGia;
        this.noiDung = noiDung;
        this.ngayTao = ngayTao;
        this.phanHoi = phanHoi;
    }

    public static CustomerReviewViewDTO fromEntity(DanhGia danhGia) {
        String tenNguoiDung = "Khách hàng";
        if (danhGia.getN() != null && danhGia.getN().getHoTen() != null && !danhGia.getN().getHoTen().isBlank()) {
            tenNguoiDung = danhGia.getN().getHoTen();
        }

        String ngayTao = "";
        if (danhGia.getNgayTao() != null) {
            ngayTao = danhGia.getNgayTao().format(DATE_FORMAT);
        }

        return new CustomerReviewViewDTO(
                danhGia.getId(),
                tenNguoiDung,
                danhGia.getDiemDanhGia(),
                danhGia.getNoiDung(),
                ngayTao,
                danhGia.getPhanHoi()
        );
    }

    public Integer getId() {
        return id;
    }

    public String getTenNguoiDung() {
        return tenNguoiDung;
    }

    public int getDiemDanhGia() {
        return diemDanhGia;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public String getNgayTao() {
        return ngayTao;
    }

    public String getPhanHoi() {
        return phanHoi;
    }
}

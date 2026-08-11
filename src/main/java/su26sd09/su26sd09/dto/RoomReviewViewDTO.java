package su26sd09.su26sd09.dto;

import su26sd09.su26sd09.entity.DanhGia;

import java.time.format.DateTimeFormatter;

public class RoomReviewViewDTO {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String ROOM_MARKER_PATTERN = "^\\[ROOM:\\d+\\]\\s*";

    private final Integer id;
    private final Integer maPhong;
    private final Integer maDatPhong;
    private final String tenNguoiDung;
    private final int diemDanhGia;
    private final String noiDung;
    private final String phanHoi;
    private final String ngayTao;

    public RoomReviewViewDTO(Integer id,
                             Integer maPhong,
                             Integer maDatPhong,
                             String tenNguoiDung,
                             int diemDanhGia,
                             String noiDung,
                             String phanHoi,
                             String ngayTao) {
        this.id = id;
        this.maPhong = maPhong;
        this.maDatPhong = maDatPhong;
        this.tenNguoiDung = tenNguoiDung;
        this.diemDanhGia = diemDanhGia;
        this.noiDung = noiDung;
        this.phanHoi = phanHoi;
        this.ngayTao = ngayTao;
    }

    public static RoomReviewViewDTO fromEntity(DanhGia danhGia, Integer maPhong) {
        String tenNguoiDung = "Khách hàng";
        if (danhGia.getN() != null && danhGia.getN().getHoTen() != null && !danhGia.getN().getHoTen().isBlank()) {
            tenNguoiDung = danhGia.getN().getHoTen();
        }

        String ngayTao = "";
        if (danhGia.getNgayTao() != null) {
            ngayTao = danhGia.getNgayTao().format(DATE_FORMAT);
        }

        boolean hasBooking = danhGia.getD() != null;
        Integer maDatPhong = hasBooking ? danhGia.getD().getId() : null;

        return new RoomReviewViewDTO(
                danhGia.getId(),
                maPhong,
                maDatPhong,
                tenNguoiDung,
                danhGia.getDiemDanhGia(),
                cleanRoomMarker(danhGia.getNoiDung(), hasBooking, maPhong),
                danhGia.getPhanHoi(),
                ngayTao
        );
    }

    private static String cleanRoomMarker(String noiDung, boolean hasBooking, Integer maPhong) {
        if (noiDung == null) {
            return null;
        }
        if (hasBooking) {
            return noiDung;
        }

        String trimmedContent = noiDung.trim();
        if (maPhong != null && trimmedContent.equals(roomMarker(maPhong))) {
            return trimmedContent;
        }

        return noiDung.replaceFirst(ROOM_MARKER_PATTERN, "");
    }

    private static String roomMarker(Integer maPhong) {
        return "[ROOM:" + maPhong + "]";
    }

    public Integer getId() {
        return id;
    }

    public Integer getMaPhong() {
        return maPhong;
    }

    public Integer getMaDatPhong() {
        return maDatPhong;
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

    public String getPhanHoi() {
        return phanHoi;
    }

    public String getNgayTao() {
        return ngayTao;
    }
}

package su26sd09.su26sd09.dto;

public class KetQuaHuyDonDTO {
    private String thongBao;
    private Integer hoaDonId;      // null nếu không phát sinh hóa đơn/hoàn tiền
    private boolean canHoanTien;   // true nếu có số tiền cần hoàn, cần điều hướng sang trang xử lý

    public KetQuaHuyDonDTO(String thongBao, Integer hoaDonId, boolean canHoanTien) {
        this.thongBao = thongBao;
        this.hoaDonId = hoaDonId;
        this.canHoanTien = canHoanTien;
    }

    public String getThongBao() { return thongBao; }
    public Integer getHoaDonId() { return hoaDonId; }
    public boolean isCanHoanTien() { return canHoanTien; }
}
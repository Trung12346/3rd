package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 1 su kien tren lich = 1 phong trong 1 don dat phong (ChiTietDatPhong),
 * duoc "trai" theo khoang [checkIn, checkOut) tren luoi thang giong Google Calendar.
 *
 * DTO nay cung mang toan bo thong tin can thiet de hien thi panel chi tiet
 * (ben phai man hinh) khi nhan vien chon 1 dat phong tren lich, nen ngoai
 * cac truong "vi tri/thoi gian" con co gia phong, phu phi, yeu cau them,
 * khuyen mai va danh sach dich vu di kem.
 */
@AllArgsConstructor
@Getter
public class LichPhongEventDTO {
    // --- dinh danh & vi tri tren luoi ---
    private int chiTietId;
    private int datPhongId;
    private int maPhong;
    private String soPhong;
    private int soTang;
    private String moTaPhong;

    // --- thoi gian & trang thai ---
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private String trangThai;

    // --- khach hang ---
    private String hoTen;
    private String sdt;
    private String email;
    private String maCccd;
    private int soNguoiLon;
    private int soTreEm;
    private String maTraCuu;

    // --- gia & tai chinh cua rieng phong nay ---
    private BigDecimal giaMoiDem;
    private BigDecimal giaKhiDat;
    private BigDecimal phuPhi;

    // --- thong tin don dat phong ---
    private LocalDateTime ngayTaoDon;
    private String yeuCauThem;

    // --- khuyen mai (co the null neu khong ap dung) ---
    private String khuyenMaiCode;
    private String khuyenMaiMoTa;
    private String khuyenMaiLoaiGiam;
    private BigDecimal khuyenMaiGiaTriGiam;

    // --- dich vu da su dung trong don (dung chung cho ca don, khong rieng phong nay) ---
    private List<LichPhongDichVuDTO> dichVu;
    private BigDecimal tongTienDichVu;
}

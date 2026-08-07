package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 1 su kien tren lich = 1 phong trong 1 don dat phong (ChiTietDatPhong),
 * duoc "trai" theo khoang [checkIn, checkOut) tren luoi thang giong Google Calendar.
 */
@AllArgsConstructor
@Getter
public class LichPhongEventDTO {
    private int chiTietId;
    private int datPhongId;
    private int maPhong;
    private String soPhong;
    private int soTang;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private String trangThai;
    private String hoTen;
    private String sdt;
    private String email;
    private int soNguoiLon;
    private int soTreEm;
    private String maTraCuu;
}

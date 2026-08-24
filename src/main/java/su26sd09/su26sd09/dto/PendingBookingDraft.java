package su26sd09.su26sd09.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ban nhap" cua 1 luot dat phong theo LOAI (tu /loai-phong/{id} hoac
 * /loai-phong/tim-kiem) TRUOC KHI khach bam "Hoan tat dat phong".
 *
 * KHONG co dong nao trong bang dat_phong duoc tao cho toi khi khach bam
 * "Hoan tat dat phong" (PhongController#SaveXacThucThongTin). Truoc do,
 * toan bo thong tin khach da nhap (ngay nhan/tra, so nguoi lon/tre em,
 * ma_cccd, dich vu bo sung da chon, khuyen mai) duoc "forward" qua cac
 * buoc (/phong/dat-phong/xac-nhan/{token} -> /phong/dat-phong/thong-tin-khach/{token})
 * bang cach luu trong session (xem PendingBookingService), KHONG dung DB,
 * de tranh tao "don rac" (khong co thong tin lien he) giu cho phong truoc
 * khach hang khac.
 */
public class PendingBookingDraft implements Serializable {

    private int loaiPhongId;
    private int soLuong = 1;
    private LocalDateTime ngayNhan;
    private LocalDateTime ngayTra;
    private int nguoiLon;
    private int treEm;
    private String mucGia;
    private String checkOutTime;
    private String maCccd;

    /** Dich vu bo sung da chon o buoc /phong/dat-phong/xac-nhan/{token}. */
    private List<Integer> dichVuIds;
    private Map<Integer, String> soLuongDichVu = new HashMap<>();
    private Map<Integer, String> ngaySuDungDichVu = new HashMap<>();
    private Integer maKhuyenMai;

    /**
     * Thong tin lien he - CHUA co gi cho toi khi khach den buoc
     * /phong/dat-phong/thong-tin-khach/{token} va go. Duoc set tam vao draft
     * (truoc khi tao that o SaveXacThucThongTin) chi de trang preview
     * (buildTransientDatPhong) hien thi lai dung du lieu khach vua nhap
     * neu form bi validate loi va can render lai.
     */
    private String hoten;
    private String email;
    private String sdt;
    private String yeuCauThem;

    public int getLoaiPhongId() { return loaiPhongId; }
    public void setLoaiPhongId(int loaiPhongId) { this.loaiPhongId = loaiPhongId; }

    public int getSoLuong() { return soLuong; }
    public void setSoLuong(int soLuong) { this.soLuong = soLuong; }

    public LocalDateTime getNgayNhan() { return ngayNhan; }
    public void setNgayNhan(LocalDateTime ngayNhan) { this.ngayNhan = ngayNhan; }

    public LocalDateTime getNgayTra() { return ngayTra; }
    public void setNgayTra(LocalDateTime ngayTra) { this.ngayTra = ngayTra; }

    public int getNguoiLon() { return nguoiLon; }
    public void setNguoiLon(int nguoiLon) { this.nguoiLon = nguoiLon; }

    public int getTreEm() { return treEm; }
    public void setTreEm(int treEm) { this.treEm = treEm; }

    public String getMucGia() { return mucGia; }
    public void setMucGia(String mucGia) { this.mucGia = mucGia; }

    public String getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(String checkOutTime) { this.checkOutTime = checkOutTime; }

    public String getMaCccd() { return maCccd; }
    public void setMaCccd(String maCccd) { this.maCccd = maCccd; }

    public List<Integer> getDichVuIds() { return dichVuIds; }
    public void setDichVuIds(List<Integer> dichVuIds) { this.dichVuIds = dichVuIds; }

    public Map<Integer, String> getSoLuongDichVu() { return soLuongDichVu; }
    public void setSoLuongDichVu(Map<Integer, String> soLuongDichVu) { this.soLuongDichVu = soLuongDichVu; }

    public Map<Integer, String> getNgaySuDungDichVu() { return ngaySuDungDichVu; }
    public void setNgaySuDungDichVu(Map<Integer, String> ngaySuDungDichVu) { this.ngaySuDungDichVu = ngaySuDungDichVu; }

    public Integer getMaKhuyenMai() { return maKhuyenMai; }
    public void setMaKhuyenMai(Integer maKhuyenMai) { this.maKhuyenMai = maKhuyenMai; }

    public String getHoten() { return hoten; }
    public void setHoten(String hoten) { this.hoten = hoten; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSdt() { return sdt; }
    public void setSdt(String sdt) { this.sdt = sdt; }

    public String getYeuCauThem() { return yeuCauThem; }
    public void setYeuCauThem(String yeuCauThem) { this.yeuCauThem = yeuCauThem; }
}

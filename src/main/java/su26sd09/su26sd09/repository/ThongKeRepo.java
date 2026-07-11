package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.HoaDon;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ThongKeRepo extends JpaRepository<HoaDon, Integer> {

    @Query(value = """
            SELECT SUM(tong_tien) FROM hoa_don 
            WHERE (:start IS NULL OR ngay_xuat >= :start) 
            AND (:end IS NULL OR ngay_xuat <= :end)
            """, nativeQuery = true)
    public Double getTotalRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
            SELECT COUNT(ma_hoa_don) FROM hoa_don 
            WHERE (:start IS NULL OR ngay_xuat >= :start) 
            AND (:end IS NULL OR ngay_xuat <= :end)
            """, nativeQuery = true)
    public Integer getTotalInvoice(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
            SELECT AVG(tong_tien) FROM hoa_don 
            WHERE (:start IS NULL OR ngay_xuat >= :start) 
            AND (:end IS NULL OR ngay_xuat <= :end)
            """, nativeQuery = true)
    public Double getAvgRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
                SELECT
                COALESCE(SUM(
                    DATEDIFF(
                        DAY, GREATEST(ngay_nhan_phong, :start),
                        LEAST(ngay_tra_phong, :end)
                    )
                ), 0) AS room_nights_sold
            FROM dat_phong WHERE trang_thai <> 'Da huy'
            AND ngay_nhan_phong < :end
            AND ngay_tra_phong > :start""", nativeQuery = true)
    public Integer getOccupancy(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(ma_phong) * DATEDIFF(DAY, :start, :end) FROM phong", nativeQuery = true)
    public Double getTotalRoom(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT label,
                   SUM(tong_tien) AS revenue
            FROM (
                SELECT FORMAT(ngay_xuat, :pattern, 'en-US') AS label,
                       tong_tien
                FROM hoa_don
                WHERE ngay_xuat >= :start
                  AND ngay_xuat <= :end
            ) t
            GROUP BY label
            ORDER BY label
        """, nativeQuery = true)
    public List<Object[]> sumDoanhThuTheoThoiGian(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end,
                                              @Param("pattern") String pattern);

    @Query(value = """
        SELECT
            lp.ten_loai,
            SUM(c.gia_khi_dat) AS doanh_thu
        FROM
        (SELECT ct.gia_khi_dat, ct.ma_phong FROM chi_tiet_dat_phong ct JOIN dat_phong d ON d.ma_dat_phong = ct.ma_dat_phong WHERE d.ngay_tao >= :start AND d.ngay_tao <= :end) c
        JOIN phong p
            ON c.ma_phong = p.ma_phong
        JOIN loai_phong lp
            ON p.ma_loai_phong = lp.ma_loai_phong
        GROUP BY lp.ten_loai;
        """, nativeQuery = true)
    public List<Object[]> getRevenueByRoomType(@Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    @Query(value = """
        SELECT ten_loai FROM loai_phong
        """, nativeQuery = true)
    public List<String> getTenLoaiPhong();

    // ===== PHAN TICH NANG CAO (deep analysis additions) =====

    /**
     * Row 0: [tien_phong, tien_dich_vu, tien_giam, tien_vat] cong don trong ky, tinh tren hoa_don.
     */
    @Query(value = """
        SELECT
            COALESCE(SUM(tien_phong), 0)    AS doanh_thu_phong,
            COALESCE(SUM(tien_dich_vu), 0)  AS doanh_thu_dich_vu,
            COALESCE(SUM(tien_giam), 0)     AS tien_giam,
            COALESCE(SUM(tien_vat), 0)      AS tien_vat
        FROM hoa_don
        WHERE ngay_xuat >= :start AND ngay_xuat < DATEADD(day, 1, :end)
        """, nativeQuery = true)
    public List<Object[]> getRevenueBreakdown(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Row 0: [tong_dat_phong, so_dat_huy] trong ky, dua tren ngay tao don.
     */
    @Query(value = """
        SELECT
            COUNT(*) AS tong_dat_phong,
            COALESCE(SUM(CASE WHEN trang_thai = N'Da huy' THEN 1 ELSE 0 END), 0) AS so_dat_huy
        FROM dat_phong
        WHERE ngay_tao >= :start AND ngay_tao < DATEADD(day, 1, :end)
        """, nativeQuery = true)
    public List<Object[]> getCancellationStats(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Row 0: [tong_khach, khach_quay_lai] - khach quay lai la khach co > 1 don (chua tinh don da huy) trong ky.
     */
    @Query(value = """
        SELECT
            COUNT(*) AS tong_khach,
            COALESCE(SUM(CASE WHEN cnt > 1 THEN 1 ELSE 0 END), 0) AS khach_quay_lai
        FROM (
            SELECT ma_khach, COUNT(*) AS cnt
            FROM dat_phong
            WHERE ngay_tao >= :start AND ngay_tao < DATEADD(day, 1, :end)
              AND trang_thai <> N'Da huy'
            GROUP BY ma_khach
        ) t
        """, nativeQuery = true)
    public List<Object[]> getRepeatGuestStats(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT tt.phuong_thuc, SUM(tt.so_tien) AS tong_tien
        FROM thanh_toan tt
        JOIN hoa_don hd ON tt.ma_hoa_don = hd.ma_hoa_don
        WHERE tt.trang_thai = N'Thanh cong'
          AND hd.ngay_xuat >= :start AND hd.ngay_xuat < DATEADD(day, 1, :end)
        GROUP BY tt.phuong_thuc
        ORDER BY SUM(tt.so_tien) DESC
        """, nativeQuery = true)
    public List<Object[]> getPaymentMethodBreakdown(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT trang_thai, COUNT(*) AS so_luong
        FROM dat_phong
        WHERE ngay_tao >= :start AND ngay_tao < DATEADD(day, 1, :end)
        GROUP BY trang_thai
        """, nativeQuery = true)
    public List<Object[]> getBookingStatusBreakdown(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT TOP 10
            p.so_phong,
            lp.ten_loai,
            COUNT(ct.ma_chi_tiet)     AS luot_dat,
            SUM(ct.gia_khi_dat)       AS doanh_thu
        FROM chi_tiet_dat_phong ct
        JOIN dat_phong d  ON ct.ma_dat_phong = d.ma_dat_phong
        JOIN phong p      ON ct.ma_phong = p.ma_phong
        JOIN loai_phong lp ON p.ma_loai_phong = lp.ma_loai_phong
        WHERE d.ngay_tao >= :start AND d.ngay_tao < DATEADD(day, 1, :end)
          AND d.trang_thai <> N'Da huy'
        GROUP BY p.so_phong, lp.ten_loai
        ORDER BY SUM(ct.gia_khi_dat) DESC
        """, nativeQuery = true)
    public List<Object[]> getTopPhongDoanhThu(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT COALESCE(SUM(ct.gia_khi_dat), 0)
        FROM chi_tiet_dat_phong ct
        JOIN dat_phong d ON ct.ma_dat_phong = d.ma_dat_phong
        WHERE d.ngay_tao >= :start AND d.ngay_tao < DATEADD(day, 1, :end)
          AND d.trang_thai <> N'Da huy'
        """, nativeQuery = true)
    public Double getTongDoanhThuPhongChiTiet(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT TOP 10
            dv.ten_dich_vu,
            SUM(ct.so_luong)               AS so_luong,
            SUM(ct.so_luong * ct.don_gia)  AS doanh_thu
        FROM chi_tiet_dich_vu ct
        JOIN dich_vu dv  ON ct.ma_dich_vu = dv.ma_dich_vu
        JOIN dat_phong d ON ct.ma_dat_phong = d.ma_dat_phong
        WHERE d.ngay_tao >= :start AND d.ngay_tao < DATEADD(day, 1, :end)
        GROUP BY dv.ten_dich_vu
        ORDER BY SUM(ct.so_luong * ct.don_gia) DESC
        """, nativeQuery = true)
    public List<Object[]> getTopDichVuDoanhThu(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT COALESCE(SUM(ct.so_luong * ct.don_gia), 0)
        FROM chi_tiet_dich_vu ct
        JOIN dat_phong d ON ct.ma_dat_phong = d.ma_dat_phong
        WHERE d.ngay_tao >= :start AND d.ngay_tao < DATEADD(day, 1, :end)
        """, nativeQuery = true)
    public Double getTongDoanhThuDichVuChiTiet(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT TOP 10
            nd.ho_ten,
            nd.email,
            COUNT(DISTINCT hd.ma_hoa_don) AS so_lan_dat,
            SUM(hd.tong_tien)             AS tong_chi_tieu
        FROM hoa_don hd
        JOIN dat_phong d  ON hd.ma_dat_phong = d.ma_dat_phong
        JOIN khach_hang nd ON d.ma_khach = nd.ma_khach_hang
        WHERE hd.ngay_xuat >= :start AND hd.ngay_xuat < DATEADD(day, 1, :end)
        GROUP BY nd.ho_ten, nd.email
        ORDER BY SUM(hd.tong_tien) DESC
        """, nativeQuery = true)
    public List<Object[]> getTopKhachHang(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Doanh thu THUC THU (cash-basis): tien thuc te da thu qua thanh_toan, tinh theo
     * ngay_thanh_toan - khac voi doanh thu ghi nhan tren hoa_don (accrual, tinh theo ngay_xuat).
     * Chi tinh cac giao dich trang_thai = 'Thanh cong'.
     */
    @Query(value = """
        SELECT COALESCE(SUM(so_tien), 0)
        FROM thanh_toan
        WHERE trang_thai = N'Thanh cong'
          AND ngay_thanh_toan >= :start AND ngay_thanh_toan < DATEADD(day, 1, :end)
        """, nativeQuery = true)
    public Double getActualRevenueCollected(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
        SELECT COUNT(*)
        FROM thanh_toan
        WHERE trang_thai = N'Thanh cong'
          AND ngay_thanh_toan >= :start AND ngay_thanh_toan < DATEADD(day, 1, :end)
        """, nativeQuery = true)
    public Integer getSoGiaoDichThanhCong(@Param("start") LocalDate start, @Param("end") LocalDate end);
}




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
}




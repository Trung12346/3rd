package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.Dich_vu;

import java.math.BigDecimal;
import java.util.List;

public interface DichVuRepo extends JpaRepository<Dich_vu,Integer> {

    @Query("SELECT d FROM Dich_vu d WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(d.ten_dich_vu) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:trangThai IS NULL OR :trangThai = '' " +
            "     OR (:trangThai = 'active' AND d.hoat_dong = true) " +
            "     OR (:trangThai = 'inactive' AND (d.hoat_dong = false OR d.hoat_dong IS NULL))) " +
            "AND (:loaiDichVu IS NULL OR :loaiDichVu = '' OR d.loaiDv = :loaiDichVu) " +
            "ORDER BY d.id DESC")
    List<Dich_vu> search(@Param("keyword") String keyword,
                         @Param("trangThai") String trangThai,
                         @Param("loaiDichVu") String loaiDichVu);
    @Query("SELECT COUNT(d) FROM Dich_vu d WHERE d.hoat_dong = true")
    long countActive();

    @Query("SELECT d FROM Dich_vu d WHERE d.hoat_dong = true AND d.loaiDv = :loaiDichVu ORDER BY d.ten_dich_vu ASC")
    List<Dich_vu> findActiveByLoai(@Param("loaiDichVu") String loaiDichVu);

    /** Tìm dịch vụ theo tên + đơn giá (dùng để chống trùng dịch vụ phát sinh giữa các đơn). */
    @Query("SELECT d FROM Dich_vu d WHERE LOWER(d.ten_dich_vu) = LOWER(:ten) AND d.gia = :gia")
    List<Dich_vu> findByTenAndGia(@Param("ten") String ten, @Param("gia") BigDecimal gia);

    /** Lấy map maDichVu -> maDatPhong từ chi_tiet_dich_vu, dùng để hiển thị "Đơn phát sinh" trong dich-vu-list. */
    @Query("SELECT c.dv.id, c.datPhong.id FROM Chi_tiet_dich_vu c " +
            "WHERE c.dv IS NOT NULL AND c.datPhong IS NOT NULL " +
            "ORDER BY c.dv.id, c.datPhong.id")
    List<Object[]> findMaDatPhongTheoDichVu();
}
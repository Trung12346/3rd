package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.HoaDon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface HoaDonRepo extends JpaRepository<HoaDon, Integer> {
    HoaDon findByD_Id(Integer maDatPhong);

    @Query(value = "select h from HoaDon h " +
           "left join h.d d " +
           "left join d.n n " +
           "left join h.k k " +
           "where " +
           "(:maHoaDon is null or h.id = :maHoaDon) and " +
           "(:maDatPhong is null or d.id = :maDatPhong) and " +
           "(:tenKhach is null or n.hoTen like %:tenKhach%) and " +
           "(:maKhuyenMai is null or k.promoCode like %:maKhuyenMai%) and " +
           "(:ngayTu is null or h.ngayXuat >= :ngayTu) and " +
           "(:ngayDen is null or h.ngayXuat <= :ngayDen) and " +
           "(:tongTu is null or h.tongTien >= :tongTu) and " +
           "(:tongDen is null or h.tongTien <= :tongDen) and " +
           "(:trangThai is null or " +
           "  (:trangThai = 'chua' and h.daThanhToan = 0) or " +
           "  (:trangThai = 'mot_phan' and h.daThanhToan > 0 and h.tongTien > h.daThanhToan) or " +
           "  (:trangThai = 'du' and h.daThanhToan > 0 and h.tongTien = h.daThanhToan))",
           countQuery = "select count(h) from HoaDon h " +
           "left join h.d d " +
           "left join d.n n " +
           "left join h.k k " +
           "where " +
           "(:maHoaDon is null or h.id = :maHoaDon) and " +
           "(:maDatPhong is null or d.id = :maDatPhong) and " +
           "(:tenKhach is null or n.hoTen like %:tenKhach%) and " +
           "(:maKhuyenMai is null or k.promoCode like %:maKhuyenMai%) and " +
           "(:ngayTu is null or h.ngayXuat >= :ngayTu) and " +
           "(:ngayDen is null or h.ngayXuat <= :ngayDen) and " +
           "(:tongTu is null or h.tongTien >= :tongTu) and " +
           "(:tongDen is null or h.tongTien <= :tongDen) and " +
           "(:trangThai is null or " +
           "  (:trangThai = 'chua' and h.daThanhToan = 0) or " +
           "  (:trangThai = 'mot_phan' and h.daThanhToan > 0 and h.tongTien > h.daThanhToan) or " +
           "  (:trangThai = 'du' and h.daThanhToan > 0 and h.tongTien = h.daThanhToan))")
    Page<HoaDon> search(
            @Param("maHoaDon") Integer maHoaDon,
            @Param("maDatPhong") Integer maDatPhong,
            @Param("tenKhach") String tenKhach,
            @Param("maKhuyenMai") String maKhuyenMai,
            @Param("ngayTu") LocalDateTime ngayTu,
            @Param("ngayDen") LocalDateTime ngayDen,
            @Param("tongTu") BigDecimal tongTu,
            @Param("tongDen") BigDecimal tongDen,
            @Param("trangThai") String trangThai,
            Pageable pageable);
}

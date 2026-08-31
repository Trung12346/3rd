package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.LoaiPhong;

import java.math.BigDecimal;
import java.util.List;

public interface LoaiPhongRepository extends JpaRepository<LoaiPhong, Integer> {

    List<LoaiPhong> findAllByOrderByTenLoaiAsc();

    /**
     * Tìm kiếm loại phòng theo giá và sức chứa (không có keyword)
     */
    @Query("""
        select lp from LoaiPhong lp
        where (:minGia is null or lp.giaCoBan >= :minGia)
        and (:maxGia is null or lp.giaCoBan <= :maxGia)
        and (:soKhach is null or lp.sucChuaToiDa >= :soKhach)
        order by lp.id desc
    """)
    List<LoaiPhong> searchLoaiPhong(
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach
    );

    /**
     * Tìm kiếm loại phòng theo tên (không phân biệt hoa thường)
     */
    @Query("select l from LoaiPhong l where lower(l.tenLoai) like lower(concat('%', :name, '%'))")
    public List<LoaiPhong> findbyName(@Param("name") String name);

    /**
     * Tìm kiếm loại phòng có phân trang - hỗ trợ keyword, giá, sức chứa
     * Sử dụng lower() để không phân biệt chữ hoa/thường
     */
    @Query("""
        select lp from LoaiPhong lp
        where (:keyword is null or lower(lp.tenLoai) like lower(concat('%', :keyword, '%')))
        and (:minGia is null or lp.giaCoBan >= :minGia)
        and (:maxGia is null or lp.giaCoBan <= :maxGia)
        and (:soKhach is null or lp.sucChuaToiDa >= :soKhach)
        order by lp.id desc
    """)
    Page<LoaiPhong> searchLoaiPhongPaged(
            @Param("keyword") String keyword,
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach,
            Pageable pageable
    );

    /**
     * Tìm kiếm loại phòng có phân trang - SỬ DỤNG NATIVE QUERY
     * Hỗ trợ tìm kiếm không phân biệt dấu (accent-insensitive) trên SQL Server
     * Nếu dùng MySQL, thay COLLATE SQL_Latin1_General_CP1_CI_AI thành COLLATE utf8mb4_unicode_ci
     */
    @Query(value = """
        SELECT * FROM loai_phong lp
        WHERE (:keyword IS NULL OR lp.ten_loai COLLATE SQL_Latin1_General_CP1_CI_AI LIKE CONCAT('%', :keyword, '%'))
        AND (:minGia IS NULL OR lp.gia_co_ban >= :minGia)
        AND (:maxGia IS NULL OR lp.gia_co_ban <= :maxGia)
        AND (:soKhach IS NULL OR lp.suc_chua_toi_da >= :soKhach)
        ORDER BY lp.ma_loai_phong DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
    """, nativeQuery = true)
    List<LoaiPhong> searchLoaiPhongPagedNative(
            @Param("keyword") String keyword,
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Đếm số lượng loại phòng theo điều kiện tìm kiếm - Native Query
     */
    @Query(value = """
        SELECT COUNT(*) FROM loai_phong lp
        WHERE (:keyword IS NULL OR lp.ten_loai COLLATE SQL_Latin1_General_CP1_CI_AI LIKE CONCAT('%', :keyword, '%'))
        AND (:minGia IS NULL OR lp.gia_co_ban >= :minGia)
        AND (:maxGia IS NULL OR lp.gia_co_ban <= :maxGia)
        AND (:soKhach IS NULL OR lp.suc_chua_toi_da >= :soKhach)
    """, nativeQuery = true)
    long countSearchLoaiPhongPagedNative(
            @Param("keyword") String keyword,
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach
    );

    /**
     * Kiểm tra loại phòng có đang được sử dụng bởi phòng nào không
     * Trả về số phòng đang sử dụng loại phòng này
     */
    @Query("SELECT COUNT(p) FROM Phong p WHERE p.loaiPhong.id = :loaiPhongId")
    long countPhongByLoaiPhong(@Param("loaiPhongId") Integer loaiPhongId);
}
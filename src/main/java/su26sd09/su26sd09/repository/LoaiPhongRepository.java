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

    @Query("""
        select lp from LoaiPhong lp
        where (:minGia is null or lp.giaCoBan >= :minGia)
        and (:maxGia is null or lp.giaCoBan <= :maxGia)
        and (:soKhach is null or lp.sucChuaToiDa >= :soKhach)
        order by lp.tenLoai asc
    """)
    List<LoaiPhong> searchLoaiPhong(
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach
    );


    @Query("select l from LoaiPhong l where l.tenLoai like concat('%',:name,'%')")
    public List<LoaiPhong> findbyName(@Param("name") String name);


    @Query("""
        select lp from LoaiPhong lp
        where (:keyword is null or lower(lp.tenLoai) like lower(concat('%', :keyword, '%')))
        and (:minGia is null or lp.giaCoBan >= :minGia)
        and (:maxGia is null or lp.giaCoBan <= :maxGia)
        and (:soKhach is null or lp.sucChuaToiDa >= :soKhach)
        order by lp.tenLoai asc
    """)
    Page<LoaiPhong> searchLoaiPhongPaged(
            @Param("keyword") String keyword,
            @Param("minGia") BigDecimal minGia,
            @Param("maxGia") BigDecimal maxGia,
            @Param("soKhach") Integer soKhach,
            Pageable pageable
    );





}

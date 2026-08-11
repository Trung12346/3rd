package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.ChiTietDatPhong;

import java.time.LocalDateTime;
import java.util.List;

public interface ChiTietDatPhongRepo extends JpaRepository<ChiTietDatPhong,Integer> {
    @Query("select c from ChiTietDatPhong c where c.d.id = :id")
    List<ChiTietDatPhong> findByDatPhongId(@Param("id") int id);

    /**
     * Cac ChiTietDatPhong (= 1 phong trong 1 don) thuoc 1 LoaiPhong, co khoang
     * [ngaydatPhong, ngaytraPhong) giao voi [start, end) va don chua bi huy.
     * Dung de ve luoi lich thang (calendar month view) cho trang quan ly lich.
     */
    @Query("""
        select c from ChiTietDatPhong c
        join c.d d
        join c.p p
        where p.loaiPhong.id = :loaiPhongId
        and d.trangThai <> 'Da huy'
        and d.ngaydatPhong < :end
        and d.ngaytraPhong > :start
        order by d.ngaydatPhong asc
    """)
    List<ChiTietDatPhong> findForCalendar(@Param("loaiPhongId") int loaiPhongId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Transactional
    void deleteByDId(Integer maDatPhong);




    boolean existsByDId(Integer id);
}

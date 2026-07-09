package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.Phong;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DatPhongRepo extends JpaRepository<DatPhong,Integer> {


    @Query("select d from DatPhong d where d.n.ma_khach_hang = :id")
    Page<DatPhong> findByNguoiDung(int id, Pageable pageable);

    List<DatPhong> findByTrangThaiAndNgayTaoBefore(String trangThai, LocalDateTime ngay);


    @Query("select d from DatPhong d where d.n.ma_khach_hang = :id")
    List<DatPhong> FindByNguoiDung(Integer id);

    @Query("select c.p from ChiTietDatPhong c where c.d.id = :maDatPhong")
    List<Phong> findPhongByDatPhongId(@Param("maDatPhong") Integer maDatPhong);

    /**
     * Lấy các đơn DatPhong gần nhất còn liên quan tới 1 phòng:
     * - Da nhan phong: khách đang ở, khoảng ngày sẽ bị gạch chéo khi đặt tiếp.
     * - Da tra phong: phòng vừa trả xong, đặt tiếp phải tuân thủ ràng buộc giờ.
     * Sắp xếp theo ngayTao desc -> lấy đơn mới nhất.
     */
    @Query("""
    select d
    from DatPhong d
    where d.trangThai in (
        'Cho xac nhan',
        'Da xac nhan',
        'Da nhan phong',
        'Da tra phong'
    )
    and exists (
        select 1
        from ChiTietDatPhong c
        where c.d.id = d.id
        and c.p.maPhong = :maPhong
    )
    order by d.ngayTao desc
""")
    List<DatPhong> findRecentBookingsForPhong(@Param("maPhong") int maPhong);

    @Query("""
        select count(d)
        from DatPhong d
        join ChiTietDatPhong c on c.d.id = d.id
        where c.p.maPhong = :id
        and d.trangThai='Cho xac nhan'
""")
    Long findPendingBookingsByPhong(@Param("id") Integer id);

    @Query("""
    select (count(dp) > 0)
    from DatPhong dp
    join ChiTietDatPhong c on c.d.id = dp.id
    where c.p.maPhong = :maPhong
      and dp.id <> :maDatPhong
      and dp.trangThai in ('Cho xac nhan','Da xac nhan','Da nhan phong')
""")
    boolean existsBookingNotCheckout(@Param("maPhong") Integer maPhong,
                                     @Param("maDatPhong") Integer maDatPhong);
    @Query("""
select d
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :maPhong
and d.trangThai = 'Da nhan phong'
order by d.ngaydatPhong desc
""")
    List<DatPhong> findUsingBookings(@Param("maPhong") Integer maPhong);
    @Query("""
select d
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :maPhong
and d.trangThai = 'Da tra phong'
order by d.ngaytraPhong desc
""")
    List<DatPhong> findCheckoutBookings(@Param("maPhong") Integer maPhong);
    @Query("""
select d
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :maPhong
and d.trangThai = 'Da nhan phong'
order by d.ngaydatPhong desc
""")
    List<DatPhong> findCurrentBooking(@Param("maPhong") Integer maPhong);
    @Query("""
select d
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :maPhong
and d.trangThai = 'Da tra phong'
order by d.ngaytraPhong desc
""")
    List<DatPhong> findLatestCheckout(@Param("maPhong") Integer maPhong);
}

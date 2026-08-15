package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhuyenMai;
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
     * Tất cả đơn đặt (chưa bị hủy) của 1 khách hàng cho 1 phòng, mới nhất trước.
     * Dùng để xác định "lượt đặt phòng gần nhất" khi cho phép viết đánh giá.
     */
    /**
     * Tất cả đơn đặt (chưa bị hủy) của 1 khách hàng cho 1 phòng, mới nhất trước.
     * Khớp theo ma_khach (tài khoản) HOẶC theo email khi đơn không gắn tài khoản
     * (vd: đặt lúc chưa đăng nhập / lỗi liên kết tài khoản lúc đặt) — để tránh
     * bỏ sót lượt đặt phòng thực sự thuộc về khách khi xét quyền đánh giá.
     */
    @Query("""
    select d
    from DatPhong d
    join ChiTietDatPhong c on c.d.id = d.id
    left join d.n kh
    where c.p.maPhong = :maPhong
      and d.trangThai <> 'Da huy'
      and (
          kh.ma_khach_hang = :maKhachHang
          or (kh is null and lower(d.email) = lower(:email))
      )
    order by d.ngayTao desc
    """)
    List<DatPhong> findBookingsForCustomerAndRoom(@Param("maPhong") int maPhong,
                                                  @Param("maKhachHang") int maKhachHang,
                                                  @Param("email") String email);

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


    DatPhong findFirstByKmId(Integer kmId);

    // Dung cho tra cuu don cua khach khong co tai khoan
    java.util.Optional<DatPhong> findByMaTraCuu(String maTraCuu);

    boolean existsByMaTraCuu(String maTraCuu);

    /**
     * Danh sách mã phòng đã bị "khóa lịch" (không còn trống) trong khoảng
     * [ngayNhan, ngayTra) do có đơn đặt phòng khác đang giữ chỗ đè lên
     * khoảng ngày này. Chỉ các trạng thái "Cho xac nhan", "Da xac nhan",
     * "Da nhan phong" mới thực sự khóa lịch (khớp logic ở PhongService).
     * Điều kiện chồng lấn khoảng ngày kinh điển: A.start < B.end AND A.end > B.start
     */
    @Query("""
        select distinct c.p.maPhong
        from DatPhong d
        join ChiTietDatPhong c on c.d.id = d.id
        where d.trangThai in ('Cho xac nhan','Da xac nhan','Da nhan phong')
        and d.ngaydatPhong < :ngayTra
        and d.ngaytraPhong > :ngayNhan
    """)
    List<Integer> findMaPhongDaKhoaLichTrongKhoang(@Param("ngayNhan") LocalDateTime ngayNhan,
                                                     @Param("ngayTra") LocalDateTime ngayTra);

    /**
     * MOI (khong thay the / khong sua truy van nao o tren): danh sach cac
     * dot giu cho (booking) dang hieu luc, cham vao 1 PHONG BAT KY thuoc 1
     * LOAI PHONG cu the, chong lan voi khoang [tuNgay, denNgay). Dung cho
     * tinh nang "ngay het phong theo loai" tren calendar cua trang chi tiet
     * loai phong (/loai-phong/{id}) - MOI phong duoc tra ve rieng le (khong
     * gop/dem o day) de PhongService tu tinh so phong con trong theo tung
     * ngay trong khoang.
     *
     * Cung dieu kien khoa lich + cung cong thuc chong lan (A.start < B.end
     * AND A.end > B.start) nhu findMaPhongDaKhoaLichTrongKhoang o tren, chi
     * khac o cho loc them theo loaiPhongId va tra ve ca khoang ngay cua tung
     * dot giu cho (khong chi ma phong) de FE/BE tinh duoc CHINH XAC ngay nao
     * trong thang bi khoa, thay vi coi ca thang la khoa neu co it nhat 1
     * booking cham vao.
     */
    @Query("""
        select c.p.maPhong as maPhong, d.ngaydatPhong as ngayNhan, d.ngaytraPhong as ngayTra
        from DatPhong d
        join ChiTietDatPhong c on c.d.id = d.id
        where d.trangThai in ('Cho xac nhan','Da xac nhan','Da nhan phong')
        and c.p.loaiPhong.id = :loaiPhongId
        and d.ngaydatPhong < :denNgay
        and d.ngaytraPhong > :tuNgay
    """)
    List<su26sd09.su26sd09.dto.LichPhongTheoLoaiProjection> findLichBiKhoaTheoLoaiTrongKhoang(
            @Param("loaiPhongId") int loaiPhongId,
            @Param("tuNgay") LocalDateTime tuNgay,
            @Param("denNgay") LocalDateTime denNgay);

}

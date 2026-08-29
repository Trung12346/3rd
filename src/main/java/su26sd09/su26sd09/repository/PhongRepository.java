package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.Phong;

import java.math.BigDecimal;
import java.util.List;

public interface PhongRepository extends JpaRepository<Phong, Integer> {

    @Query("""
        select p from Phong p
        where p.hoatDong = true
        and (
            :keyword is null or :keyword = ''
            or lower(p.soPhong) like lower(concat('%', :keyword, '%'))
            or lower(p.trangThai) like lower(concat('%', :keyword, '%'))
            or lower(p.loaiPhong.tenLoai) like lower(concat('%', :keyword, '%'))
        )
        order by p.maPhong desc
    """)
    List<Phong> search(@Param("keyword") String keyword);

    @Query("""
        select p from Phong p
        where (:soPhong is null or :soPhong = '' or lower(p.soPhong) like lower(concat('%', :soPhong, '%')))
        and (:loaiPhongId is null or p.loaiPhong.id = :loaiPhongId)
        and (:soTang is null or p.soTang = :soTang)
        and (:trangThai is null or :trangThai = '' or p.trangThai = :trangThai)
        order by p.maPhong desc
    """)
    Page<Phong> searchFiltered(
            @Param("soPhong") String soPhong,
            @Param("loaiPhongId") Integer loaiPhongId,
            @Param("soTang") Integer soTang,
            @Param("trangThai") String trangThai,
            Pageable pageable
    );

    List<Phong> findByTrangThai(String trangThai);


    List<Phong> findByLoaiPhongIdAndHoatDongTrueOrderBySoPhongAsc(int loaiPhongId);

    List<Phong> findByHoatDongTrueOrderBySoPhongAsc();

    long countByLoaiPhongIdAndHoatDongTrueAndTrangThai(int loaiPhongId, String trangThai);

    long countByLoaiPhongIdAndHoatDongTrue(int loaiPhongId);
    @Query("""
select d
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :id
""")
    List<DatPhong> findAllByPhong(@Param("id") Integer id);

    /**
     * Dem so giao dich (dat phong) CHUA HOAN TAT (dang giu cho / dang o) con
     * gan voi phong nay: dung de chan xoa (ngung hoat dong) phong dang co
     * khach dat / o. Cac trang thai "Da tra phong" va "Da huy" duoc coi la
     * DA HOAN TAT nen khong tinh vao day.
     */
    @Query("""
select count(d)
from DatPhong d
join ChiTietDatPhong c on c.d.id = d.id
where c.p.maPhong = :id
and d.trangThai in ('Yeu cau dat phong','Cho xac nhan','Da xac nhan','Da nhan phong')
""")
    long countGiaoDichChuaHoanTatByPhong(@Param("id") Integer id);


    Phong findFirstByloaiPhongId(int id);

    /**
     * Dong bo lai gia/dem cua TAT CA phong thuoc 1 loai phong ve dung gia co
     * ban moi cua loai phong do - dung khi admin cap nhat gia o man hinh
     * "Loai phong" (truoc gio chi Phong moi tao/duoc luu lai moi tu dong lay
     * dung gia, cac phong cu se bi "ket" gia theo lan luu gan nhat). Dung
     * bulk update (@Modifying) thay vi fetch tung Phong roi saveAll de tranh
     * load het danh sach phong (co the rat nhieu) chi de doi 1 cot gia.
     */
    @Modifying
    @Query("update Phong p set p.giaMoiDem = :gia where p.loaiPhong.id = :loaiPhongId")
    int capNhatGiaTheoLoaiPhong(@Param("loaiPhongId") int loaiPhongId, @Param("gia") BigDecimal gia);
}

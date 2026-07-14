package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.LoaiPhong;

import java.util.List;

public interface DanhGiaRepo extends JpaRepository<DanhGia,Integer> {
    @Query("select d from DanhGia d where d.n.ma_khach_hang = :id")
    Page<DanhGia> findByNguoiDung(int id , Pageable pageable);

    @Query("select d from DanhGia d where d.n.ma_khach_hang = :id")
    List<DanhGia> FindByNguoiDung(int id);

    @Query("""
             select d from DanhGia d
             where d.daDuyet = true
             and d.d.id in (
                 select c.d.id from ChiTietDatPhong c
                 where c.p.maPhong = :maPhong
             )
             order by d.ngayTao desc
             """)
    List<DanhGia> findDaDuyetByPhong(@Param("maPhong") int maPhong);

    @Query(value = """
             SELECT COALESCE(AVG(CAST(dg.diem_danh_gia AS float)), 0)
                 FROM danh_gia dg
                 WHERE dg.da_duyet = 1
                   AND dg.ma_dat_phong IN (
                       SELECT ctdp.ma_dat_phong
                       FROM chi_tiet_dat_phong ctdp
                       WHERE ctdp.ma_phong = :maPhong
                   )
             """, nativeQuery = true)
    Double findAverageRatingByRoom(@Param("maPhong") int maPhong);

    @Query(value = """
    SELECT DISTINCT lp.ma_loai_phong, lp.ten_loai, lp.suc_chua_toi_da, lp.gia_co_ban, lp.mo_ta
    FROM (SELECT ma_phong FROM chi_tiet_dat_phong WHERE ma_dat_phong = :id) mp
    JOIN phong p ON p.ma_phong = mp.ma_phong
    JOIN loai_phong lp ON lp.ma_loai_phong = p.ma_loai_phong
    """, nativeQuery = true)
    public LoaiPhong findLoaiPhong(Integer id);

    @Query(value = """
             SELECT dg.ma_dat_phong, dg.ma_danh_gia, dg.ma_nguoi_dung, dg.diem_danh_gia,
                                                                             dg.noi_dung, dg.phan_hoi, dg.da_duyet, dg.ngay_tao
                                                                      FROM (
                                                                          SELECT DISTINCT ctdp.ma_dat_phong
                                                                          FROM (SELECT ma_phong FROM phong WHERE ma_loai_phong = :id) p
                                                                          JOIN chi_tiet_dat_phong ctdp ON ctdp.ma_phong = p.ma_phong
                                                                      ) dp
                                                                      JOIN danh_gia dg ON dp.ma_dat_phong = dg.ma_dat_phong
                                                                      WHERE :noiDung IS NULL OR LOWER(dg.noi_dung) LIKE CONCAT('%', LOWER(:noiDung), '%')
             """, nativeQuery = true )
    public List<DanhGia> findByLoaiPhong(@Param("id") Integer id, @Param("noiDung") String noiDung);
}

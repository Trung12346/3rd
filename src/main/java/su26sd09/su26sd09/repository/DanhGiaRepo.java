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
     @Query("select d from DanhGia d where d.n.maNguoiDung = :id")
    Page<DanhGia> findByNguoiDung(int id , Pageable pageable);

     @Query("select d from DanhGia d where d.n.maNguoiDung = :id")
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

     @Query(value = "SELECT lp.ma_loai_phong, ten_loai, suc_chua_toi_da, gia_co_ban, mo_ta FROM (SELECT p.ma_loai_phong FROM (SELECT ma_phong FROM chi_tiet_dat_phong WHERE ma_dat_phong = :id) AS mp JOIN phong p ON p.ma_phong = mp.ma_phong) AS mlp JOIN loai_phong lp ON lp.ma_loai_phong = mlp.ma_loai_phong", nativeQuery = true)
     public LoaiPhong findLoaiPhong(Integer id);

     @Query(value = """
             SELECT dg.ma_dat_phong, ma_danh_gia, ma_nguoi_dung, diem_danh_gia, noi_dung, phan_hoi, da_duyet, ngay_tao FROM
             (SELECT ctdp.ma_dat_phong FROM (SELECT ma_phong FROM phong WHERE ma_loai_phong = :id AND (noi_dung IS NULL OR LOWER(noi_dung) LIKE CONCAT('%', LOWER(:noiDung), '%'))) p JOIN chi_tiet_dat_phong ctdp ON ctdp.ma_phong = p.ma_phong) dp
             JOIN danh_gia dg ON dp.ma_dat_phong = dg.ma_dat_phong
             """, nativeQuery = true)
     public List<DanhGia> findByLoaiPhong(@Param("id") Integer id, @Param("noiDung") String noiDung);
}

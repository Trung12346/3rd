package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;

import java.math.BigDecimal;
import java.util.List;

public interface ChiTietDichvuRepo extends JpaRepository<Chi_tiet_dich_vu,Integer> {

    public List<Chi_tiet_dich_vu> findByDatPhongId(Integer id);
    @Transactional
    void deleteByDatPhongId(Integer maDatPhong);

    @Query("SELECT DISTINCT ctdv.dv.id FROM Chi_tiet_dich_vu ctdv " +
            "WHERE ctdv.datPhong.trangThai IN :trangThaiList")
    List<Integer> timDichVuIdDangSuDungTheoTrangThaiDon(@Param("trangThaiList") List<String> trangThaiList);

    @Query("SELECT COUNT(ctdv) FROM Chi_tiet_dich_vu ctdv " +
            "WHERE ctdv.dv.id = :dichVuId AND ctdv.datPhong.trangThai IN :trangThaiList")
    long demDichVuDangSuDungTheoTrangThaiDon(@Param("dichVuId") Integer dichVuId,
                                             @Param("trangThaiList") List<String> trangThaiList);

    @Query("SELECT COALESCE(SUM(c.donGia * c.soluong), 0) FROM Chi_tiet_dich_vu c")
    BigDecimal tongTienDichVu();

    @Query("SELECT c.dv.id, SUM(c.soluong) FROM Chi_tiet_dich_vu c WHERE c.dv IS NOT NULL GROUP BY c.dv.id ORDER BY SUM(c.soluong) DESC")
    List<Object[]> thongKeSoLuongTheoDichVu();

}

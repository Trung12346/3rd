package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.Phong;

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

    List<Phong> findByTrangThai(String trangThai);


    List<Phong> findByLoaiPhongIdAndHoatDongTrueOrderBySoPhongAsc(int loaiPhongId);

    List<Phong> findByHoatDongTrueOrderBySoPhongAsc();

    long countByLoaiPhongIdAndHoatDongTrueAndTrangThai(int loaiPhongId, String trangThai);
}

package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.LichSuHoatDong;

import java.time.LocalDateTime;

public interface LichSuHoatDongRepo extends JpaRepository<LichSuHoatDong, Long> {

    @Query(
        value = """
    SELECT l.* FROM lich_su_hoat_dong l
    JOIN nhan_su n ON n.ma_nhan_su = l.ma_nhan_su
    WHERE (:hoTenNv IS NULL OR LOWER(n.ho_ten) LIKE LOWER(N'%' + CAST(:hoTenNv AS NVARCHAR(MAX)) + N'%'))
    AND (:loaiHanhDong IS NULL OR l.loai_hanh_dong = :loaiHanhDong)
    AND (:doiTuong IS NULL OR l.doi_tuong = :doiTuong)
    AND (:maDoiTuong IS NULL OR l.ma_doi_tuong = :maDoiTuong)
    AND (:tuNgay IS NULL OR l.thoi_gian >= :tuNgay)
    AND (:denNgay IS NULL OR l.thoi_gian <= :denNgay)
    ORDER BY l.thoi_gian DESC
        """,
        countQuery = """
    SELECT COUNT(*) FROM lich_su_hoat_dong l
    JOIN nhan_su n ON n.ma_nhan_su = l.ma_nhan_su
    WHERE (:hoTenNv IS NULL OR LOWER(n.ho_ten) LIKE LOWER(N'%' + CAST(:hoTenNv AS NVARCHAR(MAX)) + N'%'))
    AND (:loaiHanhDong IS NULL OR l.loai_hanh_dong = :loaiHanhDong)
    AND (:doiTuong IS NULL OR l.doi_tuong = :doiTuong)
    AND (:maDoiTuong IS NULL OR l.ma_doi_tuong = :maDoiTuong)
    AND (:tuNgay IS NULL OR l.thoi_gian >= :tuNgay)
    AND (:denNgay IS NULL OR l.thoi_gian <= :denNgay)
        """,
        nativeQuery = true)
    Page<LichSuHoatDong> search(
            @Param("hoTenNv") String hoTenNv,
            @Param("loaiHanhDong") String loaiHanhDong,
            @Param("doiTuong") String doiTuong,
            @Param("maDoiTuong") Integer maDoiTuong,
            @Param("tuNgay") LocalDateTime tuNgay,
            @Param("denNgay") LocalDateTime denNgay,
            Pageable pageable
    );

    @Query("select distinct l.loaiHanhDong from LichSuHoatDong l order by l.loaiHanhDong")
    java.util.List<String> findDistinctLoaiHanhDong();

    @Query("select distinct l.doiTuong from LichSuHoatDong l where l.doiTuong is not null order by l.doiTuong")
    java.util.List<String> findDistinctDoiTuong();
}

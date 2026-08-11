package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;
import su26sd09.su26sd09.entity.KhuyenMai;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.PathVariable;
import su26sd09.su26sd09.entity.KhuyenMai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface khuyenMaiRepo extends JpaRepository<KhuyenMai,Integer> {




    @Query("select m from KhuyenMai m where m.promoCode like :name")
    public List<KhuyenMai> findbyPromoCode(@PathVariable("name") String name);



    @Query(value = """
       SELECT * FROM khuyen_mai km
WHERE (:promoCode IS NULL OR LOWER(km.code_khuyen_mai) LIKE LOWER(N'%' + CAST(:promoCode AS NVARCHAR(MAX)) + N'%'))
AND (:moTa IS NULL OR LOWER(km.mo_ta) LIKE LOWER(N'%' + CAST(:moTa AS NVARCHAR(MAX)) + N'%'))
AND (:loaiGiam IS NULL OR LOWER(km.loai_giam) = LOWER(:loaiGiam))
AND (:giaTriGiam IS NULL OR km.gia_tri_giam = :giaTriGiam)
AND (:ngayBatDau IS NULL OR km.ngay_bat_dau = :ngayBatDau)
AND (:ngayKetThuc IS NULL OR km.ngay_ket_thuc = :ngayKetThuc)
AND (:hoatDong IS NULL OR km.hoat_dong = :hoatDong)
""", nativeQuery = true)
    Page<KhuyenMai> search(
            @Param("promoCode") String promoCode,
            @Param("moTa") String moTa,
            @Param("loaiGiam") String loaiGiam,
            @Param("giaTriGiam") BigDecimal giaTriGiam,
            @Param("ngayBatDau") LocalDate ngayBatDau,
            @Param("ngayKetThuc") LocalDate ngayKetThuc,
            @Param("hoatDong") Boolean hoatDong,
            Pageable pageable
    );



    @Modifying
    @Query("UPDATE KhuyenMai k SET k.hoatDong = false " +
            "WHERE k.hoatDong = true AND k.ngayKetThuc < :today")
    void tatKhuyenMaiHetHan(@Param("today") LocalDate today);

    @Modifying
    @Query("UPDATE KhuyenMai k SET k.hoatDong = true " +
            "WHERE k.hoatDong = false AND k.ngayBatDau <= :today AND k.ngayKetThuc >= :today")
    void kichHoatKhuyenMaiDenNgay(@Param("today") LocalDate today);




}

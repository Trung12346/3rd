package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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



    @Query("""

            SELECT km
                         FROM KhuyenMai km
                               WHERE (:promoCode IS NULL OR LOWER(km.promoCode) LIKE LOWER(CONCAT('%', :promoCode, '%')))
                     AND (:moTa IS NULL OR LOWER(km.moTa) LIKE LOWER(CONCAT('%', :moTa, '%')))
               AND (:loaiGiam IS NULL OR LOWER(km.loaiGiam) = LOWER(:loaiGiam))
AND (:giaTriGiam IS NULL OR km.giatriGiam = :giaTriGiam)
                          AND (:ngayBatDau IS NULL OR km.ngayBatDau = :ngayBatDau)
                                                      AND (:ngayKetThuc IS NULL OR km.ngayKetThuc = :ngayKetThuc)
                                                     AND (:hoatDong IS NULL OR km.hoatDong = :hoatDong)
""")
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
}

package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.NhanSu;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NhanVienRepo extends JpaRepository<NhanSu, Integer> {

    @Query("""
    select n
    from NhanSu n
    where n.hoten like concat('%', :name, '%')
""")
    List<NhanSu> findbyName(@Param("name") String name);

    Optional<NhanSu> findByEmail(String email);


    List<NhanSu> findByVaitro_TenVaiTro(String tenVaiTro);

    boolean existsByMaCCCDAndIdNot(String maCCCD, Integer id);

    boolean existsBySdt(String sdt);

    boolean existsByMaCCCD(String maCCCD);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Integer id);

    boolean existsBySdtAndVaitro_TenVaiTroAndIdNot(
            String sdt,
            String tenVaiTro,
            Integer id
    );

    @Query("""
       SELECT CASE
           WHEN FUNCTION('DATEDIFF', YEAR, n.ngaySinh, CURRENT_DATE) >= 18
           THEN true
           ELSE false
       END
       FROM NhanSu n
       WHERE n.ngaySinh = :ngaySinh
       """)
    Boolean CheckAge(@Param("ngaySinh") LocalDate ngaySinh);


    @Query("""
    SELECT n FROM NhanSu n
    WHERE n.vaitro.tenVaiTro = 'ROLE_STAFF'
    AND (:hoTen IS NULL OR LOWER(n.hoten) LIKE LOWER(CONCAT('%', :hoTen, '%')))
    AND (:email IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :email, '%')))
                            AND (:sdt IS NULL OR n.sdt LIKE CONCAT('%', :sdt, '%'))
                                      AND (:maCCCD IS NULL OR n.maCCCD LIKE CONCAT('%', :maCCCD, '%'))
                                          AND (:boPhan IS NULL OR LOWER(n.boPhan) LIKE LOWER(CONCAT('%', :boPhan, '%')))
                              AND (:trangThai IS NULL OR n.trang_thai = :trangThai)
""")
    Page<NhanSu> search(
            @Param("hoTen") String hoTen,
            @Param("email") String email,
            @Param("sdt") String sdt,
            @Param("maCCCD") String maCCCD,
            @Param("boPhan") String boPhan,
            @Param("trangThai") Boolean trangThai,
            Pageable pageable
    );
}
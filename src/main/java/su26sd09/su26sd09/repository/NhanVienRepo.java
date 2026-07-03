package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.Nhanvien;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NhanVienRepo extends JpaRepository<Nhanvien, Integer> {

    @Query("""
        select n
        from Nhanvien n
        where n.hoten like concat('%', :name, '%')
    """)
    List<Nhanvien> findbyName(@Param("name") String name);

    Optional<Nhanvien> findByEmail(String email);


    List<Nhanvien> findByVaitro_TenVaiTro(String tenVaiTro);

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
       FROM Nhanvien n
       WHERE n.ngaySinh = :ngaySinh
       """)
    Boolean CheckAge(@Param("ngaySinh") LocalDate ngaySinh);
}
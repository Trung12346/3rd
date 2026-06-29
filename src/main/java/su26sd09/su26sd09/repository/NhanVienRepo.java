package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.PathVariable;
import su26sd09.su26sd09.entity.Nhanvien;

import java.util.List;

public interface NhanVienRepo extends JpaRepository<Nhanvien,Integer> {

    @Query("select n from Nhanvien n where n.n.hoTen like concat('%',:name,'%') ")
    public List<Nhanvien> findbyName(@PathVariable("name") String name);

    Nhanvien findByN_MaNguoiDung(Integer maNguoiDung);


}

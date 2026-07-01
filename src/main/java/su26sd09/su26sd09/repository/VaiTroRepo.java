package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.VaiTro;

import java.util.List;
import java.util.Optional;

public interface VaiTroRepo extends JpaRepository<VaiTro,Integer> {


    @Query("select v from VaiTro v where v.tenVaiTro like concat('%',:name,'%')")
    public VaiTro findbyname(@Param("name") String name);
}

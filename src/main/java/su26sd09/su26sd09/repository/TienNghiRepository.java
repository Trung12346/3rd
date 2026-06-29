package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.TienNghi;

import java.util.List;

public interface TienNghiRepository extends JpaRepository<TienNghi, Integer> {
    List<TienNghi> findAllByOrderByTenTienNghiAsc();
}

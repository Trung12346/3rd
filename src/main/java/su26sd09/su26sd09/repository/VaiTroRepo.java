package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.VaiTro;

import java.util.Optional;

public interface VaiTroRepo extends JpaRepository<VaiTro,Integer> {
}

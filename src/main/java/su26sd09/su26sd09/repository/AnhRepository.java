package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.Anh;

import java.util.UUID;
@Repository
public interface AnhRepository extends JpaRepository<Anh, UUID> {
}

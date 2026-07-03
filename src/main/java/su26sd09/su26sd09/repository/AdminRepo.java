package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.Admin;

import java.util.Optional;

public interface AdminRepo extends JpaRepository<Admin,Integer> {

    public Optional<Admin> findByEmail(String email);
}

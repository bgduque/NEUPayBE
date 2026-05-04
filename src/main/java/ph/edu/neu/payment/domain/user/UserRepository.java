package ph.edu.neu.payment.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByIdNumber(String idNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdNumber(String idNumber);

    @Query("select u from User u where lower(u.email) like lower(concat('%', :q, '%')) " +
           "or lower(u.fullName) like lower(concat('%', :q, '%')) " +
           "or u.idNumber like concat('%', :q, '%')")
    Page<User> search(String q, Pageable pageable);
}

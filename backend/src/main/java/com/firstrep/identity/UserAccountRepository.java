package com.firstrep.identity;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByPublicId(String publicId);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> lockById(@Param("id") Long id);
    @Query("select u from UserAccount u where lower(u.email) like lower(concat('%', :q, '%'))")
    Page<UserAccount> search(@Param("q") String q, Pageable pageable);
    @Query("select distinct p.code from UserAccount u join u.platformRoles r join r.permissions p where u.id = :id")
    List<String> platformPermissions(@Param("id") Long id);
}

package com.cityledger.cityledger.repository;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByGoogleId(String googleId);
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findByRole(Role role);
}

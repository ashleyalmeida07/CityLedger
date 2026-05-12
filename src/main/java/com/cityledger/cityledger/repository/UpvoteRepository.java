package com.cityledger.cityledger.repository;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.Upvote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UpvoteRepository extends JpaRepository<Upvote, Long> {
    boolean existsByComplaintAndCitizen(Complaint complaint, AppUser citizen);
    Optional<Upvote> findByComplaintAndCitizen(Complaint complaint, AppUser citizen);
    long countByComplaint(Complaint complaint);
}

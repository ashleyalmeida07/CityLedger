package com.cityledger.cityledger.repository;

import com.cityledger.cityledger.model.Comment;
import com.cityledger.cityledger.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByComplaintOrderByCreatedAtAsc(Complaint complaint);
    long countByComplaint(Complaint complaint);
}

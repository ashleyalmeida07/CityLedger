package com.cityledger.cityledger.repository;

import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByCitizen(AppUser citizen);
    List<Complaint> findByCitizenOrderByCreatedAtDesc(AppUser citizen);
    List<Complaint> findByAssignedWorker(AppUser worker);
    List<Complaint> findByStatus(ComplaintStatus status);
    List<Complaint> findAllByOrderByCreatedAtDesc();
    long countByStatus(ComplaintStatus status);

    // Find open complaints within ~500m radius (0.005 degrees ≈ 500m)
    @Query("SELECT c FROM Complaint c WHERE c.status IN ('FILED', 'IN_PROGRESS') " +
           "AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL " +
           "AND ABS(c.latitude - :lat) < :radius AND ABS(c.longitude - :lng) < :radius")
    List<Complaint> findNearbyOpenComplaints(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radius);

    // CityFeed: all complaints within a bounding box, ordered by creation date
    @Query("SELECT c FROM Complaint c WHERE c.latitude IS NOT NULL AND c.longitude IS NOT NULL " +
           "AND c.latitude BETWEEN :latMin AND :latMax AND c.longitude BETWEEN :lonMin AND :lonMax " +
           "ORDER BY c.createdAt DESC")
    List<Complaint> findFeedByBoundingBox(@Param("latMin") double latMin, @Param("latMax") double latMax,
                                          @Param("lonMin") double lonMin, @Param("lonMax") double lonMax);

    // CityFeed: sorted by upvote count
    @Query("SELECT c FROM Complaint c WHERE c.latitude IS NOT NULL AND c.longitude IS NOT NULL " +
           "AND c.latitude BETWEEN :latMin AND :latMax AND c.longitude BETWEEN :lonMin AND :lonMax " +
           "ORDER BY c.upvoteCount DESC, c.createdAt DESC")
    List<Complaint> findFeedByBoundingBoxSortedByUpvotes(@Param("latMin") double latMin, @Param("latMax") double latMax,
                                                          @Param("lonMin") double lonMin, @Param("lonMax") double lonMax);

    // Trending: top 3 most upvoted this week
    @Query("SELECT c FROM Complaint c WHERE c.createdAt >= :since ORDER BY c.upvoteCount DESC")
    List<Complaint> findTrendingThisWeek(@Param("since") LocalDateTime since);

    // Fallback: all complaints (no GPS filter) sorted by creation
    List<Complaint> findTop50ByOrderByCreatedAtDesc();

    // Fallback: all complaints sorted by upvotes
    List<Complaint> findTop50ByOrderByUpvoteCountDescCreatedAtDesc();
}


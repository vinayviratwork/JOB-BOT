package com.vinay.jobbot.repository;

import com.vinay.jobbot.entity.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, Long> {

    Optional<JobListing> findBySourceUrl(String sourceUrl);

    /** Jobs not yet scored */
    List<JobListing> findByMatchScoreIsNull();

    /** High-match jobs sorted descending */
    List<JobListing> findByMatchScoreGreaterThanEqualOrderByMatchScoreDesc(Double minScore);
}

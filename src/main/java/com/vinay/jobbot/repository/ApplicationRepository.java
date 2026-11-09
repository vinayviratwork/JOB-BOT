package com.vinay.jobbot.repository;

import com.vinay.jobbot.entity.Application;
import com.vinay.jobbot.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByStatus(ApplicationStatus status);

    Optional<Application> findByJobId(Long jobId);

    boolean existsByJobId(Long jobId);
}

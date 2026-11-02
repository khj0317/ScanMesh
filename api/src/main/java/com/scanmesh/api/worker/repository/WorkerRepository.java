package com.scanmesh.api.worker.repository;

import com.scanmesh.api.worker.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update Worker w set w.lastSeenAt = :now where w.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now);
}

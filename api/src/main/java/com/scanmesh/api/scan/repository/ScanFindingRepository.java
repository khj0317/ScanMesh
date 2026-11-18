package com.scanmesh.api.scan.repository;

import com.scanmesh.api.scan.entity.ScanFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanFindingRepository extends JpaRepository<ScanFinding, Long> {

    List<ScanFinding> findAllByScanId(Long scanId);
}

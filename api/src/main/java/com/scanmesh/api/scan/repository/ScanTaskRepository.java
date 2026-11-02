package com.scanmesh.api.scan.repository;

import com.scanmesh.api.scan.entity.ScanTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {

    /**
     * 가장 오래 기다린 작업 하나를 잠그고 ID를 돌려준다. 다른 워커가 이미 잠근 행은 기다리지 않고 건너뛰므로(SKIP LOCKED)
     * 워커 여러 대가 동시에 가져가도 같은 작업을 두 번 가져가지 않고 서로 막히지도 않는다.
     */
    @Query(value = """
        select id from scan_tasks
        where status = 'QUEUED' and engine in (:engines)
        order by id
        limit 1
        for update skip locked
        """, nativeQuery = true)
    Optional<Long> lockNextQueued(@Param("engines") Collection<String> engines);

    /** 임대가 끝난 실행 중 작업을 잠근다. 서버가 여러 대여도 같은 작업을 두 번 처리하지 않는다 */
    @Query(value = """
        select id from scan_tasks
        where status = 'RUNNING' and lease_until < :now
        order by lease_until
        limit :limit
        for update skip locked
        """, nativeQuery = true)
    List<Long> lockExpiredLeases(@Param("now") Instant now, @Param("limit") int limit);

    /** 워커의 하트비트·완료 보고와 사용자의 취소가 동시에 같은 작업을 바꾸지 않도록 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ScanTask t join fetch t.scan where t.id = :id")
    Optional<ScanTask> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ScanTask t where t.scan.id = :scanId order by t.id")
    List<ScanTask> findAllByScanIdForUpdate(@Param("scanId") Long scanId);
}

package com.scanmesh.api.scan.repository;

import com.scanmesh.api.scan.entity.Scan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    /** 다른 회원의 점검은 없는 것처럼 보이도록, 대상의 주인까지 조건에 넣는다 */
    @Query("select s from Scan s join fetch s.target t where s.id = :id and t.member.id = :memberId")
    Optional<Scan> findByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    @Query("select s from Scan s where s.target.id = :targetId order by s.id desc limit 20")
    List<Scan> findRecentByTargetId(@Param("targetId") Long targetId);

    /** 끝나지 않은(대기·실행 중인 작업이 남은) 점검이 있는지 */
    @Query("""
        select count(t) > 0 from ScanTask t
        where t.scan.target.id = :targetId
          and t.status in (com.scanmesh.common.scan.TaskStatus.QUEUED, com.scanmesh.common.scan.TaskStatus.RUNNING)
        """)
    boolean existsActiveByTargetId(@Param("targetId") Long targetId);
}

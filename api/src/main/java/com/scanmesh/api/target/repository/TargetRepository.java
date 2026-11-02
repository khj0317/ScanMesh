package com.scanmesh.api.target.repository;

import com.scanmesh.api.target.entity.Target;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TargetRepository extends JpaRepository<Target, Long> {

    List<Target> findAllByMemberIdOrderByIdDesc(Long memberId);

    Optional<Target> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndHost(Long memberId, String host);

    long countByMemberId(Long memberId);

    /**
     * 마지막 시도가 cooldownStart 이전일 때만 이번 시도를 기록한다. 조건 검사와 기록을 한 문장으로 해서,
     * 동시에 여러 번 요청해도 한 번만 통과한다. 행을 잠근 채 외부 요청을 기다리지 않아도 된다.
     *
     * @return 기록했으면 1, 간격이 아직 지나지 않았으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Target t set t.lastVerificationAttemptAt = :now
        where t.id = :id
          and (t.lastVerificationAttemptAt is null or t.lastVerificationAttemptAt <= :cooldownStart)
        """)
    int recordVerificationAttempt(@Param("id") Long id, @Param("now") Instant now, @Param("cooldownStart") Instant cooldownStart);
}

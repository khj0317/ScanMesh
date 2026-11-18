package com.scanmesh.api.scan.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.scan.dto.ScanFindingsResponse;
import com.scanmesh.api.scan.dto.ScanFindingsResponse.Issue;
import com.scanmesh.api.scan.dto.ScanFindingsResponse.Location;
import com.scanmesh.api.scan.entity.Scan;
import com.scanmesh.api.scan.entity.ScanFinding;
import com.scanmesh.api.scan.repository.ScanFindingRepository;
import com.scanmesh.api.scan.repository.ScanRepository;
import com.scanmesh.common.finding.FindingFingerprint;
import com.scanmesh.common.finding.Severity;
import com.scanmesh.common.scan.Engine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 점검 결과 조회. 엔진별로 저장된 항목을 두 번 묶는다.
 * 지문이 같으면(같은 페이지의 같은 문제) 위치 하나로, 검사 종류가 같으면 문제 하나로 접는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindingService {

    /**
     * 묶인 항목을 대표할 엔진의 순서. 자체 규칙은 한국어 제목과 조치 방법을 직접 쓰고,
     * ZAP은 조치 방법을 주지만 Nuclei는 주지 않는 경우가 많다.
     */
    private static final List<Engine> REPRESENTATIVE_ORDER = List.of(Engine.CUSTOM, Engine.ZAP, Engine.NUCLEI);

    private static final Comparator<ScanFinding> REPRESENTATIVE = Comparator
        .comparing(ScanFinding::getSeverity)
        .thenComparing(finding -> REPRESENTATIVE_ORDER.indexOf(finding.getEngine()))
        .thenComparing(ScanFinding::getId);

    private final ScanRepository scanRepository;
    private final ScanFindingRepository findingRepository;

    public ScanFindingsResponse list(Long memberId, Long scanId) {
        Scan scan = scanRepository.findByIdAndMemberId(scanId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        List<ScanFinding> findings = findingRepository.findAllByScanId(scanId);

        List<Issue> issues = groupBy(findings, FindingService::checkOf).values().stream()
            .map(FindingService::issue)
            .sorted(Comparator
                .comparing((Issue issue) -> Severity.valueOf(issue.severity()))
                .thenComparing(Issue::title))
            .toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            summary.put(severity.name(), issues.stream().filter(issue -> issue.severity().equals(severity.name())).count());
        }
        int locationCount = issues.stream().mapToInt(issue -> issue.locations().size()).sum();
        return new ScanFindingsResponse(scan.getId(), scan.status().name(), summary, locationCount, issues);
    }

    private static String checkOf(ScanFinding finding) {
        return FindingFingerprint.checkName(finding.getEngine(), finding.getRuleId(), finding.getParam());
    }

    /** 제목과 조치 방법은 대표 엔진의 값을 쓰고, 비어 있으면 다른 엔진의 값으로 채운다 */
    private static Issue issue(List<ScanFinding> group) {
        List<ScanFinding> ordered = group.stream().sorted(REPRESENTATIVE).toList();
        ScanFinding representative = ordered.getFirst();
        List<Location> locations = groupBy(group, ScanFinding::getFingerprint).values().stream()
            .map(FindingService::location)
            .sorted(Comparator
                .comparing((Location location) -> Severity.valueOf(location.severity()))
                .thenComparing(Location::url)
                .thenComparing(location -> Objects.toString(location.param(), "")))
            .toList();
        return new Issue(
            checkOf(representative),
            representative.getTitle(),
            representative.getSeverity().name(),
            first(ordered, ScanFinding::getCweId),
            first(ordered, ScanFinding::getOwaspCategory),
            engines(group),
            first(ordered, ScanFinding::getRemediation),
            locations
        );
    }

    private static Location location(List<ScanFinding> group) {
        List<ScanFinding> ordered = group.stream().sorted(REPRESENTATIVE).toList();
        ScanFinding representative = ordered.getFirst();
        return new Location(
            representative.getFingerprint(),
            representative.getUrl(),
            first(ordered, ScanFinding::getParam),
            representative.getSeverity().name(),
            engines(group),
            first(ordered, ScanFinding::getEvidence),
            group.size()
        );
    }

    private static List<String> engines(Collection<ScanFinding> group) {
        return Arrays.stream(Engine.values())
            .filter(engine -> group.stream().anyMatch(finding -> finding.getEngine() == engine))
            .map(Engine::name)
            .toList();
    }

    private static Map<String, List<ScanFinding>> groupBy(List<ScanFinding> findings, Function<ScanFinding, String> key) {
        return findings.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static <T> T first(List<ScanFinding> ordered, Function<ScanFinding, T> field) {
        return ordered.stream().map(field).filter(Objects::nonNull).findFirst().orElse(null);
    }
}

package com.example.echo.routineplace.service;

import com.example.echo.common.util.GeoDistanceUtil;
import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.entity.VisitOccurrence;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import com.example.echo.user.entity.UserPreferences;
import com.example.echo.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여러 날에 걸친 방문 이력(VisitOccurrence)에서 반복 방문 패턴을 감지해
 * 루틴 방문 장소 후보(SUGGESTED)를 만들거나, 기존 확정 장소의 요일/시간대 통계를 갱신한다.
 *
 * 사용자가 직접 확정(RoutinePlaceService.confirm)하기 전까지는 대화에 노출되지 않는다
 * (자기결정권 확보 - "집 등록"과 동일한 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutinePlaceDetectionService {

    private final VisitOccurrenceRepository visitOccurrenceRepository;
    private final RoutinePlaceRepository routinePlaceRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final Clock clock;

    /**
     * 패턴 감지에 사용하는 최근 기간 (주)
     * - 너무 짧으면 격주/월 단위 루틴을 놓치고, 너무 길면 이미 끝난 루틴(예: 퇴직 전 직장)이
     *   계속 후보로 남아 오히려 부정확해진다. 6주는 "매주" 루틴을 3회 이상 관측하기에
     *   충분하면서도 최신 생활 패턴을 반영하는 절충점.
     */
    static final int WINDOW_WEEKS = 6;

    /** 같은 좌표로 묶는 클러스터링 반경 (미터) - 안드로이드 StayPoint 판정 반경(50m)보다 넉넉히 잡아 GPS 오차 흡수 */
    private static final double CLUSTER_RADIUS_METERS = 100.0;

    /**
     * 같은 요일에 최소 이 횟수 이상 방문해야 "루틴"으로 인정.
     * 서로 다른 주(week)에서 관측된 것만 세어(같은 주에 우연히 두 번 겹친 경우 제외) 오탐을 줄인다.
     */
    private static final int MIN_OCCURRENCES_SAME_WEEKDAY = 3;
    private static final int MIN_DISTINCT_WEEKS = 3;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void detectRoutinePlaces() {
        List<UserPreferences> consented = userPreferencesRepository.findAllByRoutinePlaceConsentTrue();
        log.info("[루틴장소] 패턴 감지 배치 시작 - 대상 {}명", consented.size());
        for (UserPreferences prefs : consented) {
            try {
                detectForUser(prefs.getUserId());
            } catch (Exception e) {
                log.error("[루틴장소] 패턴 감지 실패 - userId: {}", prefs.getUserId(), e);
            }
        }
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldOccurrences() {
        LocalDate cutoff = LocalDate.now(clock).minusWeeks(WINDOW_WEEKS).minusDays(1);
        visitOccurrenceRepository.deleteByVisitDateBefore(cutoff);
        log.debug("[루틴장소] {} 이전 임시 방문 이력 정리 완료", cutoff);
    }

    @Transactional
    void detectForUser(Long userId) {
        LocalDate cutoff = LocalDate.now(clock).minusWeeks(WINDOW_WEEKS);
        List<VisitOccurrence> occurrences = visitOccurrenceRepository.findByUserIdAndVisitDateAfter(userId, cutoff);
        if (occurrences.isEmpty()) {
            return;
        }

        // findByUserId 결과가 불변 리스트일 수 있으므로(테스트에서 List.of() 등), 같은 배치 내
        // 중복 생성 방지를 위해 뒤에서 add()하는 것을 감안해 가변 리스트로 감싼다.
        List<RoutinePlace> existingPlaces = new ArrayList<>(routinePlaceRepository.findByUserId(userId));
        List<Cluster> clusters = clusterByProximity(occurrences);

        for (Cluster cluster : clusters) {
            RoutinePattern pattern = detectPattern(cluster);
            if (pattern == null) {
                continue; // 패턴 기준 미달 - 후보로 제안하지 않음
            }

            Optional<RoutinePlace> matched = existingPlaces.stream()
                    .filter(place -> GeoDistanceUtil.isWithinRadius(
                            place.getLatitude(), place.getLongitude(),
                            cluster.centroidLat, cluster.centroidLng, place.getRadiusMeters()))
                    .findFirst();

            if (matched.isPresent()) {
                RoutinePlace place = matched.get();
                if (place.getStatus() == RoutinePlaceStatus.DISMISSED) {
                    continue; // 사용자가 거절한 장소는 영구적으로 재제안하지 않는다
                }
                place.refreshStats(pattern.occurrenceCount, pattern.routineDaysCsv,
                        pattern.rangeStart, pattern.rangeEnd, LocalDateTime.now(clock));
                routinePlaceRepository.save(place);
            } else {
                RoutinePlace created = RoutinePlace.builder()
                        .userId(userId)
                        .latitude(cluster.centroidLat)
                        .longitude(cluster.centroidLng)
                        .status(RoutinePlaceStatus.SUGGESTED)
                        .routineDays(pattern.routineDaysCsv)
                        .routineTimeRangeStart(pattern.rangeStart)
                        .routineTimeRangeEnd(pattern.rangeEnd)
                        .occurrenceCount(pattern.occurrenceCount)
                        .lastDetectedAt(LocalDateTime.now(clock))
                        .build();
                routinePlaceRepository.save(created);
                existingPlaces.add(created); // 같은 배치 내 중복 생성 방지
                log.info("[루틴장소] 새 후보 감지 - userId: {}, 요일: {}, 발생 {}회",
                        userId, pattern.routineDaysCsv, pattern.occurrenceCount);
            }
        }
    }

    /**
     * 반경 기준 그리디 클러스터링. 기존 클러스터 중 centroid가 반경 이내인 곳이 있으면 합류,
     * 없으면 새 클러스터를 만든다. 사용자당 최근 6주 발생 건수가 수백 건 수준일 것으로 보여
     * O(n*클러스터수)로 충분하다(대량 트래픽을 다루는 배치가 아님).
     */
    private List<Cluster> clusterByProximity(List<VisitOccurrence> occurrences) {
        List<Cluster> clusters = new ArrayList<>();
        for (VisitOccurrence occurrence : occurrences) {
            Cluster target = clusters.stream()
                    .filter(c -> GeoDistanceUtil.isWithinRadius(
                            c.centroidLat, c.centroidLng,
                            occurrence.getLatitude(), occurrence.getLongitude(),
                            CLUSTER_RADIUS_METERS))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                target = new Cluster();
                clusters.add(target);
            }
            target.add(occurrence);
        }
        return clusters;
    }

    /**
     * 클러스터 내부를 요일별로 그룹핑해 "루틴"으로 인정할 요일들을 판정.
     * 같은 요일이라도 서로 다른 주(week)에서 관측된 것만 세어, 우연히 이틀 연속 방문한 경우를
     * 반복 패턴으로 오인하지 않도록 한다.
     *
     * @return 루틴로 인정된 요일이 하나도 없으면 null
     */
    private RoutinePattern detectPattern(Cluster cluster) {
        Map<DayOfWeek, List<VisitOccurrence>> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (VisitOccurrence occurrence : cluster.occurrences) {
            byWeekday.computeIfAbsent(occurrence.getDayOfWeek(), k -> new ArrayList<>()).add(occurrence);
        }

        List<DayOfWeek> routineDays = new ArrayList<>();
        List<VisitOccurrence> qualifyingOccurrences = new ArrayList<>();
        WeekFields weekFields = WeekFields.ISO;

        for (Map.Entry<DayOfWeek, List<VisitOccurrence>> entry : byWeekday.entrySet()) {
            List<VisitOccurrence> sameWeekday = entry.getValue();
            if (sameWeekday.size() < MIN_OCCURRENCES_SAME_WEEKDAY) {
                continue;
            }
            Set<String> distinctWeeks = sameWeekday.stream()
                    .map(o -> o.getVisitDate().get(weekFields.weekBasedYear())
                            + "-" + o.getVisitDate().get(weekFields.weekOfWeekBasedYear()))
                    .collect(Collectors.toSet());
            if (distinctWeeks.size() < MIN_DISTINCT_WEEKS) {
                continue;
            }
            routineDays.add(entry.getKey());
            qualifyingOccurrences.addAll(sameWeekday);
        }

        if (routineDays.isEmpty()) {
            return null;
        }

        routineDays.sort(Comparator.naturalOrder());
        String routineDaysCsv = routineDays.stream().map(Enum::name).collect(Collectors.joining(","));

        LocalTime rangeStart = qualifyingOccurrences.stream()
                .map(VisitOccurrence::getVisitStartTime)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalTime rangeEnd = qualifyingOccurrences.stream()
                .map(VisitOccurrence::getVisitEndTime)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new RoutinePattern(routineDaysCsv, qualifyingOccurrences.size(), rangeStart, rangeEnd);
    }

    /** 좌표 근접 기준으로 묶인 방문 발생 이력 집합 (centroid는 새 지점이 추가될 때마다 재계산) */
    private static class Cluster {
        double centroidLat;
        double centroidLng;
        final List<VisitOccurrence> occurrences = new ArrayList<>();

        void add(VisitOccurrence occurrence) {
            occurrences.add(occurrence);
            centroidLat = occurrences.stream().mapToDouble(VisitOccurrence::getLatitude).average().orElse(0);
            centroidLng = occurrences.stream().mapToDouble(VisitOccurrence::getLongitude).average().orElse(0);
        }
    }

    private record RoutinePattern(String routineDaysCsv, int occurrenceCount, LocalTime rangeStart, LocalTime rangeEnd) {
    }
}

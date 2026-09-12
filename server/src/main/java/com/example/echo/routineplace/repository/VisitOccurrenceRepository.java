package com.example.echo.routineplace.repository;

import com.example.echo.routineplace.entity.VisitOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VisitOccurrenceRepository extends JpaRepository<VisitOccurrence, Long> {

    List<VisitOccurrence> findByUserIdAndVisitDateAfter(Long userId, LocalDate cutoff);

    /** 같은 날 재기록 시 멱등성을 위한 delete-then-insert용 */
    @Modifying
    @Query("delete from VisitOccurrence v where v.userId = :userId and v.visitDate = :visitDate")
    void deleteByUserIdAndVisitDate(@Param("userId") Long userId, @Param("visitDate") LocalDate visitDate);

    /** 보관 기간(WINDOW_WEEKS)을 넘긴 임시 발생 이력 정리용 */
    @Modifying
    @Query("delete from VisitOccurrence v where v.visitDate < :cutoff")
    void deleteByVisitDateBefore(@Param("cutoff") LocalDate cutoff);

    /** 동의 철회 시 전량 파기용 */
    void deleteByUserId(Long userId);
}

package com.example.echo.diary.repository;

import com.example.echo.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

    List<Diary> findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    Optional<Diary> findByIdAndUserId(Long id, Long userId);
}

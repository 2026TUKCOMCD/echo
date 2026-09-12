package com.example.echo.routineplace.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.routineplace.dto.ConsentResponse;
import com.example.echo.routineplace.dto.ConsentUpdateRequest;
import com.example.echo.routineplace.dto.RoutinePlaceConfirmRequest;
import com.example.echo.routineplace.dto.RoutinePlaceResponse;
import com.example.echo.routineplace.service.RoutinePlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 루틴 방문 장소(회사/병원 등 반복 방문 장소) 후보 확인·확정·삭제, 기능 동의 관리 API.
 */
@RestController
@RequestMapping("/api/users/me/routine-places")
@RequiredArgsConstructor
public class RoutinePlaceController {

    private final RoutinePlaceService routinePlaceService;

    @GetMapping("/consent")
    public ResponseEntity<ConsentResponse> getConsent(@CurrentUser Long userId) {
        return ResponseEntity.ok(routinePlaceService.getConsent(userId));
    }

    @PutMapping("/consent")
    public ResponseEntity<ConsentResponse> updateConsent(
            @CurrentUser Long userId,
            @Valid @RequestBody ConsentUpdateRequest request) {
        return ResponseEntity.ok(routinePlaceService.setConsent(userId, request.getConsented()));
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<RoutinePlaceResponse>> getCandidates(@CurrentUser Long userId) {
        return ResponseEntity.ok(routinePlaceService.getCandidates(userId));
    }

    @GetMapping
    public ResponseEntity<List<RoutinePlaceResponse>> getConfirmedPlaces(@CurrentUser Long userId) {
        return ResponseEntity.ok(routinePlaceService.getConfirmedPlaces(userId));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<RoutinePlaceResponse> confirm(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RoutinePlaceConfirmRequest request) {
        return ResponseEntity.ok(routinePlaceService.confirm(userId, id, request.getCategory()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoutinePlaceResponse> updateCategory(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RoutinePlaceConfirmRequest request) {
        return ResponseEntity.ok(routinePlaceService.updateCategory(userId, id, request.getCategory()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        routinePlaceService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.echo.common.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.WeatherData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherClient weatherClient;

    @GetMapping
    public ResponseEntity<WeatherData> getWeather(
            @CurrentUser Long userId,
            @RequestParam Double lat,
            @RequestParam Double lon) {
        WeatherData weather = weatherClient.getCurrentWeather(lat, lon);
        if (weather == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(weather);
    }
}

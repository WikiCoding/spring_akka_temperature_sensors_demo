package com.wikicoding.springakkatemperaturesensorsdemo.controllers;

import com.wikicoding.springakkatemperaturesensorsdemo.services.TemperaturesService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/temperatures")
@RequiredArgsConstructor
@Slf4j
public class TemperaturesController {
    private final TemperaturesService temperaturesService;

    @GetMapping
    public CompletionStage<ResponseEntity<@NonNull TemperatureReportResponse>> getTemperatureReport() {
        return temperaturesService.getTemperatureReport()
                .thenApply(report -> ResponseEntity.ok(
                        new TemperatureReportResponse(report.getAverage(), report.getMin(), report.getMax())
                ))
                .exceptionally(throwable -> {
                    log.error("Failed to get temperature report: {}", throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
                });
    }
}

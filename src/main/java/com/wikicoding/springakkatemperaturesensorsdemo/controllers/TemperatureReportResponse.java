package com.wikicoding.springakkatemperaturesensorsdemo.controllers;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TemperatureReportResponse {
    private final double average;
    private final double min;
    private final double max;
}

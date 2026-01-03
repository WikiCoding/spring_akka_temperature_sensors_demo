package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReadingReport {
    private final double average;
    private final double min;
    private final double max;
}

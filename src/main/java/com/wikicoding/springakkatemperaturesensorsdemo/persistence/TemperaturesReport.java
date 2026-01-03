package com.wikicoding.springakkatemperaturesensorsdemo.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "temperatures_report")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemperaturesReport {
    @Id
    private String id;
    private double average;
    private double min;
    private double max;
    private LocalDateTime timestamp;
}

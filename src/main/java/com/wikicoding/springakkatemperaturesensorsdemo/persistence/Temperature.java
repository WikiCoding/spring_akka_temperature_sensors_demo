package com.wikicoding.springakkatemperaturesensorsdemo.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Temperature {
    @Id
    private String sensorId;
    private double temperature;
    private LocalDateTime timestamp;
}

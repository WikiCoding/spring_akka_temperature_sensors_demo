package com.wikicoding.springakkatemperaturesensorsdemo.persistence;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemperaturesReportRepository extends JpaRepository<@NonNull TemperaturesReport, @NonNull String> {
}

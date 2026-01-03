package com.wikicoding.springakkatemperaturesensorsdemo.services;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.ReadingReport;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.SensorManager;
import com.wikicoding.springakkatemperaturesensorsdemo.persistence.TemperaturesReport;
import com.wikicoding.springakkatemperaturesensorsdemo.persistence.TemperaturesReportRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemperaturesService {
    private final ActorSystem<SensorManager.Command> actorSystem;
    @Value("${ask.timeout.seconds:1}")
    private int askTimeout;
    private final TemperaturesReportRepository temperaturesReportRepository;

    @Transactional
    public CompletionStage<ReadingReport> getTemperatureReport() {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<ReadingReport> me) -> new SensorManager.GetReadingsCommand(me, askTimeout),
                Duration.ofSeconds(askTimeout),
                actorSystem.scheduler()
        ).thenApply(report -> {
            log.info("Saving Temperatures ºC -> Avg: {}, Min: {}, Max: {}", report.getAverage(), report.getMin(), report.getMax());

            // I'll just leave this blocking call to the db, but this could be moved to a scheduled job that persists bulks of data for example
            temperaturesReportRepository.save(
                    new TemperaturesReport(
                            UUID.randomUUID().toString(),
                            report.getAverage(),
                            report.getMin(),
                            report.getMax(),
                            LocalDateTime.now()
                    )
            );

            return report;
        });
    }

    public CompletableFuture<List<TemperaturesReport>> getAllStoredTemperatureReportsAsync() {
        return CompletableFuture.supplyAsync(temperaturesReportRepository::findAll);
    }
}

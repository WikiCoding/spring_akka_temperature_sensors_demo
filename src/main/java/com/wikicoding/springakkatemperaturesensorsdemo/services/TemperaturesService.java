package com.wikicoding.springakkatemperaturesensorsdemo.services;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.ReadingReport;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.SensorManager;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemperaturesService {
    private final ActorSystem<SensorManager.Command> actorSystem;
    @Value("${ask.timeout.seconds:1}")
    private int askTimeout;

    public CompletionStage<ReadingReport> getTemperatureReport() {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<ReadingReport> me) -> new SensorManager.GetReadingsCommand(me, askTimeout),
                Duration.ofSeconds(askTimeout),
                actorSystem.scheduler()
        ).thenApply(report -> {
            log.info("Temperatures ºC -> Avg: {}, Min: {}, Max: {}", report.getAverage(), report.getMin(), report.getMax());
            return report;
        });
    }
}

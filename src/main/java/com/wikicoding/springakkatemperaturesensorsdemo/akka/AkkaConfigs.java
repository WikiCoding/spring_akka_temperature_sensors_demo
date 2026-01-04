package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import akka.actor.typed.ActorSystem;
import com.wikicoding.springakkatemperaturesensorsdemo.persistence.TemperatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AkkaConfigs {
    @Value("${stash.capacity:10}")
    private int stashCapacity;
    @Value("${number.workers:1}")
    private int numberOfWorkers;
    @Value("${manager.pool.size:1}")
    private int poolSize;
    private final TemperatureRepository temperatureRepository;

    @Bean
    public ActorSystem<SensorManager.Command> actorSystem() {
        return ActorSystem.create(SensorSystem.create(poolSize, stashCapacity, numberOfWorkers, temperatureRepository), SensorSystem.class.getSimpleName());
    }
}

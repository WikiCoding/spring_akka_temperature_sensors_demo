package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import akka.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AkkaConfigs {
    @Value("${stash.capacity:10}")
    private int stashCapacity;
    @Value("${number.workers:1}")
    private int numberOfWorkers;

    @Bean
    public ActorSystem<SensorManager.Command> actorSystem() {
        return ActorSystem.create(SensorManager.create(stashCapacity, numberOfWorkers), SensorManager.class.getSimpleName());
    }
}

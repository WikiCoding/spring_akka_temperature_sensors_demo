package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.wikicoding.springakkatemperaturesensorsdemo.persistence.Temperature;
import com.wikicoding.springakkatemperaturesensorsdemo.persistence.TemperatureRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

public class SensorWorker extends AbstractBehavior<SensorWorker.Command> {
    /** Class variables section **/
    private final TimerScheduler<Command> timers;
    private final Object TIMER_KEY = "request-delay";
    private final TemperatureRepository temperatureRepository;

    /** Constructor and create section **/
    private SensorWorker(ActorContext<Command> context, TimerScheduler<Command> timers, TemperatureRepository temperatureRepository) {
        super(context);
        this.timers = timers;
        this.temperatureRepository = temperatureRepository;
    }

    public static Behavior<Command> create(TemperatureRepository temperatureRepository) {
        return Behaviors.withTimers(timer ->
                Behaviors.setup(context -> new SensorWorker(context, timer, temperatureRepository))
        );
    }

    /** Message Handlers Section **/
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(SensorTempReading.class, this::onSensorTempReading)
                .onMessage(WrappedTempReading.class, this::onTimeUp)
                .build();
    }

    /** Behaviours section **/
    private Behavior<Command> onSensorTempReading(SensorTempReading message) {
        Random random = new Random();
        int randomDelay = random.nextInt(3000);

        getContext().getLog().debug("Worker {} waiting for {} ms.", getContext().getSelf().path(), randomDelay);
        // Schedule a message to 'self' after the delay. Could also have a key in this case but for this demo I'm ignoring it.
        timers.startSingleTimer(
                TIMER_KEY,
                new WrappedTempReading(message),
                Duration.ofMillis(randomDelay)
        );

        return Behaviors.same();
    }

    private Behavior<Command> onTimeUp(WrappedTempReading wrapped) {
        Random random = new Random();
        double randomTemp = random.nextDouble(-10, 45);

        temperatureRepository.save(new Temperature(UUID.randomUUID().toString(), randomTemp, LocalDateTime.now()));

        getContext().getLog().debug("Worker {} replying with temperature {}ºC.", getContext().getSelf().path(), randomTemp);
        wrapped.originalRequest.getReplyTo().tell(
                new SensorManager.SensorTemperatureReading(randomTemp, getContext().getSelf())
        );

        return Behaviors.stopped();
    }

    /** Commands section **/
    public interface Command extends Serializable {}

    @Getter
    @AllArgsConstructor
    public static class SensorTempReading implements Command {
        @Serial
        private static final long serialVersionUID = 1L;
        private final ActorRef<SensorManager.Command> replyTo;
    }

    // this is just to add the delay since using Thread.sleep() is an Anti-pattern and something we really want to avoid
    @Getter
    @AllArgsConstructor
    private static final class WrappedTempReading implements Command {
        private final SensorTempReading originalRequest;
    }
}

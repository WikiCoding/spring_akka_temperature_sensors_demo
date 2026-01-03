package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SensorManager extends AbstractBehavior<SensorManager.Command> {
    /** Class variables section **/
    private final StashBuffer<Command> stash;
    private final int numberOfWorkers;
    private Map<ActorRef<SensorWorker.Command>, Double> temperatures;
    private ActorRef<ReadingReport> sender;
    private final TimerScheduler<Command> timers;
    private final Object TIMER_KEY = "request-timeout";

    /** Constructor and create section **/
    private SensorManager(ActorContext<Command> context, StashBuffer<Command> stash, int numberOfWorkers, TimerScheduler<Command> timers) {
        super(context);
        this.stash = stash;
        this.numberOfWorkers = numberOfWorkers;
        this.timers = timers;
    }

    public static Behavior<Command> create(int stashCapacity, int numOfWorkers) {
        return Behaviors.withStash(stashCapacity,
                stash ->
                        Behaviors.withTimers(timer ->
                                Behaviors.setup(context ->
                                        new SensorManager(context, stash, numOfWorkers, timer)
                                )
                ));
    }

    /** Message Handlers Section **/
    @Override
    public Receive<Command> createReceive() {
        return idleMessageHandler();
    }

    private Receive<Command> idleMessageHandler() {
        return newReceiveBuilder()
                .onMessage(GetReadingsCommand.class, this::onIdleGetReadingsCommand)
                .onSignal(Terminated.class, handler -> Behaviors.same())
                .build();
    }

    private Receive<Command> activeMessageHandler() {
        return newReceiveBuilder()
                .onMessage(GetReadingsCommand.class, this::onActiveGetReadingsCommand)
                .onMessage(SensorTemperatureReading.class, this::onActiveSensorTemperatureReadingCommand)
                .onMessage(RequestTimeout.class, message -> onTimeoutCommand())
                .onSignal(Terminated.class, this::onActiveTerminatedSignalCommand)
                .build();
    }

    /** Behaviours section **/
    private Behavior<Command> onIdleGetReadingsCommand(GetReadingsCommand message) {
        this.sender = message.getReplyTo();
        this.temperatures = new HashMap<>();

        getContext().getLog().debug("Starting SensorManager Actor with timeout of {} seconds", message.getTimeoutSeconds());
        // adding a timer as a safety measure to make sure that the SensorManager doesn't hang at the active state eventually.
        timers.startSingleTimer(TIMER_KEY, new RequestTimeout(), Duration.ofSeconds(message.getTimeoutSeconds()));

        getContext().getLog().info("Starting {} workers", numberOfWorkers);
        for (int i = 0; i < numberOfWorkers; i++) {
            createNextActorAndTellItToWork(i);
        }

        return activeMessageHandler();
    }

    private Behavior<Command> onActiveGetReadingsCommand(GetReadingsCommand message) {
        if (!stash.isFull()) {
            getContext().getLog().info("Stashing message from {}", message.getReplyTo().path());
            stash.stash(message);
        } else {
            getContext().getLog().warn("Stash is full, dropping message from {}", message.getReplyTo().path());
        }
        return Behaviors.same();
    }

    private Behavior<Command> onActiveSensorTemperatureReadingCommand(SensorTemperatureReading message) {
        getContext().getLog().debug("Current temperatures list size: {}. Adding another entry from worker {}", temperatures.size(), message.getWorker().path());
        temperatures.put(message.getWorker(), message.getTemperature());

        if (temperatures.size() == numberOfWorkers) {
            getContext().getLog().debug("Gathered all the {} temperature readings, canceling Manager timeout.", numberOfWorkers);
            timers.cancel(TIMER_KEY);

            // this will close any remaining workers. Stopping is an async signal
            for (ActorRef<Void> child : getContext().getChildren()) {
                getContext().getLog().debug("Had {} worker(s) still up. Stopping worker: {}", getContext().getChildren().size(), child.path());
                getContext().unwatch(child);
                getContext().stop(child);
            }

            double avg = temperatures.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double min = temperatures.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = temperatures.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

            sender.tell(new ReadingReport(avg, min, max));

            return stash.unstashAll(idleMessageHandler());
        }

        return Behaviors.same();
    }

    private Behavior<Command> onActiveTerminatedSignalCommand(Terminated message) {
        getContext().getLog().debug("Worker {} terminated", message.getRef().path());
        ActorRef<SensorWorker.Command> workerRef = message.getRef().unsafeUpcast();

        if (!temperatures.containsKey(workerRef) && temperatures.size() < numberOfWorkers) {
            getContext().getLog().warn("Worker {} died early, replacing...", message.getRef().path());
            createNextActorAndTellItToWork(message.getRef().path().uid());
        }

        return Behaviors.same();
    }

    private Behavior<Command> onTimeoutCommand() {
        getContext().getLog().error("Request timed out! Only gathered {}/{} readings.",
                temperatures.size(), numberOfWorkers);

        cleanupWorkers();

        double avg = temperatures.values().stream().mapToDouble(d -> d).average().orElse(0.0);
        sender.tell(new ReadingReport(avg, 0.0, 0.0));

        return stash.unstashAll(idleMessageHandler());
    }

    private void cleanupWorkers() {
        for (ActorRef<Void> child : getContext().getChildren()) {
            getContext().unwatch(child);
            getContext().stop(child);
        }
    }

    private void createNextActorAndTellItToWork(int workerId) {
        Behavior<SensorWorker.Command> workerBehaviour = Behaviors.supervise(SensorWorker.create()).onFailure(SupervisorStrategy.restart());

        ActorRef<SensorWorker.Command> worker = getContext().spawn(workerBehaviour, "SensorWorker-" + workerId);
        getContext().watch(worker);

        getContext().getLog().debug("Created worker {} and sending temperature read request.", worker.path());
        worker.tell(new SensorWorker.SensorTempReading(getContext().getSelf()));
    }

    /** Commands section **/
    public interface Command extends Serializable {}

    @Getter
    @RequiredArgsConstructor
    public static class GetReadingsCommand implements Command {
        @Serial
        private static final long serialVersionUID = 1L;
        private final ActorRef<ReadingReport> replyTo;
        private final int timeoutSeconds;
    }

    @Getter
    @RequiredArgsConstructor
    public static class SensorTemperatureReading implements Command {
        private static final long serialVersionUID = 1L;
        private final double temperature;
        private final ActorRef<SensorWorker.Command> worker;
    }

    @Getter
    @RequiredArgsConstructor
    private static class RequestTimeout implements Command {
        private static final long serialVersionUID = 1L;
    }
}

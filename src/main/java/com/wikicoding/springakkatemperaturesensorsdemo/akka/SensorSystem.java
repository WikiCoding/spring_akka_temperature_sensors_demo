package com.wikicoding.springakkatemperaturesensorsdemo.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

public class SensorSystem extends AbstractBehavior<SensorManager.Command> {
    private PoolRouter<SensorManager.Command> managerPoolRouter;
    private ActorRef<SensorManager.Command> managers;
    private final int poolSize;
    private final int stashCapacity;
    private final int numOfWorkers;

    public SensorSystem(ActorContext<SensorManager.Command> context, int poolSize, int stashCapacity, int numOfWorkers) {
        super(context);
        this.poolSize = poolSize;
        this.stashCapacity = stashCapacity;
        this.numOfWorkers = numOfWorkers;
        managerPoolRouter = Routers.pool(poolSize,
                Behaviors.supervise(SensorManager.create(stashCapacity, numOfWorkers)).onFailure(SupervisorStrategy.restart())); // round-robin routing
        managers = getContext().spawn(managerPoolRouter, "SensorManagerPool");
    }

    public static Behavior<SensorManager.Command> create(int poolSize, int stashCapacity, int numOfWorkers) {
        return Behaviors.setup(context -> new SensorSystem(context, poolSize, stashCapacity, numOfWorkers));
    }

    @Override
    public Receive<SensorManager.Command> createReceive() {
        return newReceiveBuilder()
                .onAnyMessage(message -> {
                    managers.tell(message);
                    return Behaviors.same();
                })
                .build();
    }
}

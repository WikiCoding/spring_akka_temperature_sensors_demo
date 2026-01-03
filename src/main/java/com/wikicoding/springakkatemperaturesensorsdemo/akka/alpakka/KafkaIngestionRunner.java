package com.wikicoding.springakkatemperaturesensorsdemo.akka.alpakka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.ReadingReport;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.SensorManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaIngestionRunner {
    private final ActorSystem<SensorManager.Command> actorSystem;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;
    @Value("${spring.kafka.consumer.parallelism:1}")
    private int consumerParallelism;
    @Value("${spring.kafka.consumer.topic}")
    private String consumerTopic;
    @Value("${spring.kafka.producer.topic}")
    private String producerTopic;
    @Value("${ask.timeout.seconds:1}")
    private int askTimeout;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<@NonNull String, @NonNull String> kafkaTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void startIngestion() {
        // Consumer to kick off the actors
        ConsumerSettings<String, String> consumerSettings = createConsumerSettings();

        Consumer.committableSource(consumerSettings, Subscriptions.topics(consumerTopic))
                .mapAsync(consumerParallelism, msg -> {
                    log.debug("Received Kafka message with key: {} and value: {}, triggering actor aggregation", msg.record().key(), msg.record().value());

                    return AskPattern.ask(
                            actorSystem,
                            (ActorRef<ReadingReport> me) -> new SensorManager.GetReadingsCommand(me, askTimeout),
                            Duration.ofSeconds(askTimeout + 1), // Slightly longer than actor timeout
                            actorSystem.scheduler()
                    ).thenApply(report -> {
                        log.info("Kafka batch processed: Avg: {}, Min: {}, Max: {}", report.getAverage(), report.getMin(), report.getMax());
                        produceMessageWithKafkaTemplate(report);
                        return msg.committableOffset(); // Return offset to be committed
                    });
                })
                .runWith(Committer.sink(CommitterSettings.create(actorSystem)), actorSystem);
    }

    private ConsumerSettings<String, String> createConsumerSettings() {
        return ConsumerSettings.create(actorSystem, new StringDeserializer(), new StringDeserializer())
                .withBootstrapServers(bootstrapServers)
                .withGroupId(groupId)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
    }

    private void produceMessageWithKafkaTemplate(ReadingReport report) {
        String serialized = "";

        try {
            serialized = objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String key = UUID.randomUUID().toString();
        kafkaTemplate.send(producerTopic, key, serialized);
        log.info("Report sent to topic: {} with key: {} and value: {}", producerTopic, key, serialized);
    }
}

// documentation: https://doc.akka.io/libraries/alpakka-kafka/current/consumer.html
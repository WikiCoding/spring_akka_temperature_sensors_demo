package com.wikicoding.springakkatemperaturesensorsdemo.akka.alpakka;

import akka.actor.typed.ActorSystem;
import akka.kafka.*;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.ReadingReport;
import com.wikicoding.springakkatemperaturesensorsdemo.akka.SensorManager;
import com.wikicoding.springakkatemperaturesensorsdemo.services.TemperaturesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
    private final TemperaturesService temperaturesService;

    @EventListener(ApplicationReadyEvent.class)
    public void startIngestion() {
        // Consumer to kick off the actors
        ConsumerSettings<String, String> consumerSettings = createConsumerSettings();
        ProducerSettings<String, String> producerSettings = createProducerSettings();

        Consumer.committableSource(consumerSettings, Subscriptions.topics(consumerTopic))
                .mapAsync(consumerParallelism, msg -> {
                    log.debug("Received Kafka message with key: {} and value: {}, triggering actor aggregation", msg.record().key(), msg.record().value());

                    return temperaturesService.getTemperatureReport()
                            .thenApply(report -> {
                        log.info("Kafka batch processed: Avg: {}, Min: {}, Max: {}", report.getAverage(), report.getMin(), report.getMax());
                        String serialized = serializeReport(report);
                        String key = UUID.randomUUID().toString();
                        log.info("Producing Message with Alpakka with Key: {}, Value: {} to Topic: {}", key, serialized, producerTopic);
                        return ProducerMessage.single(
                                new ProducerRecord<>(producerTopic, key, serialized),
                                msg.committableOffset()
                        ); // Return offset to be committed too
                    });
                })
                .via(Producer.flexiFlow(producerSettings))
                .map(result -> result.passThrough())
                .runWith(Committer.sink(CommitterSettings.create(actorSystem)), actorSystem);
    }

    private ConsumerSettings<String, String> createConsumerSettings() {
        return ConsumerSettings.create(actorSystem, new StringDeserializer(), new StringDeserializer())
                .withBootstrapServers(bootstrapServers)
                .withGroupId(groupId)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
    }

    private ProducerSettings<String, String> createProducerSettings() {
        return ProducerSettings.create(actorSystem, new StringSerializer(), new StringSerializer())
                .withBootstrapServers(bootstrapServers);
    }


    private String serializeReport(ReadingReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize report", e);
        }
    }
}

// documentation: https://doc.akka.io/libraries/alpakka-kafka/current/consumer.html
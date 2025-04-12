package example.projection.to.kafka;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.kafka.javadsl.SendProducer;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.javadsl.Handler;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.GeneratedMessageV3;
import example.market.domain.Market;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MarketProjectionHandler extends Handler<EventEnvelope<Market.Event>> {

    private final Logger log = LoggerFactory.getLogger(MarketProjectionHandler.class);
    private final Executor ec = Executors.newCachedThreadPool();  // Simple executor for demo
    private final ActorSystem<?> system;
    private final String topic;
    private final SendProducer<String, byte[]> producer;

    public MarketProjectionHandler(
            ActorSystem<?> system,
            String topic,
            SendProducer<String, byte[]> producer) {
        this.system = system;
        this.topic = topic;
        this.producer = producer;
    }

    @Override
    public CompletionStage<Done> process(EventEnvelope<Market.Event> envelope) {
        log.debug("processing market event [{}] to topic [{}]", envelope, topic);

        Market.Event event = envelope.event();
        byte[] serializedEvent = this.serialize(event);

        if (serializedEvent.length > 0) {
            ProducerRecord<String, byte[]> record =
                    new ProducerRecord<>(topic, event.marketId(), serializedEvent);

            return producer.send(record)
                    .thenApplyAsync(result -> {
                        log.debug("published event [{}] to topic [{}]", event, topic);
                        return Done.done();
                    }, ec);
        } else {
            return CompletableFuture.completedFuture(Done.done());
        }
    }

    private byte[] serialize(Market.Event event) {
        try {
            GeneratedMessageV3 proto;
            switch (event) {
                case Market.Closed closed -> proto = betting.house.projection.proto.Market.MarketClosed.newBuilder()
                        .setMarketId(closed.marketId())
                        .setResult(closed.result())
                        .build();
                case Market.Opened opened -> proto = betting.house.projection.proto.Market.MarketOpened.newBuilder()
                        .setMarketId(opened.marketId())
                        .build();
                case Market.Cancelled(String marketId, String reason) ->
                        proto = betting.house.projection.proto.Market.MarketCancelled.newBuilder()
                                .setMarketId(marketId)
                                .setReason(reason)
                                .build();
                case null, default -> {
                    log.info("ignoring event {} in projection", event);
                    proto = Empty.getDefaultInstance();
                }
            }

            return Any.pack(proto, "market-projection").toByteArray();
        } catch (Exception e) {
            log.error("Serialization error", e);
            return new byte[0];
        }
    }
}
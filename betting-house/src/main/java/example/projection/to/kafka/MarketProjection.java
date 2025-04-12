package example.projection.to.kafka;

import akka.actor.CoordinatedShutdown;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.projection.ProjectionBehavior;
import akka.projection.ProjectionId;
import akka.projection.javadsl.AtLeastOnceProjection;
import akka.projection.javadsl.SourceProvider;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.jdbc.javadsl.JdbcProjection;
import akka.persistence.query.Offset;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.SendProducer;
import example.market.domain.Market;
import example.projection.dbconnection.QueryDSLJdbcSession;
import example.projection.dbconnection.QueryDSLUtil;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Optional;

public class MarketProjection {

    public static void init(ActorSystem<?> system) {
        SendProducer<String, byte[]> producer = createProducer(system);
        String topic = system.settings().config()
                .getString("kafka.market-projection.topic");

        ShardedDaemonProcess.get(system).init(
                ProjectionBehavior.Command.class,
                "MarketProjection",
                Market.tags.length,
                index -> ProjectionBehavior.create(
                        createProjection(system, topic, producer, index)
                ),
                ShardedDaemonProcessSettings.create(system),
                Optional.of(ProjectionBehavior.stopMessage())
        );
    }

    private static SendProducer<String, byte[]> createProducer(ActorSystem<?> system) {
        ProducerSettings<String, byte[]> producerSettings = ProducerSettings.create(
                system,
                new StringSerializer(),
                new ByteArraySerializer()
        );

        SendProducer<String, byte[]> sendProducer = new SendProducer<>(producerSettings, system);

        CoordinatedShutdown.get(system).addTask(
                CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                "closing send producer",
                sendProducer::close
        );

        return sendProducer;
    }

    private static AtLeastOnceProjection<Offset, EventEnvelope<Market.Event>> createProjection(
            ActorSystem<?> system,
            String topic,
            SendProducer<String, byte[]> producer,
            int index
    ) {
        String tag = Market.tags[index];

        SourceProvider<Offset, EventEnvelope<Market.Event>> sourceProvider =
                EventSourcedProvider.eventsByTag(
                        system,
                        JdbcReadJournal.Identifier(),
                        tag
                );

        return JdbcProjection.atLeastOnceAsync(
                ProjectionId.of("MarketProjection", tag),
                sourceProvider,
                () -> new QueryDSLJdbcSession(QueryDSLUtil.getDataSource(system.settings().config())),
                () -> new MarketProjectionHandler(system, topic, producer),
                system
        );
    }
}

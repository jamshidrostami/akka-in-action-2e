package example.projection.to.db;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
import akka.persistence.query.Offset;
import akka.projection.ProjectionBehavior;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.ExactlyOnceProjection;
import akka.projection.javadsl.SourceProvider;
import akka.projection.jdbc.javadsl.JdbcProjection;
import example.market.domain.Bet;
import example.projection.dbconnection.QueryDSLJdbcSession;
import example.projection.dbconnection.QueryDSLUtil;

import java.util.Optional;

public class BetProjection {

    public static void init(ActorSystem<?> system, BetRepository repository) {
        ShardedDaemonProcess.get(system).init(ProjectionBehavior.Command.class,
                "bet-projection",
                Bet.tags.size(),
                index -> ProjectionBehavior.create(createProjection(system, repository, index)),
                ShardedDaemonProcessSettings.create(system),
                Optional.of(ProjectionBehavior.stopMessage())
        );
    }

    public static ExactlyOnceProjection<Offset, EventEnvelope<Bet.Event>> createProjection(
            ActorSystem<?> system,
            BetRepository repository,
            int index) {

        String tag = Bet.tags.get(index);

        SourceProvider<Offset, EventEnvelope<Bet.Event>> sourceProvider = EventSourcedProvider
                .eventsByTag(system,
                        JdbcReadJournal.Identifier(),
                        tag);

        return JdbcProjection.exactlyOnce(
                ProjectionId.of("BetProjection", tag),
                sourceProvider,
                () -> new QueryDSLJdbcSession(QueryDSLUtil.getDataSource(system.settings().config())),
                () -> new BetProjectionHandler(repository),
                system
        );
    }
}
package example.market.domain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public class Market {

    public static final EntityTypeKey<Command> typeKey = EntityTypeKey.create(Command.class, "market");

    public record Fixture(String id, String homeTeam, String awayTeam) implements CborSerializable {
    }

    public record Odds(double winHome, double winAway, double draw) implements CborSerializable {
    }

    public interface Command extends CborSerializable {
        ActorRef<Response> replyTo();
    }

    public record Open(Fixture fixture, Odds odds, OffsetDateTime opensAt,
                       ActorRef<Response> replyTo) implements Command {
    }

    /**
     * @param result 1 = winHome, 2 = winAway, 0 = draw
     */
    public record Update(Optional<Odds> odds, Optional<OffsetDateTime> opensAt, Optional<Integer> result,
                         ActorRef<Response> replyTo) implements Command {
    }

    public record Close(ActorRef<Response> replyTo) implements Command {
    }

    public record Cancel(String reason, ActorRef<Response> replyTo) implements Command {
    }

    public record GetState(ActorRef<Response> replyTo) implements Command {
    }

    public interface Response extends CborSerializable {
    }

    public static class Accepted implements Response {
    }

    public record CurrentState(Status status) implements Response {
    }

    public record RequestUnaccepted(String reason) implements Response {
    }

    public interface State extends CborSerializable {
        Status status();
    }

    public record Status(String marketId, Fixture fixture, Odds odds, int result) implements CborSerializable {

        public static Status empty(String marketId) {
            return new Status(marketId, new Fixture("", "", ""), new Odds(-1, -1, -1), 0);
        }

        public Status copy(int result) {
            return new Status(marketId, fixture, odds, result);
        }
    }

    public record UninitializedState(Status status) implements State {
    }

    public record OpenState(Status status) implements State {
    }

    public record ClosedState(Status status) implements State {
    }

    public record CancelledState(Status status) implements State {
    }

    public static Behavior<Command> create(String marketId) {
        return new EventSourcedBehavior<Command, Event, State>(PersistenceId.of(typeKey.name(), marketId)
                , SupervisorStrategy.restartWithBackoff(
                Duration.ofSeconds(10), Duration.ofSeconds(60), 0.1)) {

            @Override
            public State emptyState() {
                return new UninitializedState(Status.empty(marketId));
            }

            @Override
            public CommandHandler<Command, Event, State> commandHandler() {
                return (state, command) -> {
                    if (state instanceof UninitializedState && command instanceof Open open) {
                        Opened opened = new Opened(state.status().marketId, open.fixture, open.odds);
                        return Effect().persist(opened)
                                .thenReply(open.replyTo(), ignored -> new Accepted());
                    } else if (state instanceof OpenState && command instanceof Update update) {
                        Updated updated = new Updated(state.status().marketId, update.odds, update.result);
                        return Effect().persist(updated)
                                .thenReply(update.replyTo(), ignored -> new Accepted());
                    } else if (state instanceof OpenState && command instanceof Close) {
                        Closed closed = new Closed(state.status().marketId, state.status().result, OffsetDateTime.now(ZoneId.of("UTC")));
                        return Effect().persist(closed)
                                .thenReply(command.replyTo(), ignored -> new Accepted());
                    } else if (command instanceof Cancel(String reason, ActorRef<Response> replyTo)) {
                        Cancelled cancelled = new Cancelled(state.status().marketId, reason);
                        return Effect().persist(cancelled)
                                .thenReply(replyTo, ignored -> new Accepted());
                    } else if (command instanceof GetState) {
                        return Effect().none()
                                .thenReply(command.replyTo(), ignored -> new CurrentState(state.status()));
                    } else {
                        return Effect().none()
                                .thenReply(command.replyTo(), ignored ->
                                        new RequestUnaccepted("[" + command + "] is not allowed upon state [" + state + "]"));
                    }
                };
            }

            @Override
            public EventHandler<State, Event> eventHandler() {
                return (state, event) -> {
                    if (event instanceof Opened(String id, Fixture fixture, Odds odds)) {
                        return new OpenState(new Status(id, fixture, odds, 0));
                    } else if (state instanceof OpenState(Status status) && event instanceof Updated updated) {
                        return new OpenState(new Status(status.marketId
                                , status.fixture
                                , updated.odds
                                .orElse(status.odds)
                                , updated.result
                                .orElse(status.result)));
                    } else if (state instanceof OpenState(Status status) && event instanceof Closed closed) {
                        return new ClosedState(status.copy(closed.result));
                    } else if (event instanceof Cancelled) {
                        return new CancelledState(state.status());
                    }
                    return state;
                };
            }

            @Override
            public Set<String> tagsFor(Event event) {
                return Set.of(calculateTag(marketId, tags));
            }

            @Override
            public akka.persistence.typed.javadsl.RetentionCriteria retentionCriteria() {
                return akka.persistence.typed.javadsl.RetentionCriteria.snapshotEvery(100, 2);
            }
        };
    }

    public interface Event extends CborSerializable {
        String marketId();
    }

    public record Opened(String marketId, Fixture fixture, Odds odds) implements Event {
    }

    public record Updated(String marketId, Optional<Odds> odds, Optional<Integer> result) implements Event {
    }

    public record Closed(String marketId, int result, OffsetDateTime at) implements Event {
    }

    public record Cancelled(String marketId, String reason) implements Event {
    }

    public static final String[] tags = new String[3];

    static {
        for (int i = 0; i < tags.length; i++) {
            tags[i] = "market-tag-" + i;
        }
    }

    private static String calculateTag(String entityId, String[] tags) {
        int tagIndex = Math.abs(entityId.hashCode() % tags.length);
        return tags[tagIndex];
    }
}
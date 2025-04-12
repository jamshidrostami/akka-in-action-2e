package example.market.domain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.RetentionCriteria;

import java.time.Duration;
import java.util.Set;

public class Wallet {

    public static final EntityTypeKey<Command> typeKey = EntityTypeKey.create(Command.class, "wallet");

    public interface Command extends CborSerializable {
    }

    public record ReserveFunds(int amount, ActorRef<UpdatedResponse> replyTo) implements Command {
    }

    public record AddFunds(int amount, ActorRef<UpdatedResponse> replyTo) implements Command {
    }

    public record CheckFunds(ActorRef<Response> replyTo) implements Command {
    }

    public interface Event extends CborSerializable {
    }

    public record FundsReserved(int amount) implements Event {
    }

    public record FundsAdded(int amount) implements Event {
    }

    public record FundsReservationDenied(int amount) implements Event {
    }

    public interface Response extends CborSerializable {
    }

    public interface UpdatedResponse extends Response {
    }

    public static final class Accepted implements UpdatedResponse {
    }

    public static final class Rejected implements UpdatedResponse {
    }

    public record CurrentBalance(int amount) implements Response {
    }

    public record State(int balance) implements CborSerializable {
    }

    public static Behavior<Command> create(String walletId) {
        return Behaviors.setup(context ->
                new EventSourcedBehavior<Command, Event, State>(PersistenceId.of(typeKey.name(), walletId)
                        , SupervisorStrategy.restartWithBackoff(Duration.ofSeconds(10), Duration.ofSeconds(60), 0.1)) {

                    @Override
                    public State emptyState() {
                        return new State(0);
                    }

                    @Override
                    public CommandHandler<Command, Event, State> commandHandler() {
                        return (state, command) -> {
                            if (command instanceof ReserveFunds(int amount, ActorRef<UpdatedResponse> replyTo)) {
                                if (amount <= state.balance) {
                                    return Effect().persist(new FundsReserved(amount))
                                            .thenReply(replyTo, s -> new Accepted());
                                } else {
                                    return Effect().persist(new FundsReservationDenied(amount))
                                            .thenReply(replyTo, s -> new Rejected());
                                }
                            } else if (command instanceof AddFunds(int amount, ActorRef<UpdatedResponse> replyTo)) {
                                return Effect().persist(new FundsAdded(amount))
                                        .thenReply(replyTo, s -> new Accepted());
                            } else if (command instanceof CheckFunds(ActorRef<Response> replyTo)) {
                                return Effect().reply(replyTo, new CurrentBalance(state.balance));
                            }
                            return Effect().none();
                        };
                    }

                    @Override
                    public EventHandler<State, Event> eventHandler() {
                        return (state, event) -> {
                            if (event instanceof FundsReserved(int amount)) {
                                return new State(state.balance - amount);
                            } else if (event instanceof FundsAdded(int amount)) {
                                return new State(state.balance + amount);
                            } else if (event instanceof FundsReservationDenied) {
                                return state;
                            }
                            return state;
                        };
                    }

                    @Override
                    public Set<String> tagsFor(Event event) {
                        return Set.of(calculateTag(walletId, tags));
                    }

                    @Override
                    public RetentionCriteria retentionCriteria() {
                        return RetentionCriteria.snapshotEvery(100, 2);
                    }
                });
    }

    private static final String[] tags = new String[3];

    static {
        for (int i = 0; i < tags.length; i++) {
            tags[i] = "wallet-tag-" + i;
        }
    }

    private static String calculateTag(String entityId, String[] tags) {
        int tagIndex = Math.abs(entityId.hashCode() % tags.length);
        return tags[tagIndex];
    }
}
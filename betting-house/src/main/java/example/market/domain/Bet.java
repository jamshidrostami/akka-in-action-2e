package example.market.domain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.RetentionCriteria;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class Bet {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Bet.class);

    public static final EntityTypeKey<Command> typeKey = EntityTypeKey.create(Command.class, "bet");

    public interface Command {
    }

    public interface ReplyCommand extends Command, CborSerializable {
        ActorRef<Response> replyTo();
    }

    public record Open(String walletId, String marketId, double odds, int stake, int result,
                       ActorRef<Response> replyTo) implements ReplyCommand {
    }

    public record Settle(int result, ActorRef<Response> replyTo) implements ReplyCommand {
    }

    public record Cancel(String reason, ActorRef<Response> replyTo) implements ReplyCommand {
    }

    public record GetState(ActorRef<Response> replyTo) implements ReplyCommand {
    }

    private record MarketOddsAvailable(boolean available, Optional<Double> marketOdds) implements Command {
    }

    private record RequestWalletFunds(Wallet.UpdatedResponse response) implements Command {
    }

    private record ValidationsTimedOut(int seconds) implements Command {
    }

    private record Fail(String reason) implements Command {
    }

    private record Close(String reason) implements Command {
    }

    public interface Response {
    }

    public record Accepted() implements Response {
    }

    public record RequestUnaccepted(String reason) implements Response {
    }

    public record CurrentState(State state) implements Response {
    }

    public record Status(String betId, String walletId, String marketId, double odds, int stake,
                         int result) implements CborSerializable {

        public static Status empty(String marketId) {
            return new Status(marketId, "uninitialized", "uninitialized", -1, -1, 0);
        }
    }

    public interface State extends CborSerializable {
        Status status();
    }

    public record UninitializedState(Status status) implements State {
    }

    public record OpenState(Status status, Optional<Boolean> marketConfirmed,
                            Optional<Boolean> fundsConfirmed) implements State {
        public OpenState copyMarketConfirmed(boolean confirmed) {
            return new OpenState(status, Optional.of(confirmed), fundsConfirmed);
        }

        public OpenState copyFundsConfirmed(boolean confirmed) {
            return new OpenState(status, marketConfirmed, Optional.of(confirmed));
        }
    }

    public record SettledState(Status status) implements State {
    }

    public record CancelledState(Status status) implements State {
    }

    public record FailedState(Status status, String reason) implements State {
    }

    public record ClosedState(Status status) implements State {
    }

    public static Behavior<Command> create(String betId) {
        return Behaviors.withTimers(timers ->
                Behaviors.setup(context -> {
                    ClusterSharding sharding = ClusterSharding.get(context.getSystem());
                    return new EventSourcedBehavior<Command, Event, State>(PersistenceId.of(typeKey.name(), betId)
                            , SupervisorStrategy.restartWithBackoff(
                            Duration.ofSeconds(10), Duration.ofSeconds(60), 0.1)) {

                        @Override
                        public State emptyState() {
                            return new UninitializedState(Status.empty(betId));
                        }

                        @Override
                        public CommandHandler<Command, Event, State> commandHandler() {
                            return (state, command) -> {
                                if (state instanceof UninitializedState(
                                        Status status
                                ) && command instanceof Open open) {
                                    timers.startSingleTimer(
                                            "lifespan",
                                            new ValidationsTimedOut(10), // this would read from configuration
                                            Duration.ofSeconds(10));
                                    Opened opened = new Opened(
                                            status.betId,
                                            open.walletId,
                                            open.marketId,
                                            open.odds,
                                            open.stake,
                                            open.result);
                                    return Effect()
                                            .persist(opened)
                                            .thenRun((State s) -> requestMarketStatus(open, sharding, context))
                                            .thenRun((State s) -> requestFundsReservation(open, sharding, context))
                                            .thenReply(open.replyTo, param -> new Accepted());
                                } else if (state instanceof OpenState openState) {
                                    if (command instanceof MarketOddsAvailable(
                                            boolean available, Optional<Double> marketOdds
                                    )) {
                                        if (available) {
                                            return Effect()
                                                    .persist(new MarketConfirmed(openState));
                                        } else {
                                            return Effect()
                                                    .persist(
                                                            new Failed(
                                                                    state.status().betId,
                                                                    "market odds [" + marketOdds + "] not available"));
                                        }
                                    } else if (command instanceof RequestWalletFunds(Wallet.UpdatedResponse response)) {
                                        if (response instanceof Wallet.Accepted) {
                                            return Effect()
                                                    .persist(new FundsGranted(openState));
                                        } else if (response instanceof Wallet.Rejected) {
                                            return Effect()
                                                    .persist(
                                                            new Failed(state.status().betId, "funds not available"));
                                        } else {
                                            throw new IllegalArgumentException("Unknown response type");
                                        }
                                    } else if (command instanceof ValidationsTimedOut) {
                                        if (openState.marketConfirmed.isPresent() && openState.fundsConfirmed.isPresent() &&
                                                openState.marketConfirmed.get() && openState.fundsConfirmed.get()) {
                                            return Effect()
                                                    .persist(new ValidationsPassed(openState));
                                        } else {
                                            return Effect().persist(
                                                    new Failed(
                                                            state.status().betId,
                                                            "validations didn't passed [" + state + "]"));
                                        }
                                    } else if (command instanceof Settle settle) {
                                        boolean winner = isWinner(openState, settle.result);
                                        if (winner) {
                                            EntityRef<Wallet.Command> entityRef = sharding.entityRefFor(Wallet.typeKey, openState.status().walletId);

                                            context.ask(Wallet.UpdatedResponse.class, entityRef, Duration.ofSeconds(10)
                                                    , param -> new Wallet.AddFunds(openState.status().stake, param),
                                                    (response, failure) -> {
                                                        if (response != null) {
                                                            return new Close(String.format("stake reimbursed to wallet [%s]", entityRef));
                                                        } else {
                                                            String message = String.format("state NOT reimbursed to wallet [%s]. Reason [%s]", entityRef, failure.getMessage());
                                                            context.getLog().error(message);
                                                            return new Fail(message);
                                                        }
                                                    });
                                        } else {
                                            return Effect()
                                                    .none();
                                        }
                                    } else if (command instanceof Close) {
                                        return Effect()
                                                .persist(new Closed());
                                    }
                                }

                                if (command instanceof GetState(ActorRef<Response> replyTo)) {
                                    return Effect()
                                            .none().thenReply(replyTo, ignored -> new CurrentState(state));
                                }

                                if (command instanceof Cancel) {
                                    if (command instanceof ValidationsTimedOut(int seconds)) {
                                        return Effect()
                                                .persist(new Cancelled(state.status().betId, String.format("validation in process when life span expired after [%s] seconds", seconds)));
                                    }
                                    return Effect()
                                            .none();
                                } else if (command instanceof ReplyCommand replyCommand) {
                                    return Effect()
                                            .none().thenReply(replyCommand.replyTo(), ignored -> new RequestUnaccepted(String.format("[%s] has been rejected upon the current state [%s]", replyCommand, state)));
                                } else if (command instanceof Fail) {
                                    return Effect()
                                            .persist(new Failed(state.status().betId, String.format("Reimbursment unsuccessfull. For wallet [%s]", state.status().walletId)));
                                } else {
                                    context.getLog().error(String.format("Invalid command [%s] in state [%s]", command, state));
                                    return Effect()
                                            .none();
                                }
                            };
                        }

                        @Override
                        public EventHandler<State, Event> eventHandler() {
                            return (state, event) -> {
                                if (event instanceof Opened(
                                        String id, String walletId, String marketId, double odds, int stake, int result
                                )) {
                                    return new OpenState(new Status(id, walletId, marketId, odds, stake, result), null, null);
                                } else if (event instanceof MarketConfirmed(OpenState state1)) {
                                    return state1.copyMarketConfirmed(true);
                                } else if (event instanceof FundsGranted(OpenState state1)) {
                                    return state1.copyFundsConfirmed(true);
                                } else if (event instanceof ValidationsPassed) {
                                    return state;
                                } else if (event instanceof Closed) {
                                    return new ClosedState(state.status());
                                } else if (event instanceof Settled settled) {
                                    return new SettledState(state.status());
                                } else if (event instanceof Cancelled cancelled) {
                                    return new CancelledState(state.status());
                                } else if (event instanceof Failed failed) {
                                    return new FailedState(state.status(), failed.reason);
                                }
                                return state;
                            };
                        }

                        @Override
                        public Set<String> tagsFor(Event event) {
                            tags.add(calculateTag(betId, tags));
                            return new HashSet<>(tags);
                        }

                        @Override
                        public RetentionCriteria retentionCriteria() {
                            return RetentionCriteria.snapshotEvery(100, 2);
                        }
                    };
                })
        );
    }

    public interface Event extends CborSerializable {
    }

    public record MarketConfirmed(OpenState state) implements Event {
    }

    public record FundsGranted(OpenState state) implements Event {
    }

    public record ValidationsPassed(OpenState state) implements Event {
    }

    public record Opened(String betId, String walletId, String marketId, double odds, int stake,
                         int result) implements Event {
    }

    public record Settled(String betId) implements Event {
    }

    public record Cancelled(String betId, String reason) implements Event {
    }

    public record Failed(String betId, String reason) implements Event {
    }

    public record Closed() implements Event {
    }

    private static void requestMarketStatus(
            Open command,
            ClusterSharding sharding,
            ActorContext<Command> context) {

        EntityRef<Market.Command> marketRef = sharding.entityRefFor(Market.typeKey, command.marketId);

        context.ask(Market.Response.class, marketRef, Duration.ofSeconds(3), Market.GetState::new
                , (response, failure) -> {
                    if (response instanceof Market.CurrentState(Market.Status status1)) {
                        Match matched = oddsDoMatch(status1, command);
                        return new MarketOddsAvailable(matched.doMatch(), Optional.of(matched.marketOdds()));
                    } else {
                        context.getLog().error(failure.getMessage());
                        return new MarketOddsAvailable(false, Optional.empty());
                    }
                });
    }


    private static void requestFundsReservation(Open command, ClusterSharding sharding, ActorContext<Command> context) {
        EntityRef<Wallet.Command> walletRef = sharding.entityRefFor(Wallet.typeKey, command.walletId);
        ActorRef<Wallet.UpdatedResponse> walletResponseMapper =
                context.messageAdapter(Wallet.UpdatedResponse.class, RequestWalletFunds::new);

        walletRef.tell(new Wallet.ReserveFunds(command.stake, walletResponseMapper));
    }

    private record Match(boolean doMatch, double marketOdds) {
    }

    private static Match oddsDoMatch(Market.Status marketStatus, Open command) {
        logger.debug(String.format("checking marketStatus %s matches requested odds %s", marketStatus, command.odds));
        if (marketStatus.result() == 0) {
            return new Match(marketStatus.odds().draw() >= command.odds, marketStatus.odds().draw());
        } else if (marketStatus.result() == 1) {
            return new Match(marketStatus.odds().winHome() >= command.odds, marketStatus.odds().winHome());
        } else {
            return new Match(marketStatus.odds().winAway() >= command.odds, marketStatus.odds().winAway());
        }
    }

    private static boolean isWinner(State state, int resultFromMarket) {
        return state.status().result == resultFromMarket;
    }

    public static final List<String> tags = new ArrayList<>(3);

    static {
        for (int i = 0; i < 3; i++) {
            tags.add(String.format("bet-tag-%d", i));
        }
    }

    private static String calculateTag(String entityId, List<String> tags) {
        int tagIndex = Math.abs(entityId.hashCode() % tags.size());
        return tags.get(tagIndex);
    }
}
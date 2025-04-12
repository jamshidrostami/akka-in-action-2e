package example.entry;

import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import example.bet.grpc.BetProto;
import example.bet.grpc.BetService;
import example.market.domain.Bet;
import scala.concurrent.ExecutionContext;

import java.time.Duration;
import java.util.concurrent.CompletionStage;


public class BetServiceImplSharding implements BetService {

    private final ClusterSharding sharding;
    private ExecutionContext executionContext;

    public BetServiceImplSharding(ClusterSharding sharding, ExecutionContext executionContext) {
        this.sharding = sharding;
        this.executionContext = executionContext;
        sharding.init(Entity.of(Bet.typeKey, entityContext ->
                Bet.create(entityContext.getEntityId())));
    }

    public CompletionStage<BetProto.BetResponse> cancel(BetProto.CancelMessage in) {
        EntityRef<Bet.Command> bet = sharding.entityRefFor(Bet.typeKey, in.getBetId());

        return bet.<Bet.Response>ask(replyTo -> new Bet.Cancel(in.getReason(), replyTo), Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Bet.Accepted) {
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("initialized")
                                .build();
                    } else if (response instanceof Bet.RequestUnaccepted) {
                        String reason = ((Bet.RequestUnaccepted) response).reason();
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("Bet NOT cancelled because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    public CompletionStage<BetProto.BetResponse> open(BetProto.Bet in) {
        EntityRef<Bet.Command> bet = sharding.entityRefFor(Bet.typeKey, in.getBetId());

        return bet.<Bet.Response>ask(replyTo -> new Bet.Open(in.getWalletId(), in.getMarketId()
                        , in.getOdds(), in.getStake(), in.getResult(), replyTo), Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Bet.Accepted) {
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("initialized")
                                .build();
                    } else if (response instanceof Bet.RequestUnaccepted) {
                        String reason = ((Bet.RequestUnaccepted) response).reason();
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("Bet NOT opened because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    public CompletionStage<BetProto.BetResponse> settle(BetProto.SettleMessage in) {
        EntityRef<Bet.Command> bet = sharding.entityRefFor(Bet.typeKey, in.getBetId());

        return bet.<Bet.Response>ask(replyTo -> new Bet.Settle(in.getResult(), replyTo), Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Bet.Accepted) {
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("initialized")
                                .build();
                    } else if (response instanceof Bet.RequestUnaccepted) {
                        String reason = ((Bet.RequestUnaccepted) response).reason();
                        return BetProto.BetResponse.newBuilder()
                                .setMessage("Bet NOT settled because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    public CompletionStage<BetProto.Bet> getState(BetProto.BetId in) {
        EntityRef<Bet.Command> bet = sharding.entityRefFor(Bet.typeKey, in.getBetId());

        return bet.ask(Bet.GetState::new, Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Bet.CurrentState state) {
                        return BetProto.Bet.newBuilder()
                                .setBetId(state.state().status().betId())
                                .setWalletId(state.state().status().walletId())
                                .setMarketId(state.state().status().marketId())
                                .setOdds(state.state().status().odds())
                                .setStake(state.state().status().stake())
                                .setResult(state.state().status().result())
                                .build();
                    }
                    return null;
                });
    }
}
package example.entry;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.stream.javadsl.Source;
import example.market.domain.Market;
import example.market.grpc.MarketProto;
import example.market.grpc.MarketService;
import scala.concurrent.ExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class MarketServiceImplSharding implements MarketService {

    private final ClusterSharding sharding;
    //    private final ExecutionContext executionContext = ExecutionContext.global();
    private ExecutionContext executionContext;

    public MarketServiceImplSharding(ClusterSharding sharding, ExecutionContext executionContext) {
        this.sharding = sharding;
        this.executionContext = executionContext;
        sharding.init(Entity.of(Market.typeKey, entityContext ->
                Market.create(entityContext.getEntityId())));
    }

    @Override
    public CompletionStage<MarketProto.Response> cancel(MarketProto.CancelMarket in) {
        EntityRef<Market.Command> market = sharding.entityRefFor(Market.typeKey, in.getMarketId());

        return market.<Market.Response>ask(replyTo -> new Market.Cancel(in.getReason(), replyTo)
                        , Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Market.Accepted) {
                        return MarketProto.Response.newBuilder()
                                .setMessage("initialized")
                                .build();
                    } else if (response instanceof Market.RequestUnaccepted) {
                        String reason = ((Market.RequestUnaccepted) response).reason();
                        return MarketProto.Response.newBuilder()
                                .setMessage("market NOT cancelled because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    @Override
    public CompletionStage<MarketProto.Response> closeMarket(MarketProto.MarketId in) {
        EntityRef<Market.Command> market = sharding.entityRefFor(Market.typeKey, in.getMarketId());
        return market.ask(Market.Close::new
                        , Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Market.Accepted) {
                        return MarketProto.Response.newBuilder()
                                .setMessage("initialized")
                                .build();
                    } else if (response instanceof Market.RequestUnaccepted) {
                        String reason = ((Market.RequestUnaccepted) response).reason();
                        return MarketProto.Response.newBuilder()
                                .setMessage("market NOT closed because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    @Override
    public CompletionStage<MarketProto.MarketData> getState(MarketProto.MarketId in) {
        EntityRef<Market.Command> market = sharding.entityRefFor(Market.typeKey, in.getMarketId());
        return market.ask(Market.GetState::new
                        , Duration.ofSeconds(3))
                .thenApply(state -> {
                    Market.CurrentState currentState = (Market.CurrentState) state;

                    String marketId = currentState.status().marketId();
                    Market.Fixture fixture = currentState.status().fixture();
                    Market.Odds odds = currentState.status().odds();

                    return MarketProto.MarketData.newBuilder()
                            .setMarketId(marketId)
                            .setFixture(fixture != null ? MarketProto.FixtureData.newBuilder()
                                    .setId(fixture.id())
                                    .setHomeTeam(fixture.homeTeam())
                                    .setAwayTeam(fixture.awayTeam())
                                    : null)
                            .setOdds(odds != null ? MarketProto.OddsData.newBuilder()
                                    .setWinHome(odds.winHome())
                                    .setWinAway(odds.winAway())
                                    .setTie(odds.draw())
                                    : null)
                            .build();
                });
    }

    @Override
    public CompletionStage<MarketProto.Response> open(MarketProto.MarketData in) {
        EntityRef<Market.Command> market = sharding.entityRefFor(Market.typeKey, in.getMarketId());

        return market.<Market.Response>ask(replyTo -> {
                    Market.Fixture fixture;
                    if (in.getFixture() != null) {
                        fixture = new Market.Fixture(in.getFixture().getId()
                                , in.getFixture().getHomeTeam()
                                , in.getFixture().getAwayTeam());
                    } else {
                        throw new IllegalArgumentException("Fixture is empty. Not allowed");
                    }

                    Market.Odds odds;
                    if (in.getOdds() != null) {
                        odds = new Market.Odds(in.getOdds().getWinHome(), in.getOdds().getWinAway(), in.getOdds().getTie());
                    } else {
                        throw new IllegalArgumentException("Odds are empty. Not allowed");
                    }

                    OffsetDateTime opensAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(in.getOpensAt()), ZoneId.of("UTC"));
                    return new Market.Open(fixture, odds, opensAt, replyTo);
                }, Duration.ofSeconds(3))
                .thenApply(response -> {
                    if (response instanceof Market.Accepted) {
                        return MarketProto.Response.newBuilder().setMessage("initialized")
                                .build();
                    } else if (response instanceof Market.RequestUnaccepted) {
                        String reason = ((Market.RequestUnaccepted) response).reason();
                        return MarketProto.Response.newBuilder().setMessage("market NOT initialized because [" + reason + "]")
                                .build();
                    }
                    return null;
                });
    }

    public Source<MarketProto.Response, NotUsed> update(
            Source<MarketProto.MarketData, NotUsed> in) {

        return in.mapAsync(10, marketData -> {
            EntityRef<Market.Command> marketRef =
                    sharding.entityRefFor(Market.typeKey, marketData.getMarketId());

            return marketRef.<Market.Response>ask(replyTo -> auxUpdate(marketData, replyTo)
                            , Duration.ofSeconds(3))
                    .thenApply(response -> {
                        if (response instanceof Market.Accepted) {
                            return MarketProto.Response.newBuilder().setMessage("Updated")
                                    .build();
                        } else if (response instanceof Market.RequestUnaccepted) {
                            String reason = ((Market.RequestUnaccepted) response).reason();
                            return MarketProto.Response.newBuilder().setMessage("market NOT updated because [" + reason + "]")
                                    .build();
                        }
                        return null; // Handle other cases if necessary
                    });
        });
    }

    private Market.Update auxUpdate(MarketProto.MarketData marketData, ActorRef<Market.Response> replyTo) {
        MarketProto.OddsData m = marketData.getOdds();
        Optional<Market.Odds> odds = Optional.of(new Market.Odds(m.getWinHome(), m.getWinAway(), m.getTie()));

        Optional<OffsetDateTime> opensAt = Optional.of(
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(marketData.getOpensAt()), ZoneId.of("UTC"))
        );

        Optional<Integer> result = Optional.of(marketData.getResult().getNumber());

        return new Market.Update(odds, opensAt, result, replyTo);
    }
}
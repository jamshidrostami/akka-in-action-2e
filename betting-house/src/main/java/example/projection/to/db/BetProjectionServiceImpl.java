package example.projection.to.db;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.DispatcherSelector;
import betting.house.projection.proto.BetProjectionProto;
import betting.house.projection.proto.BetProjectionService;
import example.projection.dbconnection.QueryDSLJdbcSession;
import example.projection.dbconnection.QueryDSLUtil;
import scala.concurrent.ExecutionContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BetProjectionServiceImpl implements BetProjectionService {

    private final ExecutionContext jdbcExecutor;
    private final BetRepository betRepository;
    Supplier<QueryDSLJdbcSession> queryDSLJdbcSessionCreator;

    public BetProjectionServiceImpl(ActorSystem<?> system, BetRepository betRepository) {
        this.jdbcExecutor = system.dispatchers().lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"));
        this.betRepository = betRepository;

        queryDSLJdbcSessionCreator = () -> new QueryDSLJdbcSession(QueryDSLUtil.getDataSource(system.settings().config()));
    }

    public CompletionStage<BetProjectionProto.SumStakes> getBetByMarket(BetProjectionProto.MarketIdsBet in) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                QueryDSLJdbcSession session = queryDSLJdbcSessionCreator.get();
                List<BetProjectionProto.SumStake> sumStakes = betRepository.getBetPerMarketTotalStake(in.getMarketId(), session)
                        .stream()
                        .map(each -> BetProjectionProto.SumStake.newBuilder()
                                .setTotal(each.getSum())
                                .setResult(each.getResult())
                                .build())
                        .collect(Collectors.toList());
                return BetProjectionProto.SumStakes.newBuilder()
                        .addAllSumstakes(sumStakes)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
//        return CompletableFuture.supplyAsync(() -> QueryDSLJdbcSessionFactory.withSession(null, session -> {
//            try {
//                List<BetProjectionProto.SumStake> sumStakes = betRepository.getBetPerMarketTotalStake(in.getMarketId(), session)
//                        .stream()
//                        .map(each -> BetProjectionProto.SumStake.newBuilder()
//                                .setTotal(each.sum())
//                                .setResult(each.result())
//                                .build())
//                        .collect(Collectors.toList());
//                return BetProjectionProto.SumStakes.newBuilder()
//                        .addAllSumstakes(sumStakes)
//                        .build();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }

//            return null;
        });
    }
}
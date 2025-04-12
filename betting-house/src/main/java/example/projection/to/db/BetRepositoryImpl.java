package example.projection.to.db;

import com.querydsl.core.types.Projections;
import example.projection.dbconnection.QueryDSLJdbcSession;

import java.util.List;

import static example.projection.to.db.QBetWalletMarket.betWalletMarket;

public class BetRepositoryImpl implements BetRepository {

    @Override
    public void addBet(String betId, String walletId, String marketId, double odds,
                       int stake, int result, QueryDSLJdbcSession session) throws Exception {
        session.withConnection(connection -> {
            String id = session.getQueryFactory().select(betWalletMarket.betId)
                    .from(betWalletMarket)
                    .where(betWalletMarket.betId.eq(betId))
                    .fetchFirst();

            if (id == null) {
                return session.getQueryFactory()
                        .insert(betWalletMarket)
                        .set(betWalletMarket.betId, betId)
                        .set(betWalletMarket.walletId, walletId)
                        .set(betWalletMarket.marketId, marketId)
                        .set(betWalletMarket.odds, odds)
                        .set(betWalletMarket.stake, stake)
                        .set(betWalletMarket.result, result)
                        .execute();
            } else {
                return session.getQueryFactory()
                        .update(betWalletMarket)
                        .set(betWalletMarket.walletId, walletId)
                        .set(betWalletMarket.marketId, marketId)
                        .set(betWalletMarket.odds, odds)
                        .set(betWalletMarket.stake, stake)
                        .set(betWalletMarket.result, result)
                        .where(betWalletMarket.betId.eq(betId))
                        .execute();
            }
        });
    }

    @Override
    public List<StakePerResult> getBetPerMarketTotalStake(String marketId,
                                                          QueryDSLJdbcSession session) throws Exception {
        return session.withConnection(connection -> session.getQueryFactory()
                .select(Projections.bean(StakePerResult.class
                        , (betWalletMarket.odds.multiply(betWalletMarket.stake)).sum().as("sum")
                        , betWalletMarket.result))
                .from(betWalletMarket)
                .where(betWalletMarket.marketId.eq(marketId))
                .groupBy(betWalletMarket.marketId, betWalletMarket.result)
                .fetch());
    }
}
package example.projection.to.db;

import example.projection.dbconnection.QueryDSLJdbcSession;

import java.util.List;

public interface BetRepository {

    void addBet(String betId, String walletId, String marketId, double odds,
                int stake, int result, QueryDSLJdbcSession session) throws Exception;

    List<StakePerResult> getBetPerMarketTotalStake(String marketId,
                                                   QueryDSLJdbcSession session) throws Exception;
}


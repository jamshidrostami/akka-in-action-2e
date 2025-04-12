package example.projection.to.db;

import akka.projection.eventsourced.EventEnvelope;
import akka.projection.jdbc.javadsl.JdbcHandler;
import example.market.domain.Bet;
import example.projection.dbconnection.QueryDSLJdbcSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetProjectionHandler extends JdbcHandler<EventEnvelope<Bet.Event>, QueryDSLJdbcSession> {

    private final Logger logger = LoggerFactory.getLogger(BetProjectionHandler.class);
    private final BetRepository repository;

    public BetProjectionHandler(BetRepository repository) {
        this.repository = repository;
    }

    @Override
    public void process(QueryDSLJdbcSession session, EventEnvelope<Bet.Event> envelope) {
        Bet.Event event = envelope.event();
        if (event instanceof Bet.Opened openedEvent) {
            try {
                repository.addBet(
                        openedEvent.betId(),
                        openedEvent.walletId(),
                        openedEvent.marketId(),
                        openedEvent.odds(),
                        openedEvent.stake(),
                        openedEvent.result(),
                        session
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            logger.debug("ignoring event {} in projection", event);
        }
    }
}
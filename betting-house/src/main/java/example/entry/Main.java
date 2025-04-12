package example.entry;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.management.javadsl.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import example.projection.server.BetProjectionServer;
import example.projection.to.db.BetProjection;
import example.projection.to.db.BetRepositoryImpl;
import example.projection.to.kafka.MarketProjection;
import example.server.BetServiceServer;
import example.server.MarketServiceServer;
import example.server.WalletServiceServer;
import scala.concurrent.ExecutionContext;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.control.NonFatal;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "betting-house");
        try {
            ClusterSharding sharding = ClusterSharding.get(system);
            ExecutionContext ec = system.executionContext();

            AkkaManagement.get(system).start();
            ClusterBootstrap.get(system).start();
//            QueryDSLSetup.init(system);

            BetServiceServer.init(system, sharding, ec);
            MarketServiceServer.init(system, sharding, ec);
            WalletServiceServer.init(system, sharding, ec);

            BetRepositoryImpl betRepository = new BetRepositoryImpl();
            BetProjectionServer.init(betRepository, system);
            BetProjection.init(system, betRepository);
            MarketProjection.init(system);
        } catch (Throwable ex) {
            if (NonFatal.apply(ex)) {
                log.error("Terminating Betting App. Reason [{}]", ex.getMessage());
            }
            system.terminate();
        }
    }
}
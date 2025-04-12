package example.projection.server;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import betting.house.projection.proto.BetProjectionServiceHandlerFactory;
import example.projection.to.db.BetProjectionServiceImpl;
import example.projection.to.db.BetRepository;

public class BetProjectionServer {

    public static void init(BetRepository repository, ActorSystem<?> system) {
        var service = BetProjectionServiceHandlerFactory.createWithServerReflection(
                new BetProjectionServiceImpl(system, repository), system);

        int port = system.settings().config().getInt("services.bet-projection.port");
        String host = system.settings().config().getString("services.host");

        Http.get(system).newServerAt(host, port).bind(service);
    }
}
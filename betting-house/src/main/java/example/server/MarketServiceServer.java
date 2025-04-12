package example.server;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.ServerBinding;
import akka.japi.function.Function;
import example.entry.MarketServiceImplSharding;
import example.market.grpc.MarketServiceHandlerFactory;
import scala.concurrent.ExecutionContext;

import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.Http.get;

public class MarketServiceServer {

    public static CompletionStage<ServerBinding> init(
            ActorSystem<?> system,
            akka.cluster.sharding.typed.javadsl.ClusterSharding sharding,
            ExecutionContext ec) {
        Function<akka.http.javadsl.model.HttpRequest, CompletionStage<akka.http.javadsl.model.HttpResponse>> marketService =
                MarketServiceHandlerFactory.createWithServerReflection(new MarketServiceImplSharding(sharding, ec), system);


        int port = system.settings().config().getInt("services.market.port");
        String host = system.settings().config().getString("services.host");

        return get(system.classicSystem())
                .newServerAt(host, port)
                .bind(marketService);
    }
}
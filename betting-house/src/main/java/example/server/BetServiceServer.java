package example.server;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import example.bet.grpc.BetServiceHandlerFactory;
import example.entry.BetServiceImplSharding;
import scala.concurrent.ExecutionContext;

import java.util.concurrent.CompletionStage;

public class BetServiceServer {

    public static CompletionStage<ServerBinding> init(
            ActorSystem<?> system,
            ClusterSharding sharding,
            ExecutionContext ec) {
        Function<HttpRequest, CompletionStage<HttpResponse>> betService =
                BetServiceHandlerFactory.createWithServerReflection(new BetServiceImplSharding(sharding, ec), system);


        int port = system.settings().config().getInt("services.bet.port");
        String host = system.settings().config().getString("services.host");

        return Http.get(system.classicSystem())
                .newServerAt(host, port)
                .bind(betService);
    }
}
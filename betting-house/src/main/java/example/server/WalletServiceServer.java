package example.server;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import example.entry.WalletService;
import scala.concurrent.ExecutionContext;

import java.util.concurrent.CompletionStage;

public class WalletServiceServer {

    public static CompletionStage<ServerBinding> init(
            ActorSystem<Void> system,
            ClusterSharding sharding,
            ExecutionContext ec) {

        int port = system.settings().config().getInt("services.wallet.port");
        String host = system.settings().config().getString("services.host");

        return Http.get(system.classicSystem())
                .newServerAt(host, port)
                .bind(new WalletService(sharding).route());
    }
}
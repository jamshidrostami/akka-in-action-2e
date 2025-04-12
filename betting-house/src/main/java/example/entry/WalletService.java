package example.entry;


import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import example.market.domain.Wallet;
import scala.concurrent.ExecutionContext;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static akka.http.javadsl.server.Directives.*;

public class WalletService {

    private final ClusterSharding sharding;
    private final ExecutionContext executionContext;

    public WalletService(ClusterSharding sharding) {
        this.sharding = sharding;
        this.executionContext = ExecutionContext.global();

        sharding.init(Entity.of(Wallet.typeKey, entityContext -> Wallet.create(entityContext.getEntityId())));
    }

    public Route route() {
        return pathPrefix("wallet", () ->
                concat(
                        path("add", () ->
                                post(() ->
                                        parameter("walletId", walletId ->
                                                parameter("funds", fundsStr -> {
                                                    int funds = Integer.parseInt(fundsStr);

                                                    EntityRef<Wallet.Command> wallet =
                                                            sharding.entityRefFor(Wallet.typeKey, walletId);

                                                    try {
                                                        return wallet.<Wallet.UpdatedResponse>ask(replyTo ->
                                                                        new Wallet.AddFunds(funds, replyTo), Duration.ofSeconds(5))
                                                                .thenApply(updatedResponse -> {
                                                                    if (updatedResponse instanceof Wallet.Accepted) {
                                                                        return complete(StatusCodes.ACCEPTED);
                                                                    } else {
                                                                        return complete(StatusCodes.NOT_ACCEPTABLE);
                                                                    }
                                                                })
                                                                .toCompletableFuture()
                                                                .get();
                                                    } catch (InterruptedException | ExecutionException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                })
                                        )
                                )
                        ),
                        path("remove", () ->
                                post(() ->
                                        parameter("walletId", walletId ->
                                                parameter("funds", fundsStr -> {
                                                    EntityRef<Wallet.Command> wallet =
                                                            sharding.entityRefFor(Wallet.typeKey, walletId);

                                                    int funds = Integer.parseInt(fundsStr);
                                                    try {
                                                        return wallet.<Wallet.UpdatedResponse>ask(replyTo -> new Wallet.ReserveFunds(funds, replyTo)
                                                                        , Duration.ofSeconds(5))
                                                                .thenApply(response -> {
                                                                    if (response instanceof Wallet.Accepted) {
                                                                        return complete(StatusCodes.ACCEPTED);
                                                                    } else if (response instanceof Wallet.Rejected) {
                                                                        return complete(StatusCodes.BAD_REQUEST, "not enough funds in the wallet");
                                                                    } else {
                                                                        return null; // Replace with appropriate response for other cases
                                                                    }
                                                                })
                                                                .toCompletableFuture()
                                                                .get();
                                                    } catch (InterruptedException | ExecutionException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                })
                                        )
                                )
                        ),
                        get(() ->
                                parameter("walletId", walletId -> {
                                    EntityRef<Wallet.Command> wallet =
                                            sharding.entityRefFor(Wallet.typeKey, walletId);

                                    try {
                                        return wallet.<Wallet.Response>ask(Wallet.CheckFunds::new, Duration.ofSeconds(5))
                                                .thenApply(response -> {
                                                    if (response instanceof Wallet.CurrentBalance currentBalance) {
                                                        return complete(StatusCodes.OK, currentBalance, Jackson.marshaller());
                                                    } else {
                                                        return complete(StatusCodes.NOT_ACCEPTABLE);
                                                    }
                                                })
                                                .toCompletableFuture()
                                                .get();
                                    } catch (InterruptedException | ExecutionException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        )
                )
        );
    }
}
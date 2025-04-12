package example.projection.to.db;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;

import javax.annotation.processing.Generated;
import java.sql.Types;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;


/**
 * QQuestion is a Querydsl query type for QQuestion
 */
@Generated("com.querydsl.sql.codegen.MetaDataSerializer")
public class QBetWalletMarket extends com.querydsl.sql.RelationalPathBase<QBetWalletMarket> {

    private static final long serialVersionUID = 1853831281;

    public static final QBetWalletMarket betWalletMarket = new QBetWalletMarket("bet_wallet_market");


    public final StringPath betId = createString("betId");
    public final StringPath walletId = createString("walletId");
    public final StringPath marketId = createString("marketId");

    public final NumberPath<Double> odds = createNumber("odds", Double.class);
    public final NumberPath<Integer> stake = createNumber("stake", Integer.class);
    public final NumberPath<Integer> result = createNumber("result", Integer.class);

    public final com.querydsl.sql.PrimaryKey<QBetWalletMarket> questionPkey = createPrimaryKey(betId);


    public QBetWalletMarket(String variable) {
        super(QBetWalletMarket.class, forVariable(variable), "public", "bet_wallet_market");
        addMetadata();
    }

    public QBetWalletMarket(String variable, String schema, String table) {
        super(QBetWalletMarket.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QBetWalletMarket(String variable, String schema) {
        super(QBetWalletMarket.class, forVariable(variable), schema, "bet_wallet_market");
        addMetadata();
    }

    public QBetWalletMarket(Path<? extends QBetWalletMarket> path) {
        super(path.getType(), path.getMetadata(), "public", "bet_wallet_market");
        addMetadata();
    }

    public QBetWalletMarket(PathMetadata metadata) {
        super(QBetWalletMarket.class, metadata, "public", "bet_wallet_market");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(betId, ColumnMetadata.named("betid").withIndex(1).ofType(Types.VARCHAR).withSize(50));
        addMetadata(walletId, ColumnMetadata.named("walletid").withIndex(2).ofType(Types.VARCHAR).withSize(50));
        addMetadata(marketId, ColumnMetadata.named("marketid").withIndex(3).ofType(Types.VARCHAR).withSize(50));
        addMetadata(odds, ColumnMetadata.named("odds").withIndex(4).ofType(Types.DOUBLE).withSize(2147483647));
        addMetadata(stake, ColumnMetadata.named("stake").withIndex(5).ofType(Types.INTEGER).withSize(2147483647));
        addMetadata(result, ColumnMetadata.named("result").withIndex(6).ofType(Types.INTEGER).withSize(2147483647));
    }

}


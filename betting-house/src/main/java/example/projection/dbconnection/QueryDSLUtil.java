package example.projection.dbconnection;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariDataSource;

public class QueryDSLUtil {

    public static HikariDataSource getDataSource(Config config) {
        return buildDataSource(
                config.getConfig("jdbc-connection-settings"));
    }

    private static HikariDataSource buildDataSource(Config config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("read-side-frasua-connection-pool");
        dataSource.setMaximumPoolSize(config.getInt("connection-pool.max-pool-size"));

        long timeout = config.getDuration("connection-pool.timeout").toMillis();
        dataSource.setConnectionTimeout(timeout);
        dataSource.setDriverClassName(config.getString("driver"));
        dataSource.setJdbcUrl(config.getString("url"));
        dataSource.setUsername(config.getString("user"));
        dataSource.setPassword(config.getString("password"));
        dataSource.setAutoCommit(false);

        return dataSource;
    }
}
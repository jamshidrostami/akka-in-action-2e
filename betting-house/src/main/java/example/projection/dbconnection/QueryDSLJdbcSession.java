package example.projection.dbconnection;

import akka.japi.function.Function;
import akka.projection.jdbc.JdbcSession;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.PostgreSQLTemplates;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.types.JSR310InstantType;
import com.querydsl.sql.types.JSR310LocalDateTimeType;
import com.querydsl.sql.types.JSR310LocalDateType;
import com.querydsl.sql.types.JSR310LocalTimeType;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class QueryDSLJdbcSession implements JdbcSession {
    private final HikariDataSource dataSource;
    private final SQLQueryFactory queryFactory;
    private Connection connection;

    public QueryDSLJdbcSession(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        Configuration configuration = this.querydslConfiguration(new PostgreSQLTemplates());
        this.queryFactory = new SQLQueryFactory(configuration, this::getConnection);
    }

    private Configuration querydslConfiguration(SQLTemplates templates) {
        Configuration configuration = new Configuration(templates);
        configuration.register(new JSR310InstantType());
        configuration.register(new JSR310LocalDateTimeType());
        configuration.register(new JSR310LocalDateType());
        configuration.register(new JSR310LocalTimeType());

        return configuration;
    }

    @Override
    public <Result> Result withConnection(Function<Connection, Result> func) throws Exception {
        if (connection == null || connection.isClosed()) {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
        }
        return func.apply(connection);
    }

    @Override
    public void commit() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close(); // Returns to HikariCP pool
        }
    }

    public SQLQueryFactory getQueryFactory() {
        return queryFactory;
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = dataSource.getConnection();
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO
        }
    }
}
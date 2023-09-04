package io.tapdata.sybase.extend;

import io.tapdata.common.CommonDbConfig;
import io.tapdata.common.ResultSetConsumer;
import io.tapdata.connector.mysql.MysqlJdbcContextV2;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.simplify.TapSimplify;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.kit.DbKit;
import io.tapdata.kit.EmptyKit;
import io.tapdata.kit.StringKit;
import io.tapdata.sybase.util.ConnectorUtil;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.tapdata.entity.simplify.TapSimplify.list;

/**
 * @author GavinXiao
 * @description SybaseContext create by Gavin
 * @create 2023/7/10 15:47
 **/
public class SybaseContext extends MysqlJdbcContextV2 {
    public static final String TAG = SybaseContext.class.getSimpleName();
    private final static String SYBASE_VERSION = "select @@version";
    //select table cloumns
    private final static String SELECT_TABLE_INFO = "select col.colid as id," +
            "obj.name as tableName," +
            "col.name as columnName," +
            "typ.name as dataType," +
            "col.length as all_length," +
            "col.prec as length," +
            "col.scale," +
            "case isnull(col.status,0) when 0 then 'NOT NULL' ELSE 'NULL' END AS nullable\n" +
            "from syscolumns col,sysobjects obj,systypes typ\n" +
            "where col.id=obj.id and col.usertype=typ.usertype %s";

    //select all table and table'name
    private final static String SELECT_ALL_TABLE_NAME = "select obj.name from sysobjects as obj, sysusers as ou  where obj.type = 'U' AND ou.uid = obj.uid and ou.name = '%s' %s";
    /**
     * @deprecated
     * */
    private final static String SHOW_TABLE_CONFIG = "sp_helpindex N'%s'";
    public SybaseContext(CommonDbConfig config) {
        super(config);
    }

    @Override
    public String queryVersion() throws SQLException {
        AtomicReference<String> version = new AtomicReference<>();
        queryWithNext(SYBASE_VERSION, resultSet -> version.set(resultSet.getString(1)));
        return version.get();
    }

    public Connection getConnection() throws SQLException {
        CommonDbConfig config = getConfig();
        try {
            Class.forName(SybaseConfig.JDBC_SYBASE_DRIVER);
            Properties properties = config.getProperties();
//            properties.put("dbProductName", properties.getProperty("schema"));
//            properties.put("_dbProductName", properties.getProperty("schema"));
            return DriverManager.getConnection(config.getDatabaseUrl(), properties);
        } catch (SQLException e) {
            exceptionCollector.collectUserPwdInvalid(getConfig().getUser(), e);
            throw e;
        } catch (ClassNotFoundException foundException) {
            foundException.printStackTrace();
            throw new CoreException(foundException.getMessage());
        }
    }


    public void queryAllTables(List<String> tableNames, int batchSize, Consumer<List<String>> consumer) throws SQLException {
        List<String> temp = list();
        query(queryAllTablesSql(getConfig().getSchema(), tableNames),
                resultSet -> {
                    while (resultSet.next()) {
                        String tableName = resultSet.getString("name");
                        if (EmptyKit.isNotBlank(tableName)) {
                            temp.add(tableName);
                        }
                        if (temp.size() >= batchSize) {
                            consumer.accept(temp);
                            temp.clear();
                        }
                    }
                });
        if (EmptyKit.isNotEmpty(temp)) {
            consumer.accept(temp);
            temp.clear();
        }
    }

    /**
     * query tableNames and Comments from one database and one schema
     *
     * @param tableNames some tables(all tables if tableName is empty or null)
     * @return List<TableName and Comments>
     */
    public List<DataMap> queryAllTables(List<String> tableNames) throws SQLException {
        List<DataMap> tableList = list();
        CommonDbConfig config = getConfig();
        query(queryAllTablesInfoSql(config.getSchema(), tableNames),
                resultSet -> tableList.addAll(DbKit.getDataFromResultSet(resultSet)));
        return tableList;
//        List<String> tables = verifyTables(config.getUser());
//        return tableList.stream().filter(t -> null != t && null != t.get("tableName") && tables.contains(String.valueOf(t.get("tableName")))).collect(Collectors.toList());
    }

    protected String queryAllTablesSql(String schema, List<String> tableNames) {
        String tableSql = EmptyKit.isNotEmpty(tableNames) ? "AND obj.name IN (" + StringKit.joinString(tableNames, "'", ",") + ")" : "";
        return String.format(SELECT_ALL_TABLE_NAME, schema,  tableSql);// schema, tableSql);
    }

    protected String queryAllTablesInfoSql(String schema, List<String> tableNames) {
        String tableSql = EmptyKit.isNotEmpty(tableNames) ? tableNames.size() == 1 ? "AND obj.name = '" + tableNames.get(0) + "'" : "AND obj.name IN (" + StringKit.joinString(tableNames, "'", ",") + ")" : "";
        return String.format(SELECT_TABLE_INFO, tableSql);
    }

    /**
     * query all column info from some tables
     *
     * @param tableNames some tables(all tables if tableName is empty or null)
     * @return List<column info>
     */
    public List<DataMap> queryAllColumns(List<String> tableNames) throws SQLException {
//        List<DataMap> columnList = list();
//        query(queryAllColumnsSql(getConfig().getSchema(), tableNames),
//                resultSet -> columnList.addAll(DbKit.getDataFromResultSet(resultSet)));
//        return columnList;
        return queryAllTables(tableNames);
    }

    /**
     * query all index info from some tables
     *
     * @param tableNames some tables(all tables if tableName is empty or null)
     * @return List<index info>
     */
    public List<DataMap> queryAllIndexes(List<String> tableNames) throws SQLException {
        List<DataMap> columnList = list();
        CommonDbConfig config = getConfig();
        tableNames.stream().filter(Objects::nonNull).forEach(table -> {
            DataMap addInRow = new DataMap();
            try {
                addInRow.put("tableName", table);
                call("{call sp_helpindex(?)}", statement -> {
                    statement.setString(1, config.getSchema() + "." + table);
                }, resultSet -> {
                    columnList.addAll(getDataFromResultSet(resultSet, addInRow));
                });
            } catch (Exception e) {
                TapLogger.warn(TAG, e.getMessage());
            }
        });
        return columnList;
    }

    public List<DataMap> queryAllIndexes0(List<String> tableNames) throws SQLException {
        List<DataMap> columnList = list();
        CommonDbConfig config = getConfig();
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CopyOnWriteArraySet<List<String>> tableLists = new CopyOnWriteArraySet<>(DbKit.splitToPieces(new ArrayList<>(tableNames.stream().filter(Objects::nonNull).collect(Collectors.toList())), 2));

        try {
            for (int index = 0; index < 2; index++) {
                executorService.submit(() -> {
                    try {
                        List<String> subTableNames;
                        while ((subTableNames = ConnectorUtil.getOutTableList(tableLists)) != null) {
                            subTableNames.forEach(table -> {
                                DataMap addInRow = new DataMap();
                                try {
                                    addInRow.put("tableName", table);
                                    //query(queryAllIndexesSql(table), resultSet -> {
                                    //    columnList.addAll(getDataFromResultSet(resultSet, addInRow));
                                    //});
                                    call("{call sp_helpindex(?)}", statement -> {
                                        statement.setString(1, config.getSchema() + "." + table);
                                    }, resultSet -> {
                                        columnList.addAll(getDataFromResultSet(resultSet, addInRow));
                                    });
                                } catch (Exception e) {
                                    TapLogger.warn(TAG, e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        throwable.set(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (EmptyKit.isNotNull(throwable.get())) {
                throw new RuntimeException(throwable.get());
            }
        } finally {
            executorService.shutdown();
        }
        return columnList;
    }

    interface CallableSetConsumer {
        void set(CallableStatement statement) throws SQLException;
    }

    private void call(String callableSql, CallableSetConsumer setConsumer, ResultSetConsumer resultSetConsumer) throws SQLException {
        CallableStatement statement = null;
        ResultSet resultSet = null;
        try(Connection connection = getConnection()) {
//            Class.forName("com.sybase.jdbc4.jdbc.SybDriver");
            CommonDbConfig config = getConfig();
//            connection = DriverManager.getConnection(config.getDatabaseUrl(), config.getUser(), config.getPassword());
            statement = connection.prepareCall(callableSql);
            setConsumer.set(statement);
            resultSet = statement.executeQuery();
            if (null != resultSet) {
                resultSetConsumer.accept(resultSet);
            }
        } catch (SQLException e) {
            if (!"JZ0R2".equals(e.getSQLState())) {
                throw e;
            }
        } finally {
            // 关闭资源
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ignore) {
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public static List<DataMap> getDataFromResultSet(ResultSet resultSet, DataMap addInRow) throws SQLException {
        List<DataMap> list = TapSimplify.list();
        if (EmptyKit.isNotNull(resultSet)) {
            List<String> columnNames = DbKit.getColumnsFromResultSet(resultSet);
            //cannot replace with while resultSet.next()
            while (resultSet.next()) {
                DataMap rowFromResultSet = DbKit.getRowFromResultSet(resultSet, columnNames);
                if (null != addInRow && !addInRow.isEmpty()) rowFromResultSet.putAll(addInRow);
                list.add(rowFromResultSet);
            }
        }
        return list;
    }

    protected String queryAllIndexesSql(String tableName) {
        CommonDbConfig config = getConfig();
        return String.format(SHOW_TABLE_CONFIG, config.getSchema() + "." + tableName);
    }

    protected String queryAllIndexesSql(String schema, List<String> tableNames) {
        String tableSql = EmptyKit.isNotEmpty(tableNames) ? tableNames.size() == 1 ? "AND obj.name = '" + schema + "." + tableNames.get(0) + "'" : "AND obj.name IN (" + StringKit.joinString(tableNames.stream().map(s -> schema + "." + s).collect(Collectors.toList()), "'", ",") + ")" : "";
        return String.format(SHOW_TABLE_CONFIG, tableSql);
    }

    protected List<String> verifyTables(String username) throws SQLException {
        List<DataMap> tableList = list();
        query("sp_help",
                resultSet -> tableList.addAll(DbKit.getDataFromResultSet(resultSet)));
        Map<String, List<DataMap>> owner = tableList.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(data -> String.valueOf(data.get("Owner"))));
        List<DataMap> dataMaps = owner.get(username);
        return dataMaps.stream().filter(Objects::nonNull).map(map -> String.valueOf(map.get("Name"))).collect(Collectors.toList());
    }
}

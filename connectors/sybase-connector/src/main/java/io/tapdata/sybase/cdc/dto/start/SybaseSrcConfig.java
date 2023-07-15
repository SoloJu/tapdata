package io.tapdata.sybase.cdc.dto.start;

/**
 * @author GavinXiao
 * @description TaskCDCConfig create by Gavin
 * @create 2023/7/12 18:52
 **/
public class SybaseSrcConfig {
    private String type;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    private int max_connections;
    private int max_retries;
    private int retry_wait_duration_ms;
    private String transaction_store_location;
    private int transaction_store_cache_limit;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getMax_connections() {
        return max_connections;
    }

    public void setMax_connections(int max_connections) {
        this.max_connections = max_connections;
    }

    public int getMax_retries() {
        return max_retries;
    }

    public void setMax_retries(int max_retries) {
        this.max_retries = max_retries;
    }

    public int getRetry_wait_duration_ms() {
        return retry_wait_duration_ms;
    }

    public void setRetry_wait_duration_ms(int retry_wait_duration_ms) {
        this.retry_wait_duration_ms = retry_wait_duration_ms;
    }

    public String getTransaction_store_location() {
        return transaction_store_location;
    }

    public void setTransaction_store_location(String transaction_store_location) {
        this.transaction_store_location = transaction_store_location;
    }

    public int getTransaction_store_cache_limit() {
        return transaction_store_cache_limit;
    }

    public void setTransaction_store_cache_limit(int transaction_store_cache_limit) {
        this.transaction_store_cache_limit = transaction_store_cache_limit;
    }
}
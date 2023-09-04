package io.tapdata.sybase.cdc.dto.start;

import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * @author GavinXiao
 * @description SybaseExtConfig create by Gavin
 * @create 2023/7/18 11:29
 **/
public class SybaseExtConfig implements ConfigEntity {
    Snapshot snapshot;
    Realtime realtime;

    public SybaseExtConfig() {
        snapshot = new Snapshot();
        realtime = new Realtime();
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public void setRealtime(Realtime realtime) {
        this.realtime = realtime;
    }

    @Override
    public Object toYaml() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        if (null != snapshot) hashMap.put("snapshot", snapshot.toYaml());
        if (null != realtime) hashMap.put("realtime", realtime.toYaml());
        return hashMap;
    }

    public static class Snapshot implements ConfigEntity {
        int threads;//: 1
        int fetchSizeRows;//: 10_000
        boolean traceDBTasks;//: false
        int minJobSizeRows;//: 1_000_000
        int maxJobsPerChunk;//: 32

        @Override
        public Object toYaml() {
            LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
            hashMap.put("threads", threads);
            hashMap.put("fetch-size-rows", fetchSizeRows);
            hashMap.put("_traceDBTasks", traceDBTasks);
            hashMap.put("min-job-size-rows", minJobSizeRows);
            hashMap.put("max-jobs-per-chunk", maxJobsPerChunk);
            return hashMap;
        }

        public Snapshot() {
            threads = 1;
            fetchSizeRows = 10_000;
            traceDBTasks = false;
            minJobSizeRows = 1_000_000;
            maxJobsPerChunk = 32;
        }

        public Snapshot builder() {
            return new Snapshot();
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getFetchSizeRows() {
            return fetchSizeRows;
        }

        public void setFetchSizeRows(int fetchSizeRows) {
            this.fetchSizeRows = fetchSizeRows;
        }

        public boolean isTraceDBTasks() {
            return traceDBTasks;
        }

        public void setTraceDBTasks(boolean traceDBTasks) {
            this.traceDBTasks = traceDBTasks;
        }

        public int getMinJobSizeRows() {
            return minJobSizeRows;
        }

        public void setMinJobSizeRows(int minJobSizeRows) {
            this.minJobSizeRows = minJobSizeRows;
        }

        public int getMaxJobsPerChunk() {
            return maxJobsPerChunk;
        }

        public void setMaxJobsPerChunk(int maxJobsPerChunk) {
            this.maxJobsPerChunk = maxJobsPerChunk;
        }
    }

    public static class Realtime implements ConfigEntity {
        int threads;
        int fetchSizeRows;
        int fetchIntervals;
        boolean traceDBTasks;
        Heartbeat heartbeat;

        @Override
        public Object toYaml() {
            LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
            hashMap.put("threads", threads);
            hashMap.put("fetch-size-rows", fetchSizeRows);
            hashMap.put("fetch-interval-s", fetchIntervals);
            hashMap.put("_traceDBTasks", traceDBTasks);
            Optional.ofNullable(heartbeat).ifPresent(h -> hashMap.put("heartbeat", heartbeat.toYaml()));
            return hashMap;
        }

        public Realtime builder() {
            return new Realtime();
        }

        public Realtime() {
            threads = 1;
            fetchIntervals = 10;
            fetchSizeRows = 100000;
            traceDBTasks = false;
            heartbeat = null;
        }


        public Heartbeat getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(Heartbeat heartbeat) {
            this.heartbeat = heartbeat;
        }

        public int getFetchIntervals() {
            return fetchIntervals;
        }

        public void setFetchIntervals(int fetchIntervals) {
            this.fetchIntervals = fetchIntervals;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getFetchSizeRows() {
            return fetchSizeRows;
        }

        public void setFetchSizeRows(int fetchSizeRows) {
            this.fetchSizeRows = fetchSizeRows;
        }

        public boolean isTraceDBTasks() {
            return traceDBTasks;
        }

        public void setTraceDBTasks(boolean traceDBTasks) {
            this.traceDBTasks = traceDBTasks;
        }

        public static class Heartbeat implements ConfigEntity {
            boolean enable;
            String catalog;
            String schema;
            long interval_ms;

            public Heartbeat() {
                this.enable = false;
                this.interval_ms = 10000L;
            }

            public boolean isEnable() {
                return enable;
            }

            public void setEnable(boolean enable) {
                this.enable = enable;
            }

            public String getCatalog() {
                return catalog;
            }

            public void setCatalog(String catalog) {
                this.catalog = catalog;
            }

            public String getSchema() {
                return schema;
            }

            public void setSchema(String schema) {
                this.schema = schema;
            }

            public long getInterval_ms() {
                return interval_ms;
            }

            public void setInterval_ms(long interval_ms) {
                this.interval_ms = interval_ms;
            }

            @Override
            public Object toYaml() {
                LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
                hashMap.put("enable", enable);
                hashMap.put("catalog", catalog);
                hashMap.put("schema", schema);
                hashMap.put("interval-ms", interval_ms);
                return hashMap;
            }
        }
    }
}

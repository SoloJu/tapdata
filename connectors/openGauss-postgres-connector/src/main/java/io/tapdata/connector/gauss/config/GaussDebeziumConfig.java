package io.tapdata.connector.gauss.config;

import io.debezium.config.Configuration;
import io.tapdata.kit.EmptyKit;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class GaussDebeziumConfig {

    private GaussConfig gaussConfig;
    private List<String> observedTableList;
    private String slotName; //unique for each slot, so create it by postgres config and observed tables
    private String namespace;

    public GaussDebeziumConfig() {

    }
    public GaussDebeziumConfig use(GaussConfig gaussConfig) {
        this.gaussConfig = gaussConfig;
        return this;
    }

    public GaussDebeziumConfig watch(List<String> observedTableList) {
        this.observedTableList = observedTableList;
        //unique and can find it
        this.namespace = slotName + "-openGauss-postgres-connector";
        return this;
    }

    public List<String> getObservedTableList() {
        return observedTableList;
    }

    public String getSlotName() {
        return slotName;
    }

    public GaussDebeziumConfig useSlot(String slotName) {
        this.slotName = slotName;
        return this;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * create debezium config
     *
     * @return Configuration
     */
    public Configuration create() {
        Configuration.Builder builder = Configuration.create();
        builder.with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
//                .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
                .with("offset.storage", "io.tapdata.connector.postgres.cdc.offset.PostgresOffsetBackingStore") //customize offset store, store in engine
                .with("snapshot.mode", "never")
                .with("slot.name", slotName)
//                .with("offset.storage.file.filename", "d:/cdc/offset/" + slotName + ".dat") //path must be changed with requirement
                .with("offset.flush.interval.ms", 60000)
                .with("name", slotName + "-postgres-connector")
                .with("database.server.name", gaussConfig.getDatabase())
                .with("database.hostname", gaussConfig.getHost())
                .with("database.port", gaussConfig.getPort())
                .with("database.user", gaussConfig.getUser())
                .with("database.password", gaussConfig.getPassword())
                .with("database.dbname", gaussConfig.getDatabase())
                .with("time.precision.mode", "connect")
                .with("transforms.tsFormat1.type", "org.apache.kafka.connect.transforms.TimestampConverter$Value")
                .with("transforms.tsFormat1.target.type", "string")
                .with("transforms.tsFormat1.field", "transaction_time")
                .with("transforms.tsFormat1.format", "yyyy-MM-dd HH:mm:ss")
                .with("interval.handling.mode", "string")
                .with("converters", "timestamp,timestampTZ,time,timeTZ,geometry,other")
                .with("timestamp.type", "io.tapdata.connector.postgres.converters.TimestampConverter")
                .with("timestamp.schema.name", "io.debezium.postgresql.type.Timestamp")
                .with("timestampTZ.type", "io.tapdata.connector.postgres.converters.TimestampTZConverter")
                .with("timestampTZ.schema.name", "io.debezium.postgresql.type.TimestampTZ")
                .with("time.type", "io.tapdata.connector.postgres.converters.TimeConverter")
                .with("time.schema.name", "io.debezium.postgresql.type.Time")
                .with("timeTZ.type", "io.tapdata.connector.postgres.converters.TimeTZConverter")
                .with("timeTZ.schema.name", "io.debezium.postgresql.type.TimeTZ")
                .with("geometry.type", "io.tapdata.connector.postgres.converters.GeometryConverter")
                .with("geometry.schema.name", "io.debezium.postgresql.type.Geometry")
                .with("other.type", "io.tapdata.connector.postgres.converters.OtherConverter")
                .with("other.schema.name", "io.debezium.postgresql.type.Other")
                .with("plugin.name", gaussConfig.getLogPluginName());
        if (EmptyKit.isNotEmpty(observedTableList)) {
            //construct tableWhiteList with schema.table(,) as <public.Student,postgres.test>
            String tableWhiteList = observedTableList.stream().map(v -> gaussConfig.getSchema() + "." + v).collect(Collectors.joining(", "));
            builder.with("table.whitelist", tableWhiteList);
        }
        return builder.build();
    }

    enum LogDecorderPlugins {
        DECORDERBUFS("decoderbufs"),
        WAL2JSON("wal2json"),
        WAL2JSONRDS("wal2json_rds"),
        WAL2JSONSTREMING("wal2json_streaming"),
        WAL2JSONRDSSTREAMING("wal2json_rds_streaming"),
        PGOUTPUT("pgoutput"),
        ;

        private String pluginName;

        LogDecorderPlugins(String pluginName) {
            this.pluginName = pluginName;
        }

        public String getPluginName() {
            return pluginName;
        }

        private static HashMap<String, GaussDebeziumConfig.LogDecorderPlugins> map = new HashMap<>();

        static {
            for (GaussDebeziumConfig.LogDecorderPlugins value : GaussDebeziumConfig.LogDecorderPlugins.values()) {
                map.put(value.getPluginName(), value);
            }
        }
    }

}

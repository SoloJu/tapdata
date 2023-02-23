package io.tapdata.connector.gauss.cdc;


import io.debezium.embedded.EmbeddedEngine;
import io.debezium.engine.DebeziumEngine;
import io.tapdata.connector.gauss.GaussJdbcContext;
import io.tapdata.connector.gauss.config.GaussConfig;
import io.tapdata.connector.gauss.config.GaussDebeziumConfig;
import io.tapdata.connector.gauss.offset.GaussOffset;
import io.tapdata.connector.gauss.offset.GaussOffsetStorage;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.simplify.TapSimplify;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.kit.EmptyKit;
import io.tapdata.kit.NumberKit;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.codehaus.plexus.util.StringUtils;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class GaussCdcRunner extends DebeziumCdcRunner {

    private static final String TAG = GaussCdcRunner.class.getSimpleName();
    private final GaussConfig gaussConfig;
    private GaussDebeziumConfig gaussDebeziumConfig;
    private GaussOffset gaussOffset;
    private int recordSize;
    private StreamReadConsumer consumer;
    private final AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();

    public GaussCdcRunner(GaussJdbcContext guassJdbcContext) {
        this.gaussConfig = (GaussConfig) guassJdbcContext.getConfig();
    }

    public GaussCdcRunner useSlot(String slotName) {
        this.runnerName = slotName;
        return this;
    }

    public GaussCdcRunner watch(List<String> observedTableList) {
        gaussDebeziumConfig = new GaussDebeziumConfig()
                .use(gaussConfig)
                .useSlot(runnerName)
                .watch(observedTableList);
        return this;
    }

    public GaussCdcRunner offset(Object offsetState) {
        if (EmptyKit.isNull(offsetState)) {
            gaussOffset = new GaussOffset();
        } else {
            this.gaussOffset = (GaussOffset) offsetState;
        }
        GaussOffsetStorage.guassOffsetMap.put(runnerName, gaussOffset);
        return this;
    }

    public AtomicReference<Throwable> getThrowable() {
        return throwableAtomicReference;
    }

    public void registerConsumer(StreamReadConsumer consumer, int recordSize) {
        this.recordSize = recordSize;
        this.consumer = consumer;
        //build debezium engine
        this.engine = (EmbeddedEngine) EmbeddedEngine.create()
                .using(gaussDebeziumConfig.create())
                .using(new DebeziumEngine.ConnectorCallback() {
                    @Override
                    public void taskStarted() {
                        DebeziumEngine.ConnectorCallback.super.taskStarted();
                        consumer.streamReadStarted();
                    }

                    @Override
                    public void taskStopped() {
                        DebeziumEngine.ConnectorCallback.super.taskStopped();
                        consumer.streamReadEnded();
                    }
                })
//                .using(this.getClass().getClassLoader())
//                .using(Clock.SYSTEM)
//                .notifying(this::consumeRecord)
//                .using((numberOfMessagesSinceLastCommit, timeSinceLastCommit) ->
//                        numberOfMessagesSinceLastCommit >= 1000 || timeSinceLastCommit.getSeconds() >= 60)
                .notifying(this::consumeRecords).using((result, message, throwable) -> {
                    if (result) {
                        if (StringUtils.isNotBlank(message)) {
                            TapLogger.info(TAG, "CDC engine stopped: " + message);
                        } else {
                            TapLogger.info(TAG, "CDC engine stopped");
                        }
                    } else {
                        if (null != throwable) {
                            if (StringUtils.isNotBlank(message)) {
                                throwableAtomicReference.set(new RuntimeException(message, throwable));
                            } else {
                                throwableAtomicReference.set(new RuntimeException(throwable));
                            }
                        } else {
                            throwableAtomicReference.set(new RuntimeException(message));
                        }
                    }
                    consumer.streamReadEnded();
                })
                .build();
    }

    @Override
    public void consumeRecords(List<SourceRecord> sourceRecords, DebeziumEngine.RecordCommitter<SourceRecord> committer) {
        super.consumeRecords(sourceRecords, committer);
        List<TapEvent> eventList = TapSimplify.list();
        Map<String, ?> offset = null;
        for (SourceRecord sr : sourceRecords) {
            offset = sr.sourceOffset();
            // PG use micros to indicate the time but in pdk api we use millis
            Long referenceTime = (Long) offset.get("ts_usec") / 1000;
            Struct struct = ((Struct) sr.value());
            if (struct == null) {
                continue;
            }
            String op = struct.getString("op");
            String table = struct.getStruct("source").getString("table");
            Struct after = struct.getStruct("after");
            Struct before = struct.getStruct("before");
            switch (op) { //snapshot.mode = 'never'
                case "c": //after running --insert
                case "r": //after slot but before running --read
                    eventList.add(new TapInsertRecordEvent().init().table(table).after(getMapFromStruct(after)).referenceTime(referenceTime));
                    break;
                case "d": //after running --delete
                    eventList.add(new TapDeleteRecordEvent().init().table(table).before(getMapFromStruct(before)).referenceTime(referenceTime));
                    break;
                case "u": //after running --update
                    eventList.add(new TapUpdateRecordEvent().init().table(table).after(getMapFromStruct(after)).before(getMapFromStruct(before)).referenceTime(referenceTime));
                    break;
                default:
                    break;
            }
            if (eventList.size() >= recordSize) {
                gaussOffset.setSourceOffset(TapSimplify.toJson(offset));
                consumer.accept(eventList, gaussOffset);
                GaussOffsetStorage.guassOffsetMap.put(runnerName, gaussOffset);
                eventList = TapSimplify.list();
            }
        }
        if (EmptyKit.isNotEmpty(eventList)) {
            gaussOffset.setSourceOffset(TapSimplify.toJson(offset));
            consumer.accept(eventList, gaussOffset);
            GaussOffsetStorage.guassOffsetMap.put(runnerName, gaussOffset);
        }
    }

    private DataMap getMapFromStruct(Struct struct) {
        DataMap dataMap = new DataMap();
        if (EmptyKit.isNull(struct)) {
            return dataMap;
        }
        struct.schema().fields().forEach(field -> {
            Object obj = struct.getWithoutDefault(field.name());
            if (obj instanceof ByteBuffer) {
                obj = struct.getBytes(field.name());
            } else if (obj instanceof Struct) {
                obj = BigDecimal.valueOf(NumberKit.bytes2long(((Struct) obj).getBytes("value")), (int) ((Struct) obj).get("scale"));
            }
            dataMap.put(field.name(), obj);
        });
        return dataMap;
    }
}

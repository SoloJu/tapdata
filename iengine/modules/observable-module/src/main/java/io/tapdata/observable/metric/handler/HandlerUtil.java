package io.tapdata.observable.metric.handler;

import com.google.common.collect.Lists;
import com.tapdata.entity.TapdataEvent;
import com.tapdata.entity.TapdataHeartbeatEvent;
import io.tapdata.entity.event.TapBaseEvent;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.event.control.HeartbeatEvent;
import io.tapdata.entity.event.ddl.index.TapCreateIndexEvent;
import io.tapdata.entity.event.ddl.index.TapDeleteIndexEvent;
import io.tapdata.entity.event.ddl.table.*;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.pdk.core.utils.CommonUtils;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author Dexter
 */
public class HandlerUtil {
    private HandlerUtil() {

    }
    protected static final RandomSampleEventHandler randomSampleEventHandler = new RandomSampleEventHandler(0.5);
    protected static final RandomSampleEventHandler.HandleEvent covertTapDataEvent = data -> {
        if (data instanceof TapdataEvent) {
            TapdataEvent tapdataEvent = (TapdataEvent) data;
            return tapdataEvent.getTapEvent();
        } else if (data instanceof TapEvent) {
            return (TapEvent) data;
        }
        return null;
    };
    public static EventTypeRecorder countTapDataEvent(List<TapdataEvent> events, Long sizeOfMemory) {
        long now = System.currentTimeMillis();

        List<Long> referenceTimeList = Lists.newArrayList();
        EventTypeRecorder recorder = new EventTypeRecorder();
        for (TapdataEvent tapdataEvent : events) {
            // skip events like heartbeat
            if (null == tapdataEvent.getTapEvent()) {
                if (tapdataEvent instanceof TapdataHeartbeatEvent) {
                    setEventTimestamp(recorder, tapdataEvent.getSourceTime());
                    referenceTimeList.add(tapdataEvent.getSourceTime());
                }
                continue;
            }
            referenceTimeList.add(countEventTypeAndGetReferenceTime(tapdataEvent.getTapEvent(), recorder));
        }
        sampleMemoryTapEvent(recorder, events, sizeOfMemory);
        recorder.calculateMaxReplicateLag(now, referenceTimeList);

        return recorder;
    }

    public static EventTypeRecorder countTapEvent(List<? extends TapEvent> events, Long sizeOfMemory) {
        long now = System.currentTimeMillis();

        List<Long> referenceTimeList = Lists.newArrayList();
        EventTypeRecorder recorder = new EventTypeRecorder();
        for (TapEvent tapEvent : events) {
            referenceTimeList.add(countEventTypeAndGetReferenceTime(tapEvent, recorder));
            recorder.incrProcessTimeTotal(now, tapEvent.getTime());
        }
        sampleMemoryTapEvent(recorder, events, sizeOfMemory);
        CommonUtils.ignoreAnyError(() -> recorder.calculateMaxReplicateLag(now, referenceTimeList), "HandlerUtil-countTapEvent");
        return recorder;
    }

    protected static Long countEventTypeAndGetReferenceTime(TapEvent event, EventTypeRecorder recorder) {
        Long ts;
        if (event instanceof HeartbeatEvent) {
            ts = ((HeartbeatEvent) event).getReferenceTime();
        } else {
            ts = ((TapBaseEvent) event).getReferenceTime();

            switch (event.getType()) {
                case TapInsertRecordEvent.TYPE:
                    recorder.incrInsertTotal();
                    break;
                case TapDeleteRecordEvent.TYPE:
                    recorder.incrDeleteTotal();
                    break;
                case TapUpdateRecordEvent.TYPE:
                    recorder.incrUpdateTotal();
                    break;
                case TapDeleteIndexEvent.TYPE:
                case TapCreateIndexEvent.TYPE:
                case TapAlterDatabaseTimezoneEvent.TYPE:
                case TapAlterFieldAttributesEvent.TYPE:
                case TapAlterFieldNameEvent.TYPE:
                case TapAlterFieldPrimaryKeyEvent.TYPE:
                case TapAlterTableCharsetEvent.TYPE:
                case TapClearTableEvent.TYPE:
                case TapCreateTableEvent.TYPE:
                case TapDropFieldEvent.TYPE:
                case TapDropTableEvent.TYPE:
                case TapNewFieldEvent.TYPE:
                case TapRenameTableEvent.TYPE:
                    recorder.incrDdlTotal();
                    break;
                default:
                    recorder.incrOthersTotal();
            }
        }
        setEventTimestamp(recorder, ts);
        return ts;
    }

    protected static void setEventTimestamp(EventTypeRecorder recorder, Long ts) {
        if (null != ts) {
            if (null == recorder.getNewestEventTimestamp() || ts > recorder.getNewestEventTimestamp()) {
                recorder.setNewestEventTimestamp(ts);
            }
            if (null == recorder.getOldestEventTimestamp() || ts < recorder.getOldestEventTimestamp()) {
                recorder.setOldestEventTimestamp(ts);
            }
        }
    }

    @Data
    public static class EventTypeRecorder {
        private long ddlTotal;
        private long insertTotal;
        private long updateTotal;
        private long deleteTotal;
        private long othersTotal;
        private Long processTimeTotal;
        private Long replicateLagTotal;
        private Long oldestEventTimestamp;
        private Long newestEventTimestamp;
        private long memorySize;
        private String memoryUtil = "B";

        public void incrDdlTotal() {
            this.ddlTotal += 1;
        }

        public void incrInsertTotal() {
            this.insertTotal += 1;
        }

        public void incrUpdateTotal() {
            this.updateTotal+= 1;
        }

        public void incrDeleteTotal() {
            this.deleteTotal += 1;
        }
        public void incrOthersTotal() {
            this.othersTotal += 1;
        }

        public void incrProcessTimeTotal(Long now, Long time) {
            if (null == time) return;
            if (null == processTimeTotal) {
                processTimeTotal = 0L;
            }
            processTimeTotal += (now - time);
        }

        public void calculateMaxReplicateLag(Long now, List<Long> referenceTimeList) {
            if (CollectionUtils.isEmpty(referenceTimeList)) return;
            if (null == replicateLagTotal) {
                replicateLagTotal = 0L;
            }

            // remove referenceTimeList null value
            referenceTimeList.removeIf(Objects::isNull);
            // get referenceTimeList min value
            referenceTimeList.stream().min(Long::compareTo).ifPresent(minReferenceTime -> replicateLagTotal = now - minReferenceTime);
        }

        public long getTotal() {
            return ddlTotal + insertTotal + updateTotal + deleteTotal + othersTotal;
        }
    }

    public static void sampleMemoryTapEvent(EventTypeRecorder recorder, List<?> events, Long sizeOfMemory) {
        if (null == recorder) return;
        if (null == sizeOfMemory) {
            randomSampleEventHandler.sampleMemoryTapEvent(recorder, events, covertTapDataEvent);
        } else {
            recorder.setMemorySize(sizeOfMemory);
        }
    }

    public static void sampleMemoryToTapEvent(List<TapEvent> events) {
        if (null == events || events.isEmpty()) return;
        long memory = randomSampleEventHandler.sampleMemoryTapEvent(events, covertTapDataEvent);
        events.stream().filter(Objects::nonNull).forEach(e -> e.setMemorySize(memory));
    }

    public static EventTypeRecorder countTapEvent(List<? extends TapEvent> events) {
        return countTapEvent(events, null);
    }

    public static EventTypeRecorder countTapdataEvent(List<TapdataEvent> events) {
        return countTapDataEvent(events, null);
    }
}

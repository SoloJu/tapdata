package io.tapdata.observable.metric.handler;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.tapdata.entity.TapdataEvent;
import com.tapdata.entity.TapdataHeartbeatEvent;
import io.tapdata.entity.event.TapBaseEvent;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.event.control.HeartbeatEvent;
import io.tapdata.entity.event.dml.*;
import io.tapdata.entity.event.ddl.table.*;
import io.tapdata.entity.event.ddl.index.*;
import io.tapdata.pdk.core.utils.CommonUtils;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author Dexter
 */
public class HandlerUtil {
    public static EventTypeRecorder countTapdataEvent(List<TapdataEvent> events) {
        long now = System.currentTimeMillis();

        List<Long> referenceTimeList = Lists.newArrayList();
        EventTypeRecorder recorder = new EventTypeRecorder();
        for (TapdataEvent tapdataEvent : events) {
            // skip events like heartbeat
            if (null == tapdataEvent.getTapEvent()) {
                if (tapdataEvent instanceof TapdataHeartbeatEvent) {
                    setEventTimestamp(recorder, tapdataEvent.getSourceTime());
                }
                continue;
            }
            referenceTimeList.add(countEventTypeAndGetReferenceTime(tapdataEvent.getTapEvent(), recorder));
            CommonUtils.ignoreAnyError(() -> recorder.calculateMaxReplicateLag(now, referenceTimeList), "HandlerUtil-countTapdataEvent");
        }

        return recorder;
    }

    public static EventTypeRecorder countTapEvent(List<? extends TapEvent> events) {
        long now = System.currentTimeMillis();

        List<Long> referenceTimeList = Lists.newArrayList();
        EventTypeRecorder recorder = new EventTypeRecorder();
        for (TapEvent tapEvent : events) {
            referenceTimeList.add(countEventTypeAndGetReferenceTime(tapEvent, recorder));
            recorder.incrProcessTimeTotal(now, tapEvent.getTime());
        }

        CommonUtils.ignoreAnyError(() -> recorder.calculateMaxReplicateLag(now, referenceTimeList), "HandlerUtil-countTapEvent");
        return recorder;
    }

    private static Long countEventTypeAndGetReferenceTime(TapEvent event, EventTypeRecorder recorder) {
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

    private static void setEventTimestamp(EventTypeRecorder recorder, Long ts) {
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

        public void incrReplicateLagTotal(Long now, Long replicateLag) {
            if (null == replicateLag) return;
            if (null == replicateLagTotal) {
                replicateLagTotal = 0L;
            }
            replicateLagTotal += (now - replicateLag);
        }

        public void calculateMaxReplicateLag(Long now, List<Long> referenceTimeList) {
            if (CollectionUtils.isEmpty(referenceTimeList)) return;
            if (null == replicateLagTotal) {
                replicateLagTotal = 0L;
            }

            // remove referenceTimeList null value
            referenceTimeList.removeIf(Objects::isNull);
            // get referenceTimeList max value
            referenceTimeList.stream().max(Long::compareTo).ifPresent(maxReferenceTime -> replicateLagTotal = now - maxReferenceTime);
        }



        public long getTotal() {
            return ddlTotal + insertTotal + updateTotal + deleteTotal + othersTotal;
        }
    }
}

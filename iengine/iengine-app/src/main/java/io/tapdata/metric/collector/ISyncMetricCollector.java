package io.tapdata.metric.collector;

import com.tapdata.entity.task.context.DataProcessorContext;
import io.tapdata.entity.event.TapBaseEvent;
import io.tapdata.observable.logging.ObsLogger;

import java.time.Duration;
import java.util.List;

/**
 * @author <a href="mailto:harsen_lin@163.com">Harsen</a>
 * @version v1.0 2023/12/13 14:15 Create
 */
public interface ISyncMetricCollector {
    void snapshotBegin();

    void snapshotCompleted();

    void cdcBegin();

    void log(TapBaseEvent tapEvent);

    void log(List<? extends TapBaseEvent> tapEvents);

    void close(ObsLogger obsLogger);

    static ISyncMetricCollector init(DataProcessorContext dataProcessorContext) {
        if (null != dataProcessorContext) {
            return new SyncMetricCollector(100, 0);
        }
        return new NoneSyncMetricCollector();
    }

    public static void main(String[] args) {
        double v = 3947.10874285;// PT1M39.193S
        System.out.println(Duration.ofMillis(3843662));

    }
}

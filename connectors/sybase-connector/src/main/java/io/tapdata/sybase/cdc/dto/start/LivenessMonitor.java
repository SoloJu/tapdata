package io.tapdata.sybase.cdc.dto.start;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class LivenessMonitor implements ConfigEntity {
    boolean enable;
    int inactive_timeout_ms;
    int min_free_memory_threshold_percent;
    int liveness_check_interval_ms;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public int getInactive_timeout_ms() {
        return inactive_timeout_ms;
    }

    public void setInactive_timeout_ms(int inactive_timeout_ms) {
        this.inactive_timeout_ms = inactive_timeout_ms;
    }

    public int getMin_free_memory_threshold_percent() {
        return min_free_memory_threshold_percent;
    }

    public void setMin_free_memory_threshold_percent(int min_free_memory_threshold_percent) {
        this.min_free_memory_threshold_percent = min_free_memory_threshold_percent;
    }

    public int getLiveness_check_interval_ms() {
        return liveness_check_interval_ms;
    }

    public void setLiveness_check_interval_ms(int liveness_check_interval_ms) {
        this.liveness_check_interval_ms = liveness_check_interval_ms;
    }

    @Override
    public Object toYaml() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("enable", enable);
        map.put("inactive-timeout-ms", inactive_timeout_ms);
        map.put("min-free-memory-threshold-percent", min_free_memory_threshold_percent);
        map.put("liveness-check-interval-ms", liveness_check_interval_ms);
        return map;
    }
}
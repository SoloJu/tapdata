package com.tapdata.tm.report.dto;

import lombok.Data;

@Data
public class UniqueInstallBatch {
    private String machineId;
    private long timestamp;
    @Override
    public String toString() {
        return "{" +
                "\"machineId\": \"" + machineId + "\"," +
                "\"timestamp\": \""+ timestamp + "\""+
                "}";
    }
}

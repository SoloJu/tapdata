package io.tapdata.sybase.cdc.dto.analyse;

import io.tapdata.sybase.extend.ConnectionConfig;

import java.util.LinkedHashMap;

/**
 * @author GavinXiao
 * @description AnalyseRecord create by Gavin
 * @create 2023/7/14 9:59
 **/
public interface AnalyseRecord <V,T> {
    public T analyse(V record, LinkedHashMap<String, String> tapTable, String tableId, ConnectionConfig config);
}

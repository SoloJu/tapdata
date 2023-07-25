package io.tapdata.sybase.cdc.dto.analyse.csv;

import au.com.bytecode.opencsv.CSVReader;
import io.tapdata.entity.error.CoreException;
import io.tapdata.sybase.cdc.dto.read.ReadCSV;
import io.tapdata.sybase.cdc.dto.watch.CdcAccepter;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author GavinXiao
 * @description ReadCSVStageImpl create by Gavin
 * @create 2023/7/24 12:08
 **/
public class ReadCSVStageImpl implements ReadCSV {
    @Override
    public void read(String csvPath, CdcAccepter consumer) {
        List<List<String>> lines = new ArrayList<>();
        String[] strArr = null;
        int index = -1;
        try (
                FileInputStream inputStream = new FileInputStream(csvPath);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                CSVReader reader = new CSVReader(inputStreamReader)
        ) {
            while (null != (strArr = reader.readNext())) {
                lines.add(new ArrayList<>(Arrays.asList(strArr)));
                index++;
                if (lines.size() >= CDC_BATCH_SIZE) {
                    consumer.accept(lines, index);
                    lines = new ArrayList<>();
                }
            }
        } catch (Exception e) {
            throw new CoreException("Monitor can not handle csv line, msg: " + e.getMessage());
        } finally {
            if (!lines.isEmpty()) {
                consumer.accept(lines, index);
                lines = null;
            }
        }
    }
}

package io.tapdata.sybase.cdc.dto.analyse.csv;

import au.com.bytecode.opencsv.CSVReader;
import io.tapdata.entity.error.CoreException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author GavinXiao
 * @description ReadCSVOfBigFile create by Gavin
 * @create 2023/7/24 12:10
 * @TODO
 **/
public class ReadCSVOfBigFile implements ReadCSV {
    @Override
    public void read(String csvPath, int offset, CdcAccepter consumer) {
        List<List<String>> lines = new ArrayList<>();
        String[] strArr = null;
        offset = Math.max(offset, 0);
        int index = offset - 1;
        try (
                FileInputStream inputStream = new FileInputStream(csvPath);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                CSVReader reader = new CSVReader(bufferedReader,
                        ',',
                        '"',
                        '\\',
                        offset)
        ) {
            while (null != (strArr = reader.readNext())) {
                lines.add(new ArrayList<>(Arrays.asList(strArr)));
                index++;
                int size = lines.size();
                if (size >= CDC_BATCH_SIZE) {
                    consumer.accept(lines, index - size + 1, index);
                    lines = new ArrayList<>();
                }
            }
        } catch (Exception e) {
            throw new CoreException("Monitor can not handle csv line, msg: " + e.getMessage());
        } finally {
            if (!lines.isEmpty()) {
                consumer.accept(lines, index - lines.size() + 1, index);
            }
        }
    }
}
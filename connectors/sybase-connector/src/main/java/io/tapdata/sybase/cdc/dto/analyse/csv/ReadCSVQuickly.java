package io.tapdata.sybase.cdc.dto.analyse.csv;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.logger.Log;
import io.tapdata.sybase.cdc.dto.analyse.csv.CdcAccepter;
import io.tapdata.sybase.cdc.dto.analyse.csv.ReadCSV;
import io.tapdata.sybase.cdc.dto.analyse.csv.opencsv.CSVReaderQuick;
import io.tapdata.sybase.cdc.dto.analyse.csv.opencsv.SpecialField;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class ReadCSVQuickly implements ReadCSV {
    Log log;

    public void setLog(Log log) {
        this.log = log;
    }
    @Override
    public void read(String csvPath, int offset, CdcAccepter consumer) {

        List<List<String>> lines = new ArrayList<>();
        String[] strArr = null;
        offset = Math.max(offset, 0);
        int index = offset - 1;
        final int lastOffset = offset;

        Collection<String> objects = Collections.synchronizedCollection(new ArrayList<>());
        File touch = FileUtil.touch(csvPath);
        FileInputStream fileInputStream = IoUtil.toStream(touch);
        BufferedReader utf8Reader = IoUtil.getUtf8Reader(fileInputStream);
        Stream<String> csvLines = utf8Reader.lines();
        csvLines.parallel().forEach(objects::add);
        if (!objects.isEmpty()) {
            List<String> item = new ArrayList<>(objects);
            try (
                    FileInputStream inputStream = new FileInputStream(csvPath);
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    CSVReaderQuick reader = new CSVReaderQuick(
                            bufferedReader,
                            item,
                            offset)) {
                int lineIndex = 0;
                while (null != (strArr = reader.readNext()) && lineIndex++ >= lastOffset) {
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

    @Override
    public void read(String csvPath, int offset, List<SpecialField> specialFields, CdcAccepter accepter) {

    }
}

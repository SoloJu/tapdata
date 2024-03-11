package io.tapdata.inspect.compare;

import com.tapdata.constant.ConnectorConstant;
import com.tapdata.entity.inspect.InspectDataSource;
import com.tapdata.entity.inspect.InspectStatus;
import com.tapdata.tm.commons.util.MetaType;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.error.TaskInspectExCode_27;
import io.tapdata.exception.TapCodeException;
import io.tapdata.inspect.InspectJob;
import io.tapdata.inspect.InspectTaskContext;
import io.tapdata.pdk.apis.entity.QueryOperator;
import io.tapdata.pdk.apis.entity.TapAdvanceFilter;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connector.common.QueryHashByAdvanceFilterFunction;
import io.tapdata.pdk.apis.functions.connector.common.vo.TapHashResult;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class HashVerifyInspectJob extends InspectJob {
    private static final String TAG = HashVerifyInspectJob.class.getSimpleName();
    private static Logger logger = LogManager.getLogger(HashVerifyInspectJob.class);

    public HashVerifyInspectJob(InspectTaskContext inspectTaskContext) {
        super(inspectTaskContext);
    }
    @Override
    protected void doRun() {
        try {
            int retry = 0;
            while (retry < 4) {
                try {
                    AtomicReference<TapHashResult> sourceHash = new AtomicReference<>();
                    AtomicReference<TapHashResult> targetHash = new AtomicReference<>();

                    List<QueryOperator> srcConditions = inspectTask.getSource().getConditions();
                    TapTable srcTable = getTapTable(inspectTask.getSource());
                    //if (CollectionUtils.isNotEmpty(srcConditions) && null != inspectTask.getSource().getIsFilter() && inspectTask.getSource().getIsFilter()) {
                        QueryHashByAdvanceFilterFunction queryHashOfSource = this.sourceNode.getConnectorFunctions().getQueryHashByAdvanceFilterFunction();
                        if (null == queryHashOfSource) {
                            retry = 3;
                            throw new TapCodeException(TaskInspectExCode_27.CONNECTOR_NOT_SUPPORT_FUNCTION,
                                    "Source node does not support hash verification with filter function: " + sourceNode.getConnectorContext().getSpecification().getId());
                        }
                        TapAdvanceFilter tapAdvanceFilter = wrapFilter(srcConditions);
                        PDKInvocationMonitor.invoke(this.sourceNode, PDKMethod.QUERY_HASH_BY_ADVANCE_FILTER,
                                () -> queryHashOfSource.query(this.sourceNode.getConnectorContext(), tapAdvanceFilter, srcTable, sourceHash::set), TAG);
                    //}

                    List<QueryOperator> tgtConditions = inspectTask.getTarget().getConditions();
                    TapTable tgtTable = getTapTable(inspectTask.getTarget());
                    //if (CollectionUtils.isNotEmpty(tgtConditions) && null != inspectTask.getTarget().getIsFilter() && inspectTask.getTarget().getIsFilter()) {
                        QueryHashByAdvanceFilterFunction queryHashOfTarget = this.targetNode.getConnectorFunctions().getQueryHashByAdvanceFilterFunction();
                        if (null == queryHashOfTarget) {
                            retry = 3;
                            throw new TapCodeException(TaskInspectExCode_27.CONNECTOR_NOT_SUPPORT_FUNCTION,
                                    "Target node does not support hash verification with filter function: " + targetNode.getConnectorContext().getSpecification().getId());
                        }

                        TapAdvanceFilter filter = wrapFilter(tgtConditions);
                        PDKInvocationMonitor.invoke(this.targetNode, PDKMethod.QUERY_HASH_BY_ADVANCE_FILTER,
                                () -> queryHashOfTarget.query(this.targetNode.getConnectorContext(), filter, tgtTable, targetHash::set), TAG);
                    //}

                    boolean passed = false;
                    if (null != sourceHash.get() && null != targetHash.get() && null != sourceHash.get().getHash()) {
                        passed = sourceHash.get().getHash().equals(targetHash.get().getHash());
                    }

                    stats.setEnd(new Date());
                    stats.setStatus("done");
                    stats.setResult(passed ? "passed" : "failed");
                    stats.setProgress(1);
                    break;
                } catch (Exception e) {
                    if (retry >= 3) {
                        logger.error(String.format("Failed to compare the hash of rows in table %s.%s and table %s.%s, the taskId is %s",
                                source.getName(), inspectTask.getSource().getTable(),
                                target.getName(), inspectTask.getTarget().getTable(), inspectTask.getTaskId()), e);
                        stats.setEnd(new Date());
                        stats.setStatus(InspectStatus.ERROR.getCode());
                        stats.setResult("failed");
                        stats.setErrorMsg(e.getMessage() + "\n" +
                                Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
                        break;
                    }
                    retry++;
                    stats.setErrorMsg(String.format("Check has an exception and is trying again..., The number of retries: %s", retry));
                    stats.setStatus(InspectStatus.ERROR.getCode());
                    stats.setEnd(new Date());
                    stats.setResult("failed");
                    progressUpdateCallback.progress(inspectTask, stats, null);
                    logger.error(String.format("Check has an exception and is trying again..., The number of retries: %s", retry), e);
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException interruptedException) {
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("Inspect failed " + name, e);
        }
    }

    @NotNull
    private static TapAdvanceFilter wrapFilter(List<QueryOperator> srcConditions) {
        TapAdvanceFilter tapAdvanceFilter = TapAdvanceFilter.create();
        tapAdvanceFilter.setOperators(srcConditions);
        DataMap match = new DataMap();
        if (null != srcConditions) {
            srcConditions.stream().filter(op->op.getOperator()== 5).forEach(op->match.put(op.getKey(), op.getValue()));
        }
        tapAdvanceFilter.setMatch(match);
        return tapAdvanceFilter;
    }

    private TapTable getTapTable(InspectDataSource inspectDataSource) {
        Map<String, Object> params = new HashMap<>();
        params.put("connectionId", inspectDataSource.getConnectionId());
        params.put("metaType", MetaType.table.name());
        params.put("tableName", inspectDataSource.getTable());
        TapTable tapTable = inspectTaskContext.getClientMongoOperator().findOne(params, ConnectorConstant.METADATA_INSTANCE_COLLECTION + "/metadata/v2", TapTable.class);
        if (null == tapTable) {
            tapTable = new TapTable(inspectDataSource.getTable());
        }
        return tapTable;
    }
}

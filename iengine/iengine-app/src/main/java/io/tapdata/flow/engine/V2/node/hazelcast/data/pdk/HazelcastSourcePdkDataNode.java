package io.tapdata.flow.engine.V2.node.hazelcast.data.pdk;

import com.tapdata.constant.CollectionUtil;
import com.tapdata.constant.ExecutorUtil;
import com.tapdata.constant.Log4jUtil;
import com.tapdata.constant.MilestoneUtil;
import com.tapdata.entity.SyncStage;
import com.tapdata.entity.TapdataCompleteSnapshotEvent;
import com.tapdata.entity.TapdataEvent;
import com.tapdata.entity.TapdataStartCdcEvent;
import com.tapdata.entity.dataflow.SyncProgress;
import com.tapdata.entity.task.context.DataProcessorContext;
import com.tapdata.tm.commons.dag.Node;
import com.tapdata.tm.commons.task.dto.TaskDto;
import io.tapdata.aspect.*;
import io.tapdata.aspect.utils.AspectUtils;
import io.tapdata.common.sample.sampler.CounterSampler;
import io.tapdata.common.sample.sampler.ResetCounterSampler;
import io.tapdata.common.sample.sampler.SpeedSampler;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.flow.engine.V2.exception.node.NodeException;
import io.tapdata.flow.engine.V2.progress.SnapshotProgressManager;
import io.tapdata.flow.engine.V2.sharecdc.ReaderType;
import io.tapdata.flow.engine.V2.sharecdc.ShareCdcReader;
import io.tapdata.flow.engine.V2.sharecdc.ShareCdcTaskContext;
import io.tapdata.flow.engine.V2.sharecdc.ShareCdcTaskPdkContext;
import io.tapdata.flow.engine.V2.sharecdc.exception.ShareCdcUnsupportedException;
import io.tapdata.flow.engine.V2.sharecdc.impl.ShareCdcFactory;
import io.tapdata.metrics.TaskSampleRetriever;
import io.tapdata.milestone.MilestoneStage;
import io.tapdata.milestone.MilestoneStatus;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import io.tapdata.pdk.apis.functions.connector.source.BatchCountFunction;
import io.tapdata.pdk.apis.functions.connector.source.BatchReadFunction;
import io.tapdata.pdk.apis.functions.connector.source.StreamReadFunction;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import io.tapdata.pdk.core.monitor.PDKMethod;
import io.tapdata.pdk.core.utils.LoggerUtils;
import io.tapdata.schema.TapTableMap;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jackin
 * @date 2022/2/22 2:33 PM
 **/
public class HazelcastSourcePdkDataNode extends HazelcastSourcePdkBaseNode {
	private static final String TAG = HazelcastSourcePdkDataNode.class.getSimpleName();
	private final Logger logger = LogManager.getLogger(HazelcastSourcePdkDataNode.class);

	private static final int ASYNCLY_COUNT_SNAPSHOT_ROW_SIZE_TABLE_THRESHOLD = 100;

	private ShareCdcReader shareCdcReader;
	private ResetCounterSampler resetOutputCounter;
	private CounterSampler outputCounter;
	private SpeedSampler outputQPS;
	private ResetCounterSampler resetInitialWriteCounter;
	private CounterSampler initialWriteCounter;
	private Long initialTime;

	private final SourceStateAspect sourceStateAspect;
	private Map<String, Long> snapshotRowSizeMap;
	private ExecutorService snapshotRowSizeThreadPool;

	public HazelcastSourcePdkDataNode(DataProcessorContext dataProcessorContext) {
		super(dataProcessorContext);
		sourceStateAspect = new SourceStateAspect().dataProcessorContext(dataProcessorContext);
	}

	@Override
	protected void doInit(@NotNull Context context) throws Exception {
		try {
			super.doInit(context);
			// MILESTONE-INIT_CONNECTOR-FINISH
			TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.INIT_CONNECTOR, MilestoneStatus.FINISH);
			MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.INIT_CONNECTOR, MilestoneStatus.FINISH);
		} catch (Throwable e) {
			// MILESTONE-INIT_CONNECTOR-ERROR
			TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.INIT_CONNECTOR, MilestoneStatus.ERROR, logger);
			MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.INIT_CONNECTOR, MilestoneStatus.ERROR, e.getMessage() + "\n" + Log4jUtil.getStackString(e));
			//Notify error for task.
			errorHandle(new RuntimeException(e), e.getMessage());
			throw e;
		}
	}

	@Override
	public void startSourceRunner() {
		try {
			Node<?> node = dataProcessorContext.getNode();
			Thread.currentThread().setName("PDK-SOURCE-RUNNER-" + node.getName() + "(" + node.getId() + ")");
			Log4jUtil.setThreadContext(dataProcessorContext.getTaskDto());
			TapTableMap<String, TapTable> tapTableMap = dataProcessorContext.getTapTableMap();
			try {
				if (need2InitialSync(syncProgress)) {
					if (this.sourceRunnerFirstTime.get()) {
						doSnapshot(new ArrayList<>(tapTableMap.keySet()));
					}
				}
				if (!sourceRunnerFirstTime.get() && CollectionUtils.isNotEmpty(newTables)) {
					doSnapshot(newTables);
				}
			} catch (Throwable e) {
				TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.ERROR, logger);
				MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.ERROR, e.getMessage() + "\n" + Log4jUtil.getStackString(e));
				throw e;
			} finally {
				Optional.ofNullable(snapshotProgressManager).ifPresent(SnapshotProgressManager::close);
			}
			if (need2CDC()) {
				try {
					AspectUtils.executeAspect(sourceStateAspect.state(SourceStateAspect.STATE_CDC_START));
					doCdc();
				} catch (Throwable e) {
					// MILESTONE-READ_CDC_EVENT-ERROR
					TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.ERROR);
					MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.ERROR, e.getMessage() + "\n" + Log4jUtil.getStackString(e));
					logger.error("Read CDC failed, error message: " + e.getMessage(), e);
					throw e;
				} finally {
					AspectUtils.executeAspect(sourceStateAspect.state(SourceStateAspect.STATE_CDC_COMPLETED));
				}
			}
		} catch (Throwable throwable) {
			errorHandle(throwable, throwable.getMessage());
		} finally {
			try {
				while (isRunning()) {
					try {
						if (sourceRunnerLock.tryLock(1L, TimeUnit.SECONDS)) {
							break;
						}
					} catch (InterruptedException e) {
						break;
					}
				}
				if (this.sourceRunnerFirstTime.get()) {
					this.running.set(false);
				}
			} finally {
				try {
					sourceRunnerLock.unlock();
				} catch (Exception ignored) {
				}
			}
		}
	}

	@SneakyThrows
	private void doSnapshot(List<String> tableList) {
		syncProgress.setSyncStage(SyncStage.INITIAL_SYNC.name());
		snapshotProgressManager = new SnapshotProgressManager(dataProcessorContext.getTaskDto(), clientMongoOperator,
				getConnectorNode(), dataProcessorContext.getTapTableMap());
		snapshotProgressManager.startStatsSnapshotEdgeProgress(dataProcessorContext.getNode());

		// count the data size of the tables;
		doCount();

		BatchReadFunction batchReadFunction = getConnectorNode().getConnectorFunctions().getBatchReadFunction();
		if (batchReadFunction != null) {
			// MILESTONE-READ_SNAPSHOT-RUNNING
			AspectUtils.executeAspect(sourceStateAspect.state(SourceStateAspect.STATE_INITIAL_SYNC_START));
			TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.RUNNING);
			MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.RUNNING);
			try {
				while (isRunning()) {
					for (String tableName : tableList) {
						// wait until we count the table
						while (isRunning() && (null == snapshotRowSizeMap || !snapshotRowSizeMap.containsKey(tableName))) {
							TimeUnit.MILLISECONDS.sleep(500);
						}
						try {
							while (isRunning()) {
								try {
									if (sourceRunnerLock.tryLock(1L, TimeUnit.SECONDS)) {
										break;
									}
								} catch (InterruptedException e) {
									break;
								}
							}
							if (!isRunning()) {
								break;
							}
							if (this.removeTables != null && this.removeTables.contains(tableName)) {
								logger.info("Table " + tableName + " is detected that it has been removed, the snapshot read will be skipped");
								this.removeTables.remove(tableName);
								continue;
							}
							TapTable tapTable = dataProcessorContext.getTapTableMap().get(tableName);
							Object tableOffset = ((Map<String, Object>) syncProgress.getBatchOffsetObj()).get(tapTable.getId());
							logger.info("Starting batch read, table name: " + tapTable.getId() + ", offset: " + tableOffset);
							int eventBatchSize = 100;

							executeDataFuncAspect(BatchReadFuncAspect.class, () -> new BatchReadFuncAspect()
									.eventBatchSize(eventBatchSize)
									.connectorContext(getConnectorNode().getConnectorContext())
									.offsetState(tableOffset)
									.dataProcessorContext(this.getDataProcessorContext())
									.start()
									.table(tapTable), batchReadFuncAspect -> PDKInvocationMonitor.invoke(getConnectorNode(), PDKMethod.SOURCE_BATCH_READ,
									() -> batchReadFunction.batchRead(getConnectorNode().getConnectorContext(), tapTable, tableOffset, eventBatchSize, (events, offsetObject) -> {
										if (events != null && !events.isEmpty()) {
											if (logger.isDebugEnabled()) {
												logger.debug("Batch read {} of events, {}", events.size(), LoggerUtils.sourceNodeMessage(getConnectorNode()));
											}
											((Map<String, Object>) syncProgress.getBatchOffsetObj()).put(tapTable.getId(), offsetObject);
											List<TapdataEvent> tapdataEvents = wrapTapdataEvent(events);

											if (batchReadFuncAspect != null)
												AspectUtils.accept(batchReadFuncAspect.state(BatchReadFuncAspect.STATE_READ_COMPLETE).getReadCompleteConsumers(), tapdataEvents);

											if (CollectionUtil.isNotEmpty(tapdataEvents)) {
												tapdataEvents.forEach(this::enqueue);

												if (batchReadFuncAspect != null)
													AspectUtils.accept(batchReadFuncAspect.state(BatchReadFuncAspect.STATE_ENQUEUED).getEnqueuedConsumers(), tapdataEvents);

												resetOutputCounter.inc(tapdataEvents.size());
												outputCounter.inc(tapdataEvents.size());
												outputQPS.add(tapdataEvents.size());
												resetInitialWriteCounter.inc(tapdataEvents.size());
												initialWriteCounter.inc(tapdataEvents.size());
											}
										}
									}), TAG));
						} catch (Throwable throwable) {
							Throwable throwableWrapper = throwable;
							if (!(throwableWrapper instanceof NodeException)) {
								throwableWrapper = new NodeException(throwableWrapper).context(getProcessorBaseContext());
							}
							errorHandle(throwableWrapper, throwableWrapper.getMessage());
							throw throwableWrapper;
						}
						finally {
							try {
								sourceRunnerLock.unlock();
							} catch (Exception ignored) {
							}
						}
					}
					try {
						while (isRunning()) {
							try {
								if (sourceRunnerLock.tryLock(1L, TimeUnit.SECONDS)) {
									break;
								}
							} catch (InterruptedException e) {
								break;
							}
						}
						if (CollectionUtils.isNotEmpty(newTables)) {
							tableList.clear();
							tableList.addAll(newTables);
							newTables.clear();
						} else {
							this.endSnapshotLoop.set(true);
							break;
						}
					} finally {
						try {
							sourceRunnerLock.unlock();
						} catch (Exception ignored) {
						}
					}
				}
			} finally {
				if (isRunning()) {
					initialTime = System.currentTimeMillis();
					// MILESTONE-READ_SNAPSHOT-FINISH
					TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.FINISH);
					MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_SNAPSHOT, MilestoneStatus.FINISH);
					enqueue(new TapdataCompleteSnapshotEvent());
					AspectUtils.executeAspect(sourceStateAspect.state(SourceStateAspect.STATE_INITIAL_SYNC_COMPLETED));
				}
			}
		} else {
			throw new RuntimeException("PDK node does not support batch read: " + dataProcessorContext.getDatabaseType());
		}
	}

	@SneakyThrows
	private void doCount() {
		BatchCountFunction batchCountFunction = getConnectorNode().getConnectorFunctions().getBatchCountFunction();
		if (null == batchCountFunction) {
			logger.warn("PDK node does not support table batch count: " + dataProcessorContext.getDatabaseType());
			return;
		}

		if (dataProcessorContext.getTapTableMap().keySet().size() > ASYNCLY_COUNT_SNAPSHOT_ROW_SIZE_TABLE_THRESHOLD) {
			logger.info("Start to asynchronously count the size of rows for the source table(s)");
			AtomicReference<TaskDto> task = new AtomicReference<>(dataProcessorContext.getTaskDto());
			AtomicReference<Node<?>> node = new AtomicReference<>(dataProcessorContext.getNode());
			snapshotRowSizeThreadPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new SynchronousQueue<>());
			CompletableFuture.runAsync(() -> {
				String name = String.format("Snapshot-Row-Size-Query-Thread-%s(%s)-%s(%s)",
						task.get().getName(), task.get().getId().toHexString(), node.get().getName(), node.get().getId());
				Thread.currentThread().setName(name);
				Log4jUtil.setThreadContext(task.get());

				doCountAsynchronously(batchCountFunction);
			}, snapshotRowSizeThreadPool)
			.whenComplete((v, e) -> {
				if (null != e) {
					logger.warn("Query snapshot row size failed: " + e.getMessage() + "\n" + Log4jUtil.getStackString(e));
				} else {
					logger.info("Query snapshot row size completed: " + node.get().getName() + "(" + node.get().getId() + ")");
				}
				ExecutorUtil.shutdown(this.snapshotRowSizeThreadPool, 10L, TimeUnit.SECONDS);
			});
		} else {
			doCountAsynchronously(batchCountFunction);
		}
	}

	@SneakyThrows
	private void doCountAsynchronously(BatchCountFunction batchCountFunction) {
		if (null == batchCountFunction) {
			logger.warn("PDK node does not support table batch count: " + dataProcessorContext.getDatabaseType());
		}

		for(String tableName : dataProcessorContext.getTapTableMap().keySet()) {
			if (!isRunning()) {
				return;
			}

			TapTable table = dataProcessorContext.getTapTableMap().get(tableName);
			executeDataFuncAspect(TableCountFuncAspect.class, () -> new TableCountFuncAspect()
					.dataProcessorContext(this.getDataProcessorContext())
					.start(), tableCountFuncAspect -> PDKInvocationMonitor.invoke(getConnectorNode(), PDKMethod.SOURCE_BATCH_COUNT,
					() -> {
						try {
							long count = batchCountFunction.count(getConnectorNode().getConnectorContext(), table);

							if (null == snapshotRowSizeMap) {
								snapshotRowSizeMap = new HashMap<>();
							}
							snapshotRowSizeMap.putIfAbsent(tableName, count);

							if (null != tableCountFuncAspect) {
								AspectUtils.accept(tableCountFuncAspect.state(TableCountFuncAspect.STATE_COUNTING).getTableCountConsumerList(), table.getName(), count);
							}
						} catch (Exception e) {
							RuntimeException runtimeException = new RuntimeException("Count " + table.getId() + " failed: " + e.getMessage(), e);
							logger.warn(runtimeException.getMessage() + "\n" + Log4jUtil.getStackString(e));
							throw runtimeException;
						}
					}, TAG));
		}
	}

	@SneakyThrows
	private void doCdc() {
		if (!isRunning()) {
			return;
		}
		this.endSnapshotLoop.set(true);
		if (null == syncProgress.getStreamOffsetObj()) {
			throw new RuntimeException("Starting stream read failed, errors: start point offset is null");
		} else {
			TapdataStartCdcEvent tapdataStartCdcEvent = new TapdataStartCdcEvent();
			tapdataStartCdcEvent.setSyncStage(SyncStage.CDC);
			enqueue(tapdataStartCdcEvent);
		}
		// MILESTONE-READ_CDC_EVENT-RUNNING
		TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.RUNNING);
		MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.RUNNING);
		syncProgress.setSyncStage(SyncStage.CDC.name());
		Node<?> node = dataProcessorContext.getNode();
		if (node.isLogCollectorNode()) {
			// Mining tasks force traditional increments
			doNormalCDC();
		} else {
			try {
				// Try to start with share cdc
				doShareCdc();
			} catch (ShareCdcUnsupportedException e) {
				if (e.isContinueWithNormalCdc()) {
					// If share cdc is unavailable, and continue with normal cdc is true
					logger.info("Share cdc unusable, will use normal cdc mode, reason: " + e.getMessage());
					doNormalCDC();
				} else {
					throw new RuntimeException("Read share cdc log failed: " + e.getMessage(), e);
				}
			} catch (Exception e) {
				throw new RuntimeException("Read share cdc log failed: " + e.getMessage(), e);
			}
		}
	}

	@SneakyThrows
	private void doNormalCDC() {
		if (!isRunning()) {
			return;
		}
		TapTableMap<String, TapTable> tapTableMap = dataProcessorContext.getTapTableMap();
		StreamReadFunction streamReadFunction = getConnectorNode().getConnectorFunctions().getStreamReadFunction();
		if (streamReadFunction != null) {
			logger.info("Starting stream read, table list: " + tapTableMap.keySet() + ", offset: " + syncProgress.getOffsetObj());
			List<String> tables = new ArrayList<>(tapTableMap.keySet());
			cdcDelayCalculation.addHeartbeatTable(tables);
			int batchSize = 1;
			executeDataFuncAspect(StreamReadFuncAspect.class, () -> new StreamReadFuncAspect()
					.connectorContext(getConnectorNode().getConnectorContext())
					.dataProcessorContext(getDataProcessorContext())
					.tables(tables)
					.eventBatchSize(batchSize)
					.offsetState(syncProgress.getStreamOffsetObj())
					.start(), streamReadFuncAspect -> PDKInvocationMonitor.invoke(getConnectorNode(), PDKMethod.SOURCE_STREAM_READ,
					() -> streamReadFunction.streamRead(getConnectorNode().getConnectorContext(), tables,
							syncProgress.getStreamOffsetObj(), batchSize, StreamReadConsumer.create((events, offsetObj) -> {
								try {
									while (isRunning()) {
										try {
											if (sourceRunnerLock.tryLock(1L, TimeUnit.SECONDS)) {
												break;
											}
										} catch (InterruptedException e) {
											break;
										}
									}
									if (events != null && !events.isEmpty()) {
										List<TapdataEvent> tapdataEvents = wrapTapdataEvent(events, SyncStage.CDC, offsetObj);
										if (logger.isDebugEnabled()) {
											logger.debug("Stream read {} of events, {}", events.size(), LoggerUtils.sourceNodeMessage(getConnectorNode()));
										}

										if (streamReadFuncAspect != null)
											AspectUtils.accept(streamReadFuncAspect.state(StreamReadFuncAspect.STATE_STREAMING_READ_COMPLETED).getStreamingReadCompleteConsumers(), tapdataEvents);

										if (CollectionUtils.isNotEmpty(tapdataEvents)) {
											tapdataEvents.forEach(this::enqueue);
											syncProgress.setStreamOffsetObj(offsetObj);
											resetOutputCounter.inc(tapdataEvents.size());
											outputCounter.inc(tapdataEvents.size());
											outputQPS.add(tapdataEvents.size());
											if (streamReadFuncAspect != null)
												AspectUtils.accept(streamReadFuncAspect.state(StreamReadFuncAspect.STATE_STREAMING_ENQUEUED).getStreamingEnqueuedConsumers(), tapdataEvents);
										}
									}
								} catch (Throwable throwable) {
									String error = "Error processing incremental data, error: " + throwable.getMessage();
									RuntimeException runtimeException = new RuntimeException(error, throwable);
									errorHandle(runtimeException, runtimeException.getMessage());
								} finally {
									try {
										sourceRunnerLock.unlock();
									} catch (Exception ignored) {
									}
								}
							}).stateListener((oldState, newState) -> {
								if (null != newState && StreamReadConsumer.STATE_STREAM_READ_STARTED == newState) {
									// MILESTONE-READ_CDC_EVENT-FINISH
									if (streamReadFuncAspect != null)
										executeAspect(streamReadFuncAspect.state(StreamReadFuncAspect.STATE_STREAM_STARTED).streamStartedTime(System.currentTimeMillis()));
									TaskMilestoneFuncAspect.execute(dataProcessorContext, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.FINISH);
									MilestoneUtil.updateMilestone(milestoneService, MilestoneStage.READ_CDC_EVENT, MilestoneStatus.FINISH);
								}
							})), TAG));
		} else {
			throw new RuntimeException("PDK node does not support stream read: " + dataProcessorContext.getDatabaseType());
		}
	}

	private void doShareCdc() throws Exception {
		if (!isRunning()) {
			return;
		}
		cdcDelayCalculation.addHeartbeatTable(new ArrayList<>(dataProcessorContext.getTapTableMap().keySet()));
		ShareCdcTaskContext shareCdcTaskContext = new ShareCdcTaskPdkContext(getCdcStartTs(), processorBaseContext.getConfigurationCenter(),
				dataProcessorContext.getTaskDto(), dataProcessorContext.getNode(), dataProcessorContext.getSourceConn(), getConnectorNode());
		logger.info("Starting incremental sync, read from share log storage...");
		// Init share cdc reader, if unavailable, will throw ShareCdcUnsupportedException
		this.shareCdcReader = ShareCdcFactory.shareCdcReader(ReaderType.PDK_TASK_HAZELCAST, shareCdcTaskContext);
		// Start listen message entity from share storage log
		this.shareCdcReader.listen((event, offsetObj) -> {
			TapdataEvent tapdataEvent = wrapTapdataEvent(event, SyncStage.CDC, offsetObj, true);
			if (null == tapdataEvent) {
				return;
			}
			tapdataEvent.setType(SyncProgress.Type.SHARE_CDC);
			resetOutputCounter.inc(1);
			outputCounter.inc(1);
			outputQPS.add(1);
			enqueue(tapdataEvent);
		});
	}

	private Long getCdcStartTs() {
		Long cdcStartTs;
		try {
			if (null != this.syncProgress && null != this.syncProgress.getEventTime() && this.syncProgress.getEventTime().compareTo(0L) > 0) {
				cdcStartTs = this.syncProgress.getEventTime();
			} else {
				cdcStartTs = initialFirstStartTime;
			}
		} catch (Exception e) {
			throw new RuntimeException("Get cdc start ts failed; Error: " + e.getMessage(), e);
		}
		return cdcStartTs;
	}

	@Override
	public void doClose() throws Exception {
		try {
			if (null != getConnectorNode()) {
				PDKInvocationMonitor.invoke(getConnectorNode(), PDKMethod.STOP, () -> getConnectorNode().connectorStop(), TAG);
			}
		} finally {
			super.doClose();
		}
	}

	/**
	 * TODO(dexter): restore from the db;
	 */
	@Override
	protected void initSampleCollector() {
		super.initSampleCollector();

		// TODO: init outputCounter initial value
		Map<String, Number> values = TaskSampleRetriever.getInstance().retrieve(tags, Arrays.asList(
				"outputTotal", "initialWrite"
		));
		// init statistic and sample related initialize
		resetOutputCounter = statisticCollector.getResetCounterSampler("outputTotal");
		outputCounter = sampleCollector.getCounterSampler("outputTotal");
		outputCounter.inc(values.getOrDefault("outputTotal", 0).longValue());
		outputQPS = sampleCollector.getSpeedSampler("outputQPS");
		resetInitialWriteCounter = statisticCollector.getResetCounterSampler("initialWrite");
		initialWriteCounter = sampleCollector.getCounterSampler("initialWrite");
		initialWriteCounter.inc(values.getOrDefault("initialWrite", 0).longValue());

		statisticCollector.addSampler("initialTime", () -> {
			if (initialTime != null) {
				return initialTime;
			}
			return 0;
		});
		if (syncProgress != null) {
			statisticCollector.addSampler("cdcTime", () -> syncProgress.getEventTime());
		}
	}
}

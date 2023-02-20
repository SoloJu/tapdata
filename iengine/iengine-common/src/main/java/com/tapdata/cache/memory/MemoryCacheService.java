package com.tapdata.cache.memory;

import com.tapdata.cache.*;
import com.tapdata.constant.DataFlowStageUtil;
import com.tapdata.entity.Connections;
import com.tapdata.entity.DatabaseTypeEnum;
import com.tapdata.entity.dataflow.DataFlowCacheConfig;
import com.tapdata.entity.dataflow.Stage;
import com.tapdata.entity.dataflow.StageRuntimeStats;
import com.tapdata.mongo.ClientMongoOperator;
import com.tapdata.processor.ScriptConnection;
import com.tapdata.processor.ScriptUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author jackin
 */
public class MemoryCacheService extends AbstractCacheService {


  /**
   * 打印日志的频率
   */
  public static final long LOG_INFO_BATCH_SIZE = 25000L;


  protected Logger logger = LogManager.getLogger(MemoryCacheService.class);

  /**
   * key: cache name
   * value:
   * - key: cache key
   * - value:
   * -- key: primary keys
   * -- value: record
   */
  private final Map<String, Map<String, Map<String, Map<String, Object>>>> cacheData;

  protected Map<String, DataFlowCacheConfig> cacheConfig;
  /**
   * key: cache name
   * value:
   */
  private final Map<String, ScriptConnection> queryConnections;

  /**
   * key: cache name
   * value:
   * - key: stage id
   * - value:
   * -- key: field name
   * -- value: projection
   */
  protected Map<String, Map<String, Map<String, Integer>>> cacheFieldProjection;


  protected Map<String, Long> lastLogTS;

  private final ICacheRuntimeStats cacheStageRuntimeStats;

  private Map<String, ICacheStats> cacheStatsMap;


//  private Map<String, AtomicBoolean> isInitialing;
//
//  private Map<String, AtomicBoolean> running;

  public MemoryCacheService(ClientMongoOperator clientMongoOperator) {
    super(clientMongoOperator);
    this.cacheData = new ConcurrentHashMap<>();
    cacheStageRuntimeStats = new MemoryCacheRuntimeStats();
    this.cacheConfig = new ConcurrentHashMap<>();
    this.queryConnections = new ConcurrentHashMap<>();
    this.cacheFieldProjection = new ConcurrentHashMap<>();
//    this.isInitialing = new ConcurrentHashMap<>();
//    this.running = new ConcurrentHashMap<>();
    this.lastLogTS = new ConcurrentHashMap<>();
    super.cacheStatusMap = new ConcurrentHashMap<>();
  }

  @Override
  protected Lock getCacheStatusLockInstance(String cacheName) {
    return new ReentrantLock();
  }

  @Override
  protected ICacheGetter getCacheGetterInstance(String cacheName) {
    return new MemoryCacheGetter(getConfig(cacheName), cacheData.get(cacheName), getCacheStats(cacheName), cacheStageRuntimeStats, null);
  }

  @Override
  protected ICacheStats getCacheStats(String cacheName) {
    return getCacheStatsMap().computeIfAbsent(cacheName, f -> new MemoryCacheStats(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()));
  }

  @Override
  protected ICacheStore getCacheStore(String cacheName) {
    return null;
  }

  @Override
  public synchronized void registerCache(DataFlowCacheConfig config) {
    String cacheName = config.getCacheName();
    if (this.cacheConfig.containsKey(cacheName)) {
      throw new RuntimeException(String.format("Cache name %s already exists.", cacheName));
    }

    this.cacheConfig.put(cacheName, config);
    this.cacheData.put(cacheName, new ConcurrentHashMap<>());

//    this.isInitialing.put(cacheName, new AtomicBoolean(true));
//    this.running.put(cacheName, new AtomicBoolean(true));
    this.lastLogTS.put(cacheName, 0L);

    Connections sourceConnection = config.getSourceConnection();
    Stage sourceStage = config.getSourceStage();
    Map<String, Integer> fieldProjection = DataFlowStageUtil.stageToFieldProjection(sourceStage);
    if (MapUtils.isNotEmpty(fieldProjection)) {
      cacheFieldProjection.put(cacheName, new ConcurrentHashMap<String, Map<String, Integer>>() {{
        put(sourceStage.getId(), fieldProjection);
      }});
    }

    try {
      if (StringUtils.equalsAnyIgnoreCase(sourceConnection.getDatabase_type(), DatabaseTypeEnum.MONGODB.getType(), DatabaseTypeEnum.ALIYUN_MONGODB.getType())) {
        ScriptConnection scriptConnection = ScriptUtil.initScriptConnection(sourceConnection);
        if (null == sourceConnection) {
          logger.warn("Memory cache lookup mode unsupported connection {} database type {}", sourceConnection.getName(), sourceConnection.getDatabase_type());
        }
        queryConnections.put(cacheName, scriptConnection);
      }

    } catch (Exception e) {
      logger.error(
              "Init memory cache lookup mode connection {} database type {} failed {}, will turn off lookup mode.",
              sourceConnection.getName(),
              sourceConnection.getDatabase_type(),
              e.getMessage(),
              e
      );
    }
  }

  @Override
  public synchronized void destroy(String cacheName) {
    if (MapUtils.isNotEmpty(queryConnections)) {
      if (queryConnections.containsKey(cacheName)) {
        queryConnections.get(cacheName).close();
      }
    }
//    if (running.containsKey(cacheName)) {
//      running.get(cacheName).compareAndSet(true, false);
//    }

    cacheData.remove(cacheName);
    cacheConfig.remove(cacheName);
    cacheFieldProjection.remove(cacheName);
    lastLogTS.remove(cacheName);
    cacheStageRuntimeStats.removeCacheStageRuntimeStats(null, cacheName);
//    isInitialing.remove(cacheName);
//    running.remove(cacheName);
    queryConnections.remove(cacheName);
  }

  @Override
  public DataFlowCacheConfig getConfig(String cacheName) {
    return cacheConfig.get(cacheName);
  }


  @Override
  public synchronized void setCacheStageRuntimeStats(String dataFlowId, List<Stage> dataFlowStages, List<StageRuntimeStats> dataFlowStageRuntimeStats) {
    this.cacheStageRuntimeStats.setCacheStageRuntimeStats(dataFlowId, dataFlowStages, dataFlowStageRuntimeStats);
  }

  @Override
  public List<StageRuntimeStats> getCacheStageRuntimeStats(String dataFlowId, String cacheName) {
    throw new RuntimeException("Method external unavailable.");
  }

  @Override
  public synchronized void setCacheStageRuntimeStats(String dataFlowId, String cacheName, List<StageRuntimeStats> cacheStageRuntimeStats) {
    this.cacheStageRuntimeStats.setCacheStageRuntimeStats(dataFlowId, cacheName, cacheStageRuntimeStats);
  }

  @Override
  public synchronized void removeCacheStageRuntimeStats(String dataFlowId, String cacheName) {
    this.cacheStageRuntimeStats.removeCacheStageRuntimeStats(dataFlowId, cacheName);
  }

  public void setCacheStatsMap(Map<String, ICacheStats> cacheStatsMap) {
    this.cacheStatsMap = cacheStatsMap;
  }

  protected Map<String, ICacheStats> getCacheStatsMap() {
    return cacheStatsMap;
  }

  public static void main(String[] args) {
    long startTS = System.currentTimeMillis();
    Map<String, Map<String, Object>> results = new HashMap<>();
    for (int i = 0; i < 100000; i++) {
      Map<String, Object> result = new HashMap<>();
      result.put(1 + "", "100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000");
      result.put(2 + "", "100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000");
      result.put(3 + "", "100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000");
      result.put(4 + "", "100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000");
      result.put(5 + "", "100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000100000000001000000000010000000000");
      results.put(i + "", result);

    }

    final long l = RamUsageEstimator.sizeOfMap(results);

    long endTS = System.currentTimeMillis();

    System.out.println(endTS - startTS + ":" + (l / 1024 / 1024));
  }
}

package io.tapdata.flow.engine.V2.node.hazelcast.processor;

import base.hazelcast.BaseHazelcastNodeTest;
import com.tapdata.entity.AppType;
import com.tapdata.entity.TapdataEvent;
import com.tapdata.tm.commons.dag.Node;
import com.tapdata.tm.commons.dag.process.MergeTableNode;
import com.tapdata.tm.commons.externalStorage.ExternalStorageDto;
import com.tapdata.tm.commons.task.dto.MergeTableProperties;
import io.tapdata.construct.constructImpl.ConstructIMap;
import io.tapdata.entity.codec.filter.impl.AllLayerMapIterator;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.schema.value.DateTime;
import io.tapdata.error.TaskMergeProcessorExCode_16;
import io.tapdata.exception.TapCodeException;
import io.tapdata.flow.engine.V2.util.ExternalStorageUtil;
import io.tapdata.pdk.core.api.PDKIntegration;
import lombok.SneakyThrows;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.internal.verification.Times;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author samuel
 * @Description
 * @create 2023-11-22 15:19
 **/
@DisplayName("HazelcastMergeNode Class Test")
public class HazelcastMergeNodeTest extends BaseHazelcastNodeTest {

	HazelcastMergeNode hazelcastMergeNode;

	@BeforeEach
	void beforeEach() {
		super.allSetup();
		MergeTableNode mergeTableNode = new MergeTableNode();
		mergeTableNode.setMergeProperties(new ArrayList<>());
		when(dataProcessorContext.getNode()).thenReturn((Node) mergeTableNode);
		hazelcastMergeNode = new HazelcastMergeNode(dataProcessorContext);
		ReflectionTestUtils.setField(hazelcastMergeNode, "obsLogger", mockObsLogger);
	}

	@Nested
	@DisplayName("DoInit method test")
	class DoInitTest {
		@Test
		@DisplayName("Init external storage when cloud app type")
		void testDoInitExternalStorageCloudAppType() {
			AppType appType = AppType.DFS;
			ExternalStorageDto externalStorageDto = mock(ExternalStorageDto.class);
			try (
					MockedStatic<AppType> appTypeMockedStatic = mockStatic(AppType.class);
					MockedStatic<ExternalStorageUtil> externalStorageUtilMockedStatic = mockStatic(ExternalStorageUtil.class);
					MockedStatic<PDKIntegration> pdkIntegrationMockedStatic = mockStatic(PDKIntegration.class)
			) {
				appTypeMockedStatic.when(AppType::init).thenReturn(appType);
				externalStorageUtilMockedStatic.when(() -> ExternalStorageUtil.getTargetNodeExternalStorage(
						dataProcessorContext.getNode(),
						dataProcessorContext.getEdges(),
						null,
						dataProcessorContext.getNodes()
				)).thenReturn(externalStorageDto);
				pdkIntegrationMockedStatic.when(() -> PDKIntegration.registerMemoryFetcher(anyString(), any(HazelcastMergeNode.class))).thenAnswer((Answer<Void>) invocation -> null);
				hazelcastMergeNode.doInit(jetContext);
				externalStorageUtilMockedStatic.verify(() -> ExternalStorageUtil.getTargetNodeExternalStorage(
						dataProcessorContext.getNode(),
						dataProcessorContext.getEdges(),
						null,
						dataProcessorContext.getNodes()
				), new Times(1));
				Object actualObj = ReflectionTestUtils.getField(hazelcastMergeNode, "externalStorageDto");
				assertNotNull(actualObj);
				assertEquals(externalStorageDto, actualObj);
			}
		}

		@Test
		@DisplayName("Init external storage when daas app type")
		void testDoInitExternalStorageDaasAppType() {
			AppType appType = AppType.DAAS;
			ExternalStorageDto externalStorageDto = mock(ExternalStorageDto.class);
			try (
					MockedStatic<AppType> appTypeMockedStatic = mockStatic(AppType.class);
					MockedStatic<ExternalStorageUtil> externalStorageUtilMockedStatic = mockStatic(ExternalStorageUtil.class);
					MockedStatic<PDKIntegration> pdkIntegrationMockedStatic = mockStatic(PDKIntegration.class)
			) {
				appTypeMockedStatic.when(AppType::init).thenReturn(appType);
				externalStorageUtilMockedStatic.when(() -> ExternalStorageUtil.getTargetNodeExternalStorage(
						dataProcessorContext.getNode(),
						dataProcessorContext.getEdges(),
						null,
						dataProcessorContext.getNodes()
				)).thenReturn(externalStorageDto);
				pdkIntegrationMockedStatic.when(() -> PDKIntegration.registerMemoryFetcher(anyString(), any(HazelcastMergeNode.class))).thenAnswer((Answer<Void>) invocation -> null);
				hazelcastMergeNode.doInit(jetContext);
				externalStorageUtilMockedStatic.verify(() -> ExternalStorageUtil.getTargetNodeExternalStorage(
						dataProcessorContext.getNode(),
						dataProcessorContext.getEdges(),
						null,
						dataProcessorContext.getNodes()
				), new Times(0));
				Object actualObj = ReflectionTestUtils.getField(hazelcastMergeNode, "externalStorageDto");
				assertNull(actualObj);
			}
		}
	}

	@Nested
	@DisplayName("UpsertCache Method Test")
	class UpsertCacheTest {

		private TapdataEvent tapdataEvent;
		private TapInsertRecordEvent tapInsertRecordEvent;
		private ConstructIMap<Document> constructIMap;
		private MergeTableProperties mergeTableProperties;
		private Map<String, List<String>> sourcePkOrUniqueFieldMap;

		@BeforeEach
		void beforeEach() {
			hazelcastMergeNode = spy(hazelcastMergeNode);
			tapdataEvent = mock(TapdataEvent.class);
			tapInsertRecordEvent = mock(TapInsertRecordEvent.class);
			when(tapdataEvent.getTapEvent()).thenReturn(tapInsertRecordEvent);
			constructIMap = mock(ConstructIMap.class);
			mergeTableProperties = mock(MergeTableProperties.class);
			sourcePkOrUniqueFieldMap = new HashMap<>();
			ReflectionTestUtils.setField(hazelcastMergeNode, "sourcePkOrUniqueFieldMap", sourcePkOrUniqueFieldMap);
			ReflectionTestUtils.setField(hazelcastMergeNode, "mapIterator", new AllLayerMapIterator());
		}

		@Test
		@SneakyThrows
		@DisplayName("Upsert one event into cache must call transformDateTime method")
		void testUpsertOneEventCacheMustCallDateTimeValue() {
			Map<String, Object> after = new HashMap<>();
			after.put("id", 1);
			after.put("create_time", new DateTime(Instant.now()));
			when(tapInsertRecordEvent.getAfter()).thenReturn(after);
			List<Map<String, String>> joinKeys = new ArrayList<Map<String, String>>() {{
				add(new HashMap<String, String>() {{
					put("source", "id");
					put("target", "id");
				}});
			}};
			when(mergeTableProperties.getJoinKeys()).thenReturn(joinKeys);
			when(mergeTableProperties.getId()).thenReturn("sourceId");
			sourcePkOrUniqueFieldMap.put("sourceId", new ArrayList<String>() {{
				add("id");
			}});
			when(constructIMap.find(anyString())).thenReturn(null);
			when(constructIMap.upsert(anyString(), any(Document.class))).thenAnswer(invocationOnMock -> null);
			hazelcastMergeNode.upsertCache(tapdataEvent, mergeTableProperties, constructIMap);
			verify(hazelcastMergeNode, new Times(1)).transformDateTime(any(Map.class));
		}

		@Test
		@SneakyThrows
		@DisplayName("Upsert many events into cache must call transformDateTime method")
		void testUpsertManyEventCacheMustCallDateTimeValue() {
			Map<String, Object> after = new HashMap<>();
			after.put("id", 1);
			after.put("create_time", new DateTime(Instant.now()));
			when(tapInsertRecordEvent.getAfter()).thenReturn(after);
			TapInsertRecordEvent tapInsertRecordEvent1 = mock(TapInsertRecordEvent.class);
			when(tapInsertRecordEvent1.getAfter()).thenReturn(new HashMap<String, Object>() {{
				put("id", 2);
				put("create_time", new DateTime(Instant.now()));
			}});
			TapdataEvent tapdataEvent1 = mock(TapdataEvent.class);
			when(tapdataEvent1.getTapEvent()).thenReturn(tapInsertRecordEvent1);
			List<Map<String, String>> joinKeys = new ArrayList<Map<String, String>>() {{
				add(new HashMap<String, String>() {{
					put("source", "id");
					put("target", "id");
				}});
			}};
			when(mergeTableProperties.getJoinKeys()).thenReturn(joinKeys);
			when(mergeTableProperties.getId()).thenReturn("sourceId");
			sourcePkOrUniqueFieldMap.put("sourceId", new ArrayList<String>() {{
				add("id");
			}});
			when(constructIMap.findAll(any(Set.class))).thenReturn(new HashMap<>());
			when(constructIMap.upsert(anyString(), any(Document.class))).thenAnswer(invocationOnMock -> null);
			hazelcastMergeNode.upsertCache(new ArrayList<TapdataEvent>() {{
				add(tapdataEvent);
				add(tapdataEvent1);
			}}, mergeTableProperties, constructIMap);
			verify(hazelcastMergeNode, new Times(2)).transformDateTime(any(Map.class));
		}

		@Test
		@SneakyThrows
		@DisplayName("Upsert many events into cache, when findAll throw InterruptedException")
		void testUpsertManyEventCacheFindAllThrowInterruptedException() {
			Map<String, Object> after = new HashMap<>();
			after.put("id", 1);
			after.put("create_time", new DateTime(Instant.now()));
			when(tapInsertRecordEvent.getAfter()).thenReturn(after);
			TapInsertRecordEvent tapInsertRecordEvent1 = mock(TapInsertRecordEvent.class);
			when(tapInsertRecordEvent1.getAfter()).thenReturn(new HashMap<String, Object>() {{
				put("id", 2);
				put("create_time", new DateTime(Instant.now()));
			}});
			TapdataEvent tapdataEvent1 = mock(TapdataEvent.class);
			when(tapdataEvent1.getTapEvent()).thenReturn(tapInsertRecordEvent1);
			List<Map<String, String>> joinKeys = new ArrayList<Map<String, String>>() {{
				add(new HashMap<String, String>() {{
					put("source", "id");
					put("target", "id");
				}});
			}};
			when(mergeTableProperties.getJoinKeys()).thenReturn(joinKeys);
			when(mergeTableProperties.getId()).thenReturn("sourceId");
			sourcePkOrUniqueFieldMap.put("sourceId", new ArrayList<String>() {{
				add("id");
			}});
			when(constructIMap.findAll(any(Set.class))).thenThrow(new RuntimeException(new InterruptedException()));
			assertDoesNotThrow(() -> hazelcastMergeNode.upsertCache(new ArrayList<TapdataEvent>() {{
				add(tapdataEvent);
				add(tapdataEvent1);
			}}, mergeTableProperties, constructIMap));
			verify(hazelcastMergeNode, new Times(0)).transformDateTime(any(Map.class));
		}

		@Test
		@SneakyThrows
		@DisplayName("Upsert many events into cache, when findAll throw Exception")
		void testUpsertManyEventCacheFindAllThrowException() {
			Map<String, Object> after = new HashMap<>();
			after.put("id", 1);
			after.put("create_time", new DateTime(Instant.now()));
			when(tapInsertRecordEvent.getAfter()).thenReturn(after);
			TapInsertRecordEvent tapInsertRecordEvent1 = mock(TapInsertRecordEvent.class);
			when(tapInsertRecordEvent1.getAfter()).thenReturn(new HashMap<String, Object>() {{
				put("id", 2);
				put("create_time", new DateTime(Instant.now()));
			}});
			TapdataEvent tapdataEvent1 = mock(TapdataEvent.class);
			when(tapdataEvent1.getTapEvent()).thenReturn(tapInsertRecordEvent1);
			List<Map<String, String>> joinKeys = new ArrayList<Map<String, String>>() {{
				add(new HashMap<String, String>() {{
					put("source", "id");
					put("target", "id");
				}});
			}};
			when(mergeTableProperties.getJoinKeys()).thenReturn(joinKeys);
			when(mergeTableProperties.getId()).thenReturn("sourceId");
			sourcePkOrUniqueFieldMap.put("sourceId", new ArrayList<String>() {{
				add("id");
			}});
			RuntimeException mockEx = new RuntimeException();
			when(constructIMap.findAll(any(Set.class))).thenThrow(mockEx);
			TapCodeException tapCodeException = assertThrows(TapCodeException.class, () -> hazelcastMergeNode.upsertCache(new ArrayList<TapdataEvent>() {{
				add(tapdataEvent);
				add(tapdataEvent1);
			}}, mergeTableProperties, constructIMap));
			verify(hazelcastMergeNode, new Times(0)).transformDateTime(any(Map.class));
			assertEquals(TaskMergeProcessorExCode_16.UPSERT_CACHE_FIND_BY_JOIN_KEYS_FAILED, tapCodeException.getCode());
			assertEquals(mockEx, tapCodeException.getCause());
		}
	}

	@Nested
	@DisplayName("TransformDateTime Method Test")
	class TransformDateTimeTest {
		@Test
		@DisplayName("Main process test")
		void transformDateTime() {
			ReflectionTestUtils.setField(hazelcastMergeNode, "mapIterator", new AllLayerMapIterator());
			Instant now = Instant.now();
			Map<String, Object> after = new HashMap<>();
			after.put("id", 1);
			after.put("create_time", new DateTime(now));
			after.put("sub_map", new HashMap<String, Object>() {{
				put("create_time", new DateTime(now));
			}});
			after.put("sub_list", new ArrayList<Object>() {{
				add(new HashMap<String, Object>() {{
					put("create_time", new DateTime(now));
					put("create_time1", new DateTime(now));
				}});
			}});
			hazelcastMergeNode.transformDateTime(after);
			assertEquals(4, after.size());
			assertInstanceOf(Date.class, after.get("create_time"));
		}
	}
}
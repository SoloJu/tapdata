package com.tapdata.tm.ws.handler;

import com.tapdata.manager.common.utils.StringUtils;
import com.tapdata.tm.base.dto.Filter;
import com.tapdata.tm.base.dto.Where;
import com.tapdata.tm.commons.externalStorage.ExternalStorageDto;
import com.tapdata.tm.commons.externalStorage.ExternalStorageType;
import com.tapdata.tm.config.security.UserDetail;
import com.tapdata.tm.ds.dto.DataSourceTypeDto;
import com.tapdata.tm.ds.service.impl.DataSourceDefinitionService;
import com.tapdata.tm.ds.service.impl.DataSourceService;
import com.tapdata.tm.externalStorage.service.ExternalStorageService;
import com.tapdata.tm.messagequeue.dto.MessageQueueDto;
import com.tapdata.tm.messagequeue.service.MessageQueueService;
import com.tapdata.tm.user.service.UserService;
import com.tapdata.tm.utils.SpringContextHelper;
import com.tapdata.tm.worker.service.WorkerService;
import com.tapdata.tm.ws.annotation.WebSocketMessageHandler;
import com.tapdata.tm.ws.dto.MessageInfo;
import com.tapdata.tm.ws.dto.WebSocketContext;
import com.tapdata.tm.ws.endpoint.WebSocketManager;
import com.tapdata.tm.ws.enums.MessageType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tapdata.tm.utils.MongoUtils.toObjectId;

/**
 * @author <a href="mailto:harsen_lin@163.com">Harsen</a>
 * @version v1.0 2023/6/9 15:36 Create
 */
@Slf4j
@WebSocketMessageHandler(type = MessageType.TEST_EXTERNAL_STORAGE)
public class TestExternalStorageHandler implements WebSocketHandler {

	private final MessageQueueService messageQueueService;
	private final DataSourceService dataSourceService;
	private final DataSourceDefinitionService dataSourceDefinitionService;
	private final UserService userService;
	private final WorkerService workerService;

	public TestExternalStorageHandler(MessageQueueService messageQueueService, DataSourceService dataSourceService, UserService userService
		, WorkerService workerService, DataSourceDefinitionService dataSourceDefinitionService) {
		this.messageQueueService = messageQueueService;
		this.dataSourceService = dataSourceService;
		this.userService = userService;
		this.workerService = workerService;
		this.dataSourceDefinitionService = dataSourceDefinitionService;
	}

	@Override
	public void handleMessage(WebSocketContext context) throws Exception {
		String userId = context.getUserId();
		if (StringUtils.isBlank(userId)) {
			sendErrorMessage(context, "UserId", "UserId is blank");
			return;
		}

		UserDetail userDetail = userService.loadUserById(toObjectId(userId));
		if (userDetail == null) {
			sendErrorMessage(context, "UserDetail", "UserDetail is null");
			return;
		}

		MessageInfo messageInfo = context.getMessageInfo();
		Map<String, Object> externalStorageConfig = messageInfo.getData();

		MessageType testConnectionType = MessageType.TEST_CONNECTION;
		WebSocketHandler handler = WebSocketManager.getHandler(testConnectionType.getType());
		if (null == handler) {
			sendErrorMessage(context, "Handler", String.format("Not found handler with type: %s", testConnectionType.getType()));
			return;
		}

		// 将 ExternalStorage 配置转换成 Connections 配置
		String externalStorageId = (String) externalStorageConfig.get("id");
		Map<String, Object> testConnectionConfig = newMongoDBConnections(userDetail);
		if (null == testConnectionConfig) {
			sendErrorMessage(context, "ExternalStorageId", String.format("Can not found storage by id '%s'", externalStorageId));
			return;
		}
		Map<String, Object> connectorConfig = (Map<String, Object>) testConnectionConfig.computeIfAbsent("config", s -> new LinkedHashMap<>());
		if (null == externalStorageId) {
			// 新配置
			connectorConfig.put("uri", externalStorageConfig.get("uri"));
			connectorConfig.put("ssl", externalStorageConfig.get("ssl"));
			connectorConfig.put("sslCA", externalStorageConfig.get("sslCA"));
			connectorConfig.put("sslKey", externalStorageConfig.get("sslKey"));
			connectorConfig.put("sslPass", externalStorageConfig.get("sslPass"));
			connectorConfig.put("sslValidate", externalStorageConfig.get("sslValidate"));
			connectorConfig.put("checkServerIdentity", externalStorageConfig.get("checkServerIdentity"));
		} else {
			// 已存在配置
			ExternalStorageService externalStorageService = SpringContextHelper.getBean(ExternalStorageService.class);
			ExternalStorageDto dto = externalStorageService.findNotCheckById(externalStorageId);
			if (!ExternalStorageType.mongodb.name().equals(dto.getType())) {
				WebSocketManager.sendMessage(context.getSender(), String.format("Not support test external storage with type: %s", dto.getType()));
				return;
			}

			connectorConfig.put("uri", dto.getUri());
			connectorConfig.put("ssl", dto.isSsl());
			connectorConfig.put("sslCA", dto.getSslCA());
			connectorConfig.put("sslKey", dto.getSslKey());
			connectorConfig.put("sslPass", dto.getSslPass());
			connectorConfig.put("sslValidate", dto.isSslValidate());
			connectorConfig.put("checkServerIdentity", dto.isCheckServerIdentity());
		}

		MessageInfo testConnectionMessageInfo = new MessageInfo();
		testConnectionMessageInfo.setType(testConnectionType.getType());
		testConnectionMessageInfo.setData(testConnectionConfig);

		// 消息转发
		WebSocketContext testConnectionWebSocketContext = new WebSocketContext(context.getSessionId(), context.getSender(), context.getUserId(), testConnectionMessageInfo);
		handler.handleMessage(testConnectionWebSocketContext);
	}

	private Map<String, Object> newMongoDBConnections(UserDetail userDetail) {
		// 获取最新 MongoDB 连接器配置
		Filter filter = new Filter(Where
			.where("type", "MongoDB")
			.and("tag", "All")
			.and("authentication", "All")
		);
		List<DataSourceTypeDto> dataSourceTypeDtos = dataSourceDefinitionService.dataSourceTypesV2(userDetail, filter);
		if (null == dataSourceTypeDtos || dataSourceTypeDtos.isEmpty()) {
			return null;
		}

		Map<String, Object> config = new HashMap<>();
		DataSourceTypeDto dataSourceTypeDto = dataSourceTypeDtos.get(0);
		config.put("name", "TestExternalStorage");
		config.put("connection_type", "target");
		config.put("accessNodeType", "AUTOMATIC_PLATFORM_ALLOCATION");
		config.put("accessNodeTypeEmpty", false);
		config.put("lastUpdBy", userDetail.getUserId());
		config.put("createUser", userDetail.getUsername());
		config.put("database_type", dataSourceTypeDto.getPdkId()); // mongodb
		config.put("pdkType", dataSourceTypeDto.getPdkType());
		config.put("pdkHash", dataSourceTypeDto.getPdkHash());
		config.put("everLoadSchema", true);
		config.put("tableCount", 0);
		config.put("loadAllTables", true);
		config.put("openTableExcludeFilter", false);
		config.put("heartbeatEnable", false);
		config.put("user_id", userDetail.getUserId());
		config.put("disabledLoadSchema", true);
		config.put("updateSchema", false);
		config.put("editTest", false);
		config.put("schemaUpdateHour", "UTC");
		return config;
	}

	private void sendErrorMessage(WebSocketContext context, String title, String message) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", "testConnectionResult");
		data.put("status", "SUCCESS");
		data.put("msg", message);
		data.put("result", new HashMap<String, Object>(){{
			put("status", "invalid");
			put("response_body", new HashMap<String, Object>(){{
				put("validate_details", Collections.singletonList(new HashMap<String, Object>() {{
					put("sort", 0);
					put("cost", 0);
					put("required", true);
					put("show_msg", title);
					put("fail_message", message);
					put("status", "failed");
				}}));
			}});
		}});
		sendMessage(context, data);
	}

	private void sendMessage(WebSocketContext context, Map<String, Object> data) {
		MessageQueueDto messageQueueDto = new MessageQueueDto();
		messageQueueDto.setSender(context.getSender());
		messageQueueDto.setReceiver(context.getSender());
		messageQueueDto.setType("pipe");
		messageQueueDto.setData(data);
		messageQueueService.sendMessage(messageQueueDto);
	}
}

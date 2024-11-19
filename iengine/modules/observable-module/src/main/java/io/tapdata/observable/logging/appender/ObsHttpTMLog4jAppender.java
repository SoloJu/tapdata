package io.tapdata.observable.logging.appender;

import com.google.common.collect.Queues;
import com.tapdata.constant.ExecutorUtil;
import com.tapdata.mongo.ClientMongoOperator;
import io.tapdata.observable.logging.util.TokenBucketRateLimiter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.tapdata.observable.logging.ObsLogger.LOG_TAG_SOURCE_FOR_USER_SCRIPT;

/**
 * @author samuel
 * @Description
 * @create 2023-03-08 14:40
 **/
@Plugin(
		name = "ObsHttpTMAppender",
		category = Core.CATEGORY_NAME,
		elementType = Appender.ELEMENT_TYPE)
public class ObsHttpTMLog4jAppender extends AbstractAppender {
	public static final int QUEUE_CAPACITY = 100000;
	public static final int MIN_BATCH_SIZE = 100;
	public static final long OFFSET_QUEUE_TIMEOUT = 10L;
	private final ClientMongoOperator clientMongoOperator;
	private final LinkedBlockingQueue<String> messageQueue;
	private final ExecutorService consumeMessageThreadPool;
	private final int batchSize;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final String taskId;

	protected ObsHttpTMLog4jAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties,
									 ClientMongoOperator clientMongoOperator, int batchSize, String taskId) {
		super(name, filter, layout, ignoreExceptions, properties);
		this.clientMongoOperator = clientMongoOperator;
		this.batchSize = Math.max(batchSize, MIN_BATCH_SIZE);
		this.messageQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
		this.taskId = taskId;
		this.consumeMessageThreadPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new SynchronousQueue<>(),
				r -> new Thread(r, "Consume-" + ObsHttpTMLog4jAppender.class.getSimpleName() + "-" + name));
		this.consumeMessageThreadPool.submit(this::consumeAndInsertLogs);
	}

	private void consumeAndInsertLogs() {
		List<String> bufferList = new ArrayList<>();
		while (!Thread.currentThread().isInterrupted() && running.get()) {
			int drainResult;
			try {
				drainResult = Queues.drain(messageQueue, bufferList, this.batchSize, 1L, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				break;
			}
			if (drainResult > 0 && CollectionUtils.isNotEmpty(bufferList)) {
				callTmApiInsertLogs(bufferList);
			}
		}
		if (CollectionUtils.isNotEmpty(bufferList)) {
			callTmApiInsertLogs(bufferList);
		}
		if (!messageQueue.isEmpty()) {
			for (String message : messageQueue) {
				bufferList.add(message);
				if (bufferList.size() == MIN_BATCH_SIZE) {
					callTmApiInsertLogs(bufferList);
				}
			}
			if (CollectionUtils.isNotEmpty(bufferList)) {
				callTmApiInsertLogs(bufferList);
			}
		}
	}

	protected void callTmApiInsertLogs(List<String> bufferList) {
		List<String> tmp = bufferList.stream()
				.filter(r -> !r.contains(LOG_TAG_SOURCE_FOR_USER_SCRIPT) || TokenBucketRateLimiter.get().tryAcquire(taskId))
				.collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(tmp)) {
            this.clientMongoOperator.insertMany(bufferList, "MonitoringLogs/batchJson");
        }
        bufferList.clear();
    }

	@PluginFactory
	public static ObsHttpTMLog4jAppender createAppender(
			@PluginAttribute("name") String name,
			@PluginElement("Filter") Filter filter,
			Layout<? extends Serializable> layout,
			boolean ignoreExceptions, Property[] properties,
			ClientMongoOperator clientMongoOperator, int batchSize,
			String taskId) {

		return new ObsHttpTMLog4jAppender(name, filter, layout, ignoreExceptions, properties, clientMongoOperator, batchSize, taskId);
	}

	@Override
	public void append(LogEvent event) {
		String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
		if (StringUtils.isBlank(message)) {
			return;
		}
		try {
			messageQueue.offer(message, OFFSET_QUEUE_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
		}
	}

	@Override
	public boolean stop(long timeout, TimeUnit timeUnit) {
		if (this.running.compareAndSet(true, false)) {
			if (null != this.consumeMessageThreadPool) {
				ExecutorUtil.shutdown(this.consumeMessageThreadPool, 1L, TimeUnit.MINUTES);
			}
		}
		return super.stop(timeout, timeUnit);
	}
}

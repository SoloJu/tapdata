package io.tapdata.inspect.util;

import com.tapdata.constant.JSONUtil;
import com.tapdata.entity.inspect.Inspect;
import com.tapdata.entity.inspect.InspectDataSource;
import com.tapdata.entity.inspect.InspectTask;
import io.tapdata.inspect.sql.CustomSQLObject;
import io.tapdata.inspect.sql.autoUpdate.AutoUpdateFilterTime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class CustomSQLUtil {
    protected Inspect inspect;

    public CustomSQLUtil(Inspect inspect) {
        this.inspect = inspect;
    }

    public void updateCustomFunction() {
        if (null == inspect) {
            return;
        }
        List<InspectTask> tasks = inspect.getTasks();
        for (com.tapdata.entity.inspect.InspectTask task : tasks) {
            if (null == task) {
                continue;
            }
            updateCustomFunction(task.getSource());
            updateCustomFunction(task.getTarget());
        }
    }

    protected void updateCustomFunction(InspectDataSource source) {
        if (null == source) {
            return;
        }
        if (Boolean.TRUE.equals(source.isEnableCustomCommand())) {
            Map<String, Object> customCommand = source.getCustomCommand();
            String executeQuery = String.valueOf(customCommand.get("command"));
            if (!"executeQuery".equals(executeQuery)) {
                return;
            }
            Map<String, Object> params = (Map<String, Object>) customCommand.get("params");
            String op = String.valueOf(params.get("op"));
            if (!"find".equals(op)) {
                return;
            }
            Object filterObj = params.get("filter");
            if (null == filterObj) {
                return;
            }
            String filter = String.valueOf(filterObj);
            Object obj = null;
            if (cn.hutool.json.JSONUtil.isJson(filter)) {
                obj = cn.hutool.json.JSONUtil.parseObj(filter);
            }
            if (null != obj) {
                scan(obj, null, null);
                try {
                    params.put("filter", JSONUtil.obj2Json(obj));
                } catch (Exception ignore) {
                    // ...
                }
            }
        }
    }

    protected void scan(Map<String, Object> customCommand, String lastKey, Map<String, Object> lastMap) {
        Set<String> keySet = customCommand.keySet();
        for (String key : keySet) {
            Object value = customCommand.get(key);
            if (null != lastKey) {
                CustomSQLObject<Object, Object> instance = (CustomSQLObject<Object, Object>) getInstance(key);
                if (null != instance) {
                    Object execute = instance.execute(inspect, value);
                    lastMap.put(lastKey, execute);
                    continue;
                }
            }
            scan(value, key, customCommand);
        }
    }

    protected void scan(Collection<?> collection, String lastKey, Map<String, Object> lastMap) {
        for (Object coll : collection) {
            scan(coll, lastKey, lastMap);
        }
    }

    protected void scan(Object[] array, String lastKey, Map<String, Object> lastMap) {
        for (Object arr : array) {
            scan(arr, lastKey, lastMap);
        }
    }

    protected void scan(Object value, String lastKey, Map<String, Object> lastMap) {
        if (value instanceof Map) {
            scan((Map<String, Object>) value, lastKey, lastMap);
        } else if (value instanceof Collection) {
            scan((Collection<?>) value, lastKey, lastMap);
        } else if (value.getClass().isArray()) {
            scan((Object[]) value, lastKey, lastMap);
        }
    }

    protected CustomSQLObject<?, ?> getInstance(String functionName) {
        if (null == functionName) return null;
        switch (functionName) {
            case AutoUpdateFilterTime.FUNCTION_NAME:
                return new AutoUpdateFilterTime();
            default:
                return null;
        }
    }
}

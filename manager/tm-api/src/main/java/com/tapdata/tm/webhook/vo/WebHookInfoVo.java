package com.tapdata.tm.webhook.vo;

import com.tapdata.tm.webhook.enums.PingResult;
import lombok.Data;

import java.util.List;

@Data
public class WebHookInfoVo {
    private String hookId;

    private String userId;

    /**
     * WebHook name
     */
    private String hookName;

    /**
     * WebHook URL
     */
    private String url;

    /**
     * 是否启用
     */
    private Boolean open;

    private String token;

    private String httpUser;

    private String httpPwd;

    private String customHttpHeaders;

    /**
     *
     */
    private String customTemplate;

    /**
     * ping状态
     */
    private PingResult pingResult;

    /**
     * @see com.tapdata.tm.webhook.enums.HookType hookName
     */
    private List<String> hookTypes;

    /**
     * 备注
     */
    private String mark;
}

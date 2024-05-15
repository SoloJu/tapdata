package com.tapdata.tm.webhook.entity;

import com.tapdata.tm.base.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class HookOneHistory extends BaseEntity {
    private String hookId;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private String requestParams;
    private Long requestAt;

    private String responseHeaders;
    private String responseResult;
    private String responseStatus;
    private Integer responseCode;
    private Long responseAt;


    private String eventType;
    private String type;

    /**
     * @see com.tapdata.tm.webhook.enums.PingResult
     */
    private String status;

    /**
     * @see com.tapdata.tm.webhook.enums.WebHookHistoryStatus
     */
    private String historyStatus;
    private String historyMessage;
}

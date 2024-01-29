package com.tapdata.tm.alarmMail.controller;

import com.tapdata.tm.alarmMail.dto.AlarmMailDto;
import com.tapdata.tm.alarmMail.service.AlarmMailService;
import com.tapdata.tm.base.controller.BaseController;
import com.tapdata.tm.base.dto.Filter;
import com.tapdata.tm.base.dto.ResponseMessage;
import com.tapdata.tm.config.security.UserDetail;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/AlarmMail")
@Setter(onMethod_ = {@Autowired})
public class AlarmMailController extends BaseController {
    private AlarmMailService alarmMailService;
    @Operation(summary = "Find all instances of the model matched by filter from the data source")
    @GetMapping
    public ResponseMessage<AlarmMailDto> findOne() {
        return success(alarmMailService.findOne(new Filter(),getLoginUser()));
    }

    @Operation(summary = "alarm save")
    @PostMapping("/save")
    public ResponseMessage<Void> alarmSave(@RequestBody AlarmMailDto alarmMailDto) {
        UserDetail userDetail = getLoginUser();
        alarmMailService.upsert(new Query(),alarmMailDto, userDetail);
        return success();
    }
}

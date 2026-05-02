package com.ivr.admin.controller;

import com.ivr.admin.dto.CallEventItem;
import com.ivr.admin.dto.CallLogListItem;
import com.ivr.admin.dto.CallReplayResponse;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.service.CallRecordService;
import com.ivr.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/robot/call")
public class CallRecordController {

    private final CallRecordService callRecordService;

    public CallRecordController(CallRecordService callRecordService) {
        this.callRecordService = callRecordService;
    }

    @GetMapping("/page")
    public R<PageResult<CallLogListItem>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long flowId,
                                               @RequestParam(required = false) String endReason,
                                               @RequestParam(required = false) String dateFrom,
                                               @RequestParam(required = false) String dateTo) {
        return R.ok(callRecordService.page(current, size, keyword, flowId, endReason, dateFrom, dateTo));
    }

    @GetMapping("/{callUuid}/events")
    public R<List<CallEventItem>> events(@PathVariable String callUuid) {
        return R.ok(callRecordService.events(callUuid));
    }

    @GetMapping("/{callUuid}/replay")
    public R<CallReplayResponse> replay(@PathVariable String callUuid) {
        return R.ok(callRecordService.replay(callUuid));
    }
}

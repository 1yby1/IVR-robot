package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class HotlineRequest {

    @NotBlank(message = "请输入热线号码")
    @Size(max = 32, message = "热线号码不能超过 32 位")
    @Pattern(regexp = "^[0-9A-Za-z_+\\-#*]+$", message = "热线号码只能包含数字、字母、+、-、_、#、*")
    private String hotline;

    @NotNull(message = "请选择绑定流程")
    private Long flowId;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    public String getHotline() { return hotline; }
    public void setHotline(String hotline) { this.hotline = hotline; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

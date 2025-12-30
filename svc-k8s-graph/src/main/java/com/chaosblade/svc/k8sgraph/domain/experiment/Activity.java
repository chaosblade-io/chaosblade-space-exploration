package com.chaosblade.svc.k8sgraph.domain.experiment;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验活动节点（攻击/恢复）
 */
public class Activity {
    
    /** 活动ID (UUID) */
    private String id;
    
    /** 活动名称（故障名称或恢复名称） */
    private String activityName;
    
    /** 故障代码（如：chaos.container-cpu.fullload） */
    private String app_code;
    
    /** 所属流程ID */
    private String flowId;
    
    /** 阶段：attack 或 recover */
    private String stage;
    
    /** 阶段码：attack=2, recover=8 */
    private int phase;
    
    /** 节点类型（固定值：-1） */
    private int nodeType = -1;
    
    /** 执行顺序（仅recover使用，固定值：2147483646） */
    private Integer order;
    
    /** 是否必须执行（固定值：true） */
    private boolean required = true;
    
    /** 是否同步（固定值：false） */
    private boolean sync = false;
    
    /** 用户确认（固定值：true） */
    private boolean user_check = true;
    
    /** 是否可删除（固定值：false） */
    private boolean deletable = false;
    
    /** 失败容忍度（固定值：0） */
    private int failedTolerance = 0;
    
    /** 失败时是否中断（固定值：false） */
    private boolean interruptedIfFailed = false;
    
    /** 参数是否有效（固定值：true） */
    private boolean argsValid = true;
    
    /** 参数列表（完整JSON结构） */
    private List<Object> arguments = new ArrayList<>();
    
    /** 参数列表（与arguments相同） */
    private List<Object> args = new ArrayList<>();
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }
    
    public String getApp_code() { return app_code; }
    public void setApp_code(String app_code) { this.app_code = app_code; }
    
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    
    public int getPhase() { return phase; }
    public void setPhase(int phase) { this.phase = phase; }
    
    public int getNodeType() { return nodeType; }
    public void setNodeType(int nodeType) { this.nodeType = nodeType; }
    
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    
    public boolean isSync() { return sync; }
    public void setSync(boolean sync) { this.sync = sync; }
    
    public boolean isUser_check() { return user_check; }
    public void setUser_check(boolean user_check) { this.user_check = user_check; }
    
    public boolean isDeletable() { return deletable; }
    public void setDeletable(boolean deletable) { this.deletable = deletable; }
    
    public int getFailedTolerance() { return failedTolerance; }
    public void setFailedTolerance(int failedTolerance) { this.failedTolerance = failedTolerance; }
    
    public boolean isInterruptedIfFailed() { return interruptedIfFailed; }
    public void setInterruptedIfFailed(boolean interruptedIfFailed) { this.interruptedIfFailed = interruptedIfFailed; }
    
    public boolean isArgsValid() { return argsValid; }
    public void setArgsValid(boolean argsValid) { this.argsValid = argsValid; }
    
    public List<Object> getArguments() { return arguments; }
    public void setArguments(List<Object> arguments) { 
        this.arguments = arguments; 
        this.args = arguments;  // 保持同步
    }
    
    public List<Object> getArgs() { return args; }
    public void setArgs(List<Object> args) { 
        this.args = args;
        this.arguments = args;  // 保持同步
    }
    
    /**
     * 创建攻击活动
     */
    public static Activity createAttackActivity(String id, String flowId, String faultName, String faultCode) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setFlowId(flowId);
        activity.setActivityName(faultName);
        activity.setApp_code(faultCode);
        activity.setStage("attack");
        activity.setPhase(2);
        return activity;
    }
    
    /**
     * 创建恢复活动
     */
    public static Activity createRecoverActivity(String id, String flowId, String faultName, String faultCode) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setFlowId(flowId);
        activity.setActivityName("恢复(" + faultName + ")");
        activity.setApp_code(faultCode + ".stop");
        activity.setStage("recover");
        activity.setPhase(8);
        activity.setOrder(2147483646);
        return activity;
    }
}


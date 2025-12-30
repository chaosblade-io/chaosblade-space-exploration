package com.chaosblade.svc.k8sgraph.domain.experiment;

import java.util.ArrayList;
import java.util.List;

/**
 * 故障参数组
 * 
 * 对应ChaosBlade Box的参数分组结构：
 * - Fault Configuration(故障配置) - grade=1
 * - Sphere of Influence(影响范围) - grade=2
 * - General Configuration(通用配置) - grade=3
 */
public class FaultArgumentGroup {
    
    /** 分组名称 */
    private String gradeName;
    
    /** 排序 */
    private int order;
    
    /** 是否展开 */
    private boolean open = true;
    
    /** 错误信息 */
    private String errorMessage = "";
    
    /** 参数列表 */
    private List<FaultArgument> argumentList = new ArrayList<>();
    
    // Getters and Setters
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public List<FaultArgument> getArgumentList() { return argumentList; }
    public void setArgumentList(List<FaultArgument> argumentList) { this.argumentList = argumentList; }
}


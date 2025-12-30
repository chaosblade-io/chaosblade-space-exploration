package com.chaosblade.svc.k8sgraph.domain.experiment;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验流程
 */
public class Flow {
    
    /** 流程ID (UUID) */
    private String id;
    
    /** 攻击节点列表 */
    private List<Activity> attack = new ArrayList<>();
    
    /** 恢复节点列表 */
    private List<Activity> recover = new ArrayList<>();
    
    /** 检查节点列表（通常为空） */
    private List<Activity> check = new ArrayList<>();
    
    /** 准备节点列表（通常为空） */
    private List<Activity> prepare = new ArrayList<>();
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public List<Activity> getAttack() { return attack; }
    public void setAttack(List<Activity> attack) { this.attack = attack; }
    
    public List<Activity> getRecover() { return recover; }
    public void setRecover(List<Activity> recover) { this.recover = recover; }
    
    public List<Activity> getCheck() { return check; }
    public void setCheck(List<Activity> check) { this.check = check; }
    
    public List<Activity> getPrepare() { return prepare; }
    public void setPrepare(List<Activity> prepare) { this.prepare = prepare; }
}


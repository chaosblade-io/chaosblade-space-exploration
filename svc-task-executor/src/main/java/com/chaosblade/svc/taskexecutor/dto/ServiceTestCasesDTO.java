package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

/**
 * 某个服务的所有测试用例集合
 */
public class ServiceTestCasesDTO {
    private String serviceName;
    private List<TestCaseDTO> cases;

    public ServiceTestCasesDTO() {}
    public ServiceTestCasesDTO(String serviceName, List<TestCaseDTO> cases) {
        this.serviceName = serviceName;
        this.cases = cases;
    }

    public String getServiceName() { return serviceName; }
    public List<TestCaseDTO> getCases() { return cases; }
}


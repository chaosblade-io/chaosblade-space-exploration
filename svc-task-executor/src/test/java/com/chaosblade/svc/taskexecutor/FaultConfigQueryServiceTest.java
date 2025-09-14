package com.chaosblade.svc.taskexecutor;

import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.service.FaultConfigQueryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class FaultConfigQueryServiceTest {

    @Autowired
    private FaultConfigQueryService faultConfigQueryService;

    @Test
    void testGetFaultConfigsByTaskId3() {
        Long taskId = 3L;
        List<ServiceFaultConfig> list = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        // 允许为空，但不应抛异常
        Assertions.assertNotNull(list);
        // 如果存在配置，校验结构
        if (!list.isEmpty()) {
            ServiceFaultConfig item = list.get(0);
            Assertions.assertNotNull(item.getServiceName());
            Assertions.assertNotNull(item.getFaultConfig());
        }
    }
}


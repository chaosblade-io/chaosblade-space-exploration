/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.config;

/**
 * 测试环境 Jaeger 配置参数
 */
public class JaegerTestConfig {

    // Jaeger 测试服务器配置
    public static final String JAEGER_HOST = "localhost";
    public static final int JAEGER_PORT = 16685;
    public static final String SERVICE_NAME = "checkout";
    public static final String OPERATION_NAME = "oteldemo.CheckoutService/PlaceOrder";

    // 私有构造函数，防止实例化
    private JaegerTestConfig() {
        // 静态工具类，不需要实例化
    }
}

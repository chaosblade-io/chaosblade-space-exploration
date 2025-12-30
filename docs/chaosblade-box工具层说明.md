## 接口文档
### chaosblade登录
#### url
[http://1.94.151.57:7001/chaos/UserLogin](http://1.94.151.57:7001/chaos/UserLogin)

#### method
POST

#### 参数
```json
{
  "userName": "root",
  "password": "1234",
  "Namespace": "default",
  "NameSpace": "default",
  "Lang": "zh"
}
```

#### 响应示例
```json
{
    "code": 200,
    "result": {
        "currentUserId": "1872742400",
        "id": 1,
        "license": "35fd07b35c14484f966e2718507fe52b",
        "userId": "1872742400",
        "userName": "root"
    },
    "success": true
}
```

后端响应头里还有一个cookie，例如：
JSESSIONID=0389C42095C1C08B71D753AF66B3732F; Path=/; HttpOnly

需要抽取出“JSESSIONID=0389C42095C1C08B71D753AF66B3732F”，后面的每个请求里都应该有cookie：JSESSIONID=0389C42095C1C08B71D753AF66B3732F，过期时间为10分钟需要定期更新

### 获取用户应用列表
#### url
[http://1.94.151.57:7001/chaos/GetUserApplications](http://1.94.151.57:7001/chaos/GetUserApplications)

#### method
POST

#### 参数
```json
{
    "filterDisabled": true,
    "appType": 2,       
    "osType": null,
    "page": 1,
    "size": 11,
    "key": "ts-travel2-service", // 仅需要填写这个参数
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "result": {
        "data": [
            {
                "app_id": "78",
                "app_name": "ts-travel2-service",
                "app_type": 1,
                "os_type": 0,
                "scope_type": 2
            }
        ],
        "hasMore": false,
        "page": 1,
        "pageSize": 11,
        "pages": 1,
        "total": 1
    },
    "success": true
}
```

### 获取用户应用分组
#### url
[http://1.94.151.57:7001/chaos/GetUserApplicationGroups](http://1.94.151.57:7001/chaos/GetUserApplicationGroups)

#### method
POST

#### 参数
```json
{
    "app_id": "78", // 仅需要填写这个参数
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "result": [
        "ts-travel2-service-group"
    ],
    "success": true
}
```

### 获取应用对应的机器列表
#### url
[http://1.94.151.57:7001/chaos/GetScopesByApplication](http://1.94.151.57:7001/chaos/GetScopesByApplication)

#### method
POST

#### 参数
```json
{
    "page": 1,
    "size": 10,
    "key": "",
    "tags": [],
    "kubNamespaces": [],
    "clusterIds": [],
    "app_id": "78", // 需要填写这个参数
    "app_group": [
        "ts-travel2-service-group" // 需要填写这个参数
    ],
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "result": {
        "data": [
            {
                "allow": true,
                "app": "ts-travel2-service",
                "appConfigurationId": "b9ed68d2a30038afc7cd1248509a614d",
                "appId": "78",
                "appScope": true,
                "clusterId": "0327",
                "clusterName": "mycluster",
                "deviceConfigurationId": "988573a92be8608ced3e0296a30eb9a5",
                "deviceId": "tcse-v100-03",
                "deviceName": "ts-travel2-service-7896995b98-2865d",
                "deviceType": 0,
                "invalid": false,
                "ip": "10.0.3.246",
                "k8s": true,
                "kubNamespace": "train-ticket",
                "longAppId": 78,
                "master": false,
                "nodeGroup": "ts-travel2-service-group",
                "osType": 0,
                "port": 19527,
                "privateIp": "10.0.3.246",
                "scopeType": 2,
                "targetIp": "10.0.3.246",
                "type": "host",
                "vpcId": "1111"
            }
        ],
        "hasMore": false,
        "page": 1,
        "pageSize": 10,
        "pages": 1,
        "total": 1
    },
    "success": true
}
```

### 获取故障场景分类
#### url
[http://1.94.151.57:7001/chaos/QuerySceneFunctionCategories](http://1.94.151.57:7001/chaos/QuerySceneFunctionCategories)

#### method
POST

#### 参数
```json
{
    "phase": 2,
    "scopeType": 2,
    "filterNoChild": true,
    "cloudServiceType": "",
    "osType": 0,
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "code": "200",
    "result": [
        {
            "categoryId": "1216606260205703169",
            "children": [
                {
                    "categoryId": "1216606329818566658",
                    "level": 2,
                    "name": "CPU资源",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216606392489857026",
                    "level": 2,
                    "name": "内存资源",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216606480226308098",
                    "level": 2,
                    "name": "磁盘资源",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216672245176541185",
                    "level": 2,
                    "name": "网络资源",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1217020049010950145",
                    "level": 2,
                    "name": "应用进程",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1217716899703644162",
                    "level": 2,
                    "name": "容器资源",
                    "parentId": "1216606260205703169",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                }
            ],
            "level": 1,
            "name": "系统资源",
            "parentId": "",
            "phase": 2,
            "supportScopeTypes": [
                2,
                0
            ],
            "type": 0
        },
        {
            "categoryId": "1216606670115033089",
            "children": [
                {
                    "categoryId": "1216606744870113281",
                    "level": 2,
                    "name": "延迟",
                    "parentId": "1216606670115033089",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216606820073984002",
                    "level": 2,
                    "name": "抛异常",
                    "parentId": "1216606670115033089",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216606920988938241",
                    "level": 2,
                    "name": "自定义故障",
                    "parentId": "1216606670115033089",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1216669321109118978",
                    "level": 2,
                    "name": "篡改数据",
                    "parentId": "1216606670115033089",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                },
                {
                    "categoryId": "1217022989201276929",
                    "children": [
                        {
                            "categoryId": "1217023981502308353",
                            "level": 3,
                            "name": "内存资源",
                            "parentId": "1217022989201276929",
                            "phase": 2,
                            "supportScopeTypes": [
                                2,
                                0
                            ],
                            "type": 0
                        }
                    ],
                    "level": 2,
                    "name": "资源占用",
                    "parentId": "1216606670115033089",
                    "phase": 2,
                    "supportScopeTypes": [
                        2,
                        0
                    ],
                    "type": 0
                }
            ],
            "level": 1,
            "name": "JAVA进程内",
            "parentId": "",
            "phase": 2,
            "supportScopeTypes": [
                2,
                0
            ],
            "type": 0
        }
    ],
    "success": true
}
```

### 获取故障场景详情
#### url
[http://1.94.151.57:7001/chaos/QuerySceneFunctionByCategoryId](http://1.94.151.57:7001/chaos/QuerySceneFunctionByCategoryId)

#### method
POST

#### 参数
```json
{
    "page": 1,
    "categoryId": "1216606392489857026", // 仅需要填写这个参数
    "phase": 2,
    "scopeType": 2,
    "k8sResourceType": 1,
    "size": 12,
    "osType": 0,
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "code": "200",
    "result": {
        "data": [
            {
                "agentRequired": false,
                "categoryIds": [
                    "1216606392489857026"
                ],
                "code": "chaos.container-mem.load",
                "description": "占用指定百分比的系统内存，如果当前系统内存百分比占用少于目标百分比，则会占用直到指定百分比。",
                "enabled": 2,
                "functionId": "2000401221328011266",
                "gmtCreate": 1765767792000,
                "name": "容器内内存负载",
                "nextDepAppCode": "chaos.container-mem.load.stop",
                "parentFunctionId": "2000401221328011266",
                "parentName": "容器内内存负载",
                "phaseFlag": 2,
                "sceneId": "2000401076947484673",
                "source": 1,
                "supportOsTypes": [
                    0
                ],
                "supportScopeTypes": [
                    2
                ],
                "type": "chaos_blade",
                "version": "1.0.0"
            }
        ],
        "hasMore": false,
        "page": 1,
        "pageSize": 12,
        "pages": 1,
        "total": 1
    },
    "success": true
}
```


### 获取故障参数模版
#### url
[http://1.94.151.57:7001/chaos/InitMiniFlowByAppCode](http://1.94.151.57:7001/chaos/InitMiniFlowByAppCode)

#### method
POST

#### 参数
```json
{
    "appCode": "chaos.container-mem.load", // 需要填写这个参数
    "source": 1,
    "appId": "2",
    "nodeGroups": [
        "node-agent-group" // 需要填写这个参数
    ],
    "namespace": "default",
    "Lang": "zh",
    "Namespace": "default"
}
```

#### 响应示例
```json
{
    "result": {
        "recover": [
            {
                "activityName": "恢复(容器内内存负载)",
                "app_code": "chaos.container-mem.load.stop",
                "arguments": [],
                "failedTolerance": 0,
                "interruptedIfFailed": false,
                "order": 2147483646,
                "required": true,
                "sync": false,
                "user_check": true
            }
        ],
        "attack": [
            {
                "activityName": "容器内内存负载",
                "app_code": "chaos.container-mem.load",
                "arguments": [
                    {
                        "argumentList": [
                            {
                                "alias": "avoid-being-killed",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Prevent mem-burn process from being killed by oom-killer",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 1,
                                "name": "avoid-being-killed",
                                "parameterId": "2000401221462228994"
                            },
                            {
                                "alias": "mem-percent",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "percent of burn Memory (0-100), must be a positive integer",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 1,
                                "name": "mem-percent",
                                "parameterId": "2000401221328011267"
                            },
                            {
                                "alias": "mode",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "burn memory mode, cache or ram.",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 1,
                                "name": "mode",
                                "parameterId": "2000401221411897345"
                            },
                            {
                                "alias": "rate",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "burn memory rate, unit is M/S, only support for ram mode.",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 1,
                                "name": "rate",
                                "parameterId": "2000401221382537217"
                            },
                            {
                                "alias": "reserve",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "reserve to burn Memory, unit is MB. If the mem-percent flag exist, use mem-percent first.",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 1,
                                "name": "reserve",
                                "parameterId": "2000401221353177090"
                            }
                        ],
                        "gradeName": "Fault Configuration(故障配置)",
                        "open": true,
                        "order": 1
                    },
                    {
                        "argumentList": [
                            {
                                "alias": "namespace",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": true,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Namespace, such as default, only one value can be specified",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "namespace",
                                "parameterId": "2000401221571280897"
                            },
                            {
                                "alias": "container-ids",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Container ids",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "container-ids",
                                "parameterId": "2000401221650972673"
                            },
                            {
                                "alias": "container-index",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Container index, start from 0",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "container-index",
                                "parameterId": "2000401221701304321"
                            },
                            {
                                "alias": "container-names",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Container names",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "container-names",
                                "parameterId": "2000401221676138498"
                            },
                            {
                                "alias": "include-buffer-cache",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Ram mode mem-percent is include buffer/cache",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "include-buffer-cache",
                                "parameterId": "2000401221437063169"
                            },
                            {
                                "alias": "labels",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Label selector, the relationship between values that are or",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "labels",
                                "parameterId": "2000401221596446722"
                            },
                            {
                                "alias": "names",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Resource names, such as pod name. You must add namespace flag for it. Multiple parameters are separated directly by commas",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 2,
                                "name": "names",
                                "parameterId": "2000401221546115073"
                            }
                        ],
                        "gradeName": "Sphere of Influence(影响范围)",
                        "open": true,
                        "order": 2
                    },
                    {
                        "argumentList": [
                            {
                                "alias": "async",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "whether to create asynchronously, default is false",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "async",
                                "parameterId": "2000401221869076481"
                            },
                            {
                                "alias": "chaosblade-deploy-mode",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "The mode of chaosblade deployment in container, the values are copy and download, the default value is copy which copy tool from the operator to the target container. If you select download mode, the operator will download chaosblade tool from the chaosblade-download-url.",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "chaosblade-deploy-mode",
                                "parameterId": "2000401221780996098"
                            },
                            {
                                "alias": "chaosblade-download-url",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "The chaosblade downloaded address. If you use download deployment mode, you must specify the value, or config chaosblade-download-url when deploying the operator",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "chaosblade-download-url",
                                "parameterId": "2000401221810356225"
                            },
                            {
                                "alias": "chaosblade-override",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Override the exists chaosblade tool in the target container or not, default value is false",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "chaosblade-override",
                                "parameterId": "2000401221751635969"
                            },
                            {
                                "alias": "chaosblade-path",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "description": "Chaosblade tool deployment path, default value is /opt. Please select a path with write permission",
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "chaosblade-path",
                                "parameterId": "2000401221726470145"
                            },
                            {
                                "alias": "debug",
                                "component": {
                                    "cipherText": "",
                                    "defaultValue": "",
                                    "opLevel": 0,
                                    "requestUrl": "",
                                    "required": false,
                                    "type": "input",
                                    "unit": ""
                                },
                                "enabled": true,
                                "functionId": "2000401221328011266",
                                "grade": 3,
                                "name": "debug",
                                "parameterId": "2000401221923602433"
                            }
                        ],
                        "gradeName": "General Configuration(通用配置)",
                        "open": false,
                        "order": 3
                    }
                ],
                "failedTolerance": 0,
                "interruptedIfFailed": false,
                "required": true,
                "sync": false,
                "user_check": true
            }
        ]
    },
    "success": true
}
```

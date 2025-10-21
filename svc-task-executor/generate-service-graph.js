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

const fs = require('fs');

function generateServiceGraph() {
    try {
        // 读取处理后的 trace 数据
        const rawData = fs.readFileSync('trace1-processed.json', 'utf8');
        const spans = JSON.parse(rawData);
        
        console.log(`Processing ${spans.length} spans...`);
        
        // 用于存储服务和边的集合
        const services = new Set();
        const edges = new Map(); // key: "source->target", value: edge info
        
        // 数据库关键词列表
        const dbKeywords = ['mongo', 'mysql', 'redis', 'postgres', 'elasticsearch', 'cassandra'];
        
        // 判断是否为数据库服务
        function isDatabaseService(address) {
            if (!address) return false;
            const lowerAddr = address.toLowerCase();
            return dbKeywords.some(keyword => lowerAddr.includes(keyword));
        }
        
        // 从 URL 中提取服务名
        function extractServiceFromUrl(urlFull) {
            if (!urlFull) return null;
            try {
                const url = new URL(urlFull);
                return url.hostname;
            } catch (e) {
                // 如果不是完整 URL，尝试从字符串中提取
                const match = urlFull.match(/https?:\/\/([^:\/]+)/);
                return match ? match[1] : null;
            }
        }
        
        // 处理每个 span
        spans.forEach(span => {
            // 添加服务到集合
            if (span.service) {
                services.add(span.service);
            }
            
            // 只处理有 url.full 的 span（跨服务 HTTP 客户端调用）
            if (!span.attributes || !span.attributes['url.full']) {
                return;
            }
            
            const callerService = span.service;
            const urlFull = span.attributes['url.full'];
            const serverAddress = span.attributes['server.address'];
            const httpMethod = span.attributes['http.request.method'] || 'UNKNOWN';
            const serverPort = span.attributes['server.port'] || '';
            
            // 确定被调用的服务
            let calleeService = serverAddress || extractServiceFromUrl(urlFull);
            
            if (!callerService || !calleeService) {
                return;
            }
            
            // 过滤规则
            // 1. 排除自调用
            if (callerService === calleeService) {
                return;
            }
            
            // 2. 排除数据库调用
            if (isDatabaseService(calleeService)) {
                return;
            }
            
            // 3. 添加被调用服务到服务集合
            services.add(calleeService);
            
            // 创建边的唯一标识
            const edgeKey = `${callerService}->${calleeService}`;
            
            // 如果边已存在，更新信息（合并多个调用）
            if (edges.has(edgeKey)) {
                const existingEdge = edges.get(edgeKey);
                // 合并 HTTP 方法
                if (!existingEdge.methods.includes(httpMethod)) {
                    existingEdge.methods.push(httpMethod);
                }
                // 合并端口
                if (serverPort && !existingEdge.ports.includes(serverPort)) {
                    existingEdge.ports.push(serverPort);
                }
                // 增加调用次数
                existingEdge.callCount++;
                // 添加示例 URL（保留前几个）
                if (existingEdge.exampleUrls.length < 3 && !existingEdge.exampleUrls.includes(urlFull)) {
                    existingEdge.exampleUrls.push(urlFull);
                }
            } else {
                // 创建新边
                edges.set(edgeKey, {
                    source: callerService,
                    target: calleeService,
                    methods: [httpMethod],
                    ports: serverPort ? [serverPort] : [],
                    callCount: 1,
                    exampleUrls: [urlFull]
                });
            }
        });
        
        // 转换为最终格式
        const nodeList = Array.from(services).map(service => ({ service }));
        const edgeList = Array.from(edges.values()).map(edge => ({
            source: edge.source,
            target: edge.target,
            method: edge.methods.join(','),
            port: edge.ports.join(','),
            url: edge.exampleUrls[0], // 使用第一个 URL 作为代表
            callCount: edge.callCount,
            allMethods: edge.methods,
            allPorts: edge.ports,
            exampleUrls: edge.exampleUrls
        }));
        
        // 生成结果
        const result = {
            nodes: nodeList,
            edges: edgeList,
            summary: {
                totalServices: nodeList.length,
                totalEdges: edgeList.length,
                totalCalls: edgeList.reduce((sum, edge) => sum + edge.callCount, 0)
            }
        };
        
        // 写入文件
        fs.writeFileSync('service-dependency-graph.json', JSON.stringify(result, null, 2), 'utf8');
        
        // 输出统计信息
        console.log('\n=== Service Dependency Graph Generated ===');
        console.log(`Total Services: ${result.summary.totalServices}`);
        console.log(`Total Service Dependencies: ${result.summary.totalEdges}`);
        console.log(`Total Cross-Service Calls: ${result.summary.totalCalls}`);
        
        console.log('\n=== Services ===');
        nodeList.forEach(node => console.log(`- ${node.service}`));
        
        console.log('\n=== Service Dependencies ===');
        edgeList.forEach(edge => {
            console.log(`${edge.source} -> ${edge.target} (${edge.method}) [${edge.callCount} calls]`);
        });
        
        console.log(`\nOutput saved to: service-dependency-graph.json`);
        
        return result;
        
    } catch (error) {
        console.error('Error generating service graph:', error);
        throw error;
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    generateServiceGraph();
}

module.exports = { generateServiceGraph };

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

// 读取原始数据
const rawData = fs.readFileSync('trace1.json', 'utf8');
const spans = JSON.parse(rawData);

console.log('Original spans count:', spans.length);

// 处理每个 span，只保留关键字段
const processedSpans = spans.map(span => {
    const processedSpan = {
        service: span.service,
        trace_id: span.trace_id,
        id: span.id,
        parent_id: span.parent_id,
        name: span.name,
        timestamp: span.timestamp,
        duration: span.duration,
        status: span.status
    };
    
    // 处理 attributes，只保留关键字段
    if (span.attributes) {
        const filteredAttributes = {};
        
        // 保留的关键字段列表
        const keyFields = [
            'http.request.method',
            'http.response.status_code', 
            'http.route',
            'server.address',
            'server.port',
            'service.name',
            'url.path',
            'url.scheme',
            'url.full'
        ];
        
        keyFields.forEach(field => {
            if (span.attributes[field] !== undefined) {
                filteredAttributes[field] = span.attributes[field];
            }
        });
        
        processedSpan.attributes = filteredAttributes;
    }
    
    return processedSpan;
});

// 写入处理后的文件
fs.writeFileSync('trace1-processed.json', JSON.stringify(processedSpans, null, 2), 'utf8');

console.log('Processed spans count:', processedSpans.length);
console.log('Output file: trace1-processed.json');

// 计算文件大小减少
const originalSize = fs.statSync('trace1.json').size;
const processedSize = fs.statSync('trace1-processed.json').size;
const reduction = ((originalSize - processedSize) / originalSize * 100).toFixed(1);

console.log(`File size reduction: ${reduction}% (${originalSize} -> ${processedSize} bytes)`);

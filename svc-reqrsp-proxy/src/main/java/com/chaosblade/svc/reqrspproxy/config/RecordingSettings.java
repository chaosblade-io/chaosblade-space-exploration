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

package com.chaosblade.svc.reqrspproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecordingSettings {

    @Value("${reqrsp.tap.concurrent:18}")
    private int tapConcurrent;

    @Value("${reqrsp.tap.readRetry.maxRetries:5}")
    private int tapReadRetryMaxRetries;

    @Value("${reqrsp.tap.readRetry.sleepMillis:300}")
    private long tapReadRetrySleepMillis;

    @Value("${reqrsp.collection.concurrent:18}")
    private int collectionConcurrent;

    @Value("${reqrsp.debug.exportRaw:false}")
    private boolean debugExportRawEnabled;

    public int getTapConcurrent() { return tapConcurrent; }
    public int getTapReadRetryMaxRetries() { return tapReadRetryMaxRetries; }
    public long getTapReadRetrySleepMillis() { return tapReadRetrySleepMillis; }
    public int getCollectionConcurrent() { return collectionConcurrent; }
    public boolean isDebugExportRawEnabled() { return debugExportRawEnabled; }
}


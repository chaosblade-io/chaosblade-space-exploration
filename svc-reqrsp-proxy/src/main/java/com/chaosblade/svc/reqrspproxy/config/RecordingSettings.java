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


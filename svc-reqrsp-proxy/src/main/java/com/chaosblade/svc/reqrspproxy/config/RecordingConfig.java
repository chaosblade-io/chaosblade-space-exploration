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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 录制配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "recording")
public class RecordingConfig {
    
    private Envoy envoy = new Envoy();
    private Integer defaultDurationSec = 600;
    private Integer autoCollectIntervalSec = 30;
    
    public static class Envoy {
        private String image = "envoyproxy/envoy:v1.28.3";
        private Integer port = 15006;
        private Integer adminPort = 9901;
        private String tapDir = "/var/log/envoy/taps";
        private Long maxBufferedBytes = 2097152L;
        
        public String getImage() {
            return image;
        }
        
        public void setImage(String image) {
            this.image = image;
        }
        
        public Integer getPort() {
            return port;
        }
        
        public void setPort(Integer port) {
            this.port = port;
        }
        
        public Integer getAdminPort() {
            return adminPort;
        }
        
        public void setAdminPort(Integer adminPort) {
            this.adminPort = adminPort;
        }
        
        public String getTapDir() {
            return tapDir;
        }
        
        public void setTapDir(String tapDir) {
            this.tapDir = tapDir;
        }
        
        public Long getMaxBufferedBytes() {
            return maxBufferedBytes;
        }
        
        public void setMaxBufferedBytes(Long maxBufferedBytes) {
            this.maxBufferedBytes = maxBufferedBytes;
        }
    }
    
    public Envoy getEnvoy() {
        return envoy;
    }
    
    public void setEnvoy(Envoy envoy) {
        this.envoy = envoy;
    }
    
    public Integer getDefaultDurationSec() {
        return defaultDurationSec;
    }
    
    public void setDefaultDurationSec(Integer defaultDurationSec) {
        this.defaultDurationSec = defaultDurationSec;
    }
    
    public Integer getAutoCollectIntervalSec() {
        return autoCollectIntervalSec;
    }
    
    public void setAutoCollectIntervalSec(Integer autoCollectIntervalSec) {
        this.autoCollectIntervalSec = autoCollectIntervalSec;
    }
}

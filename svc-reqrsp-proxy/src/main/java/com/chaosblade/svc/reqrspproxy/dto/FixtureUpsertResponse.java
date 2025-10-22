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

package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** Fixture管理 - 批量更新响应 */
public class FixtureUpsertResponse {

  /** 已更新的规则数量 */
  private Integer upserted;

  /** 过期时间 */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime expiresAt;

  // Constructors
  public FixtureUpsertResponse() {}

  public FixtureUpsertResponse(Integer upserted, LocalDateTime expiresAt) {
    this.upserted = upserted;
    this.expiresAt = expiresAt;
  }

  // Getters and Setters
  public Integer getUpserted() {
    return upserted;
  }

  public void setUpserted(Integer upserted) {
    this.upserted = upserted;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  @Override
  public String toString() {
    return "FixtureUpsertResponse{" + "upserted=" + upserted + ", expiresAt=" + expiresAt + '}';
  }
}

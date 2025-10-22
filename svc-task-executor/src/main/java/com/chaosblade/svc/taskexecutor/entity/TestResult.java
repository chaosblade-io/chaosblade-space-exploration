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

package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "test_result")
public class TestResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "execution_id", nullable = false)
  private Long executionId;

  @Column(name = "test_case_id", nullable = false)
  private Long testCaseId;

  @Column(name = "request_url", columnDefinition = "TEXT")
  private String requestUrl;

  @Column(name = "request_method", length = 10)
  private String requestMethod;

  @Column(name = "response_code")
  private Integer responseCode;

  @Column(name = "response_body", columnDefinition = "LONGTEXT")
  private String responseBody;

  @Column(name = "p50")
  private Integer p50;

  @Column(name = "p95")
  private Integer p95;

  @Column(name = "p99")
  private Integer p99;

  @Column(name = "err_rate", precision = 5, scale = 2)
  private java.math.BigDecimal errRate;

  public Long getId() {
    return id;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public Long getTestCaseId() {
    return testCaseId;
  }

  public void setTestCaseId(Long testCaseId) {
    this.testCaseId = testCaseId;
  }

  public String getRequestUrl() {
    return requestUrl;
  }

  public void setRequestUrl(String requestUrl) {
    this.requestUrl = requestUrl;
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public void setRequestMethod(String requestMethod) {
    this.requestMethod = requestMethod;
  }

  public Integer getResponseCode() {
    return responseCode;
  }

  public void setResponseCode(Integer responseCode) {
    this.responseCode = responseCode;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Integer getP50() {
    return p50;
  }

  public void setP50(Integer p50) {
    this.p50 = p50;
  }

  public Integer getP95() {
    return p95;
  }

  public void setP95(Integer p95) {
    this.p95 = p95;
  }

  public Integer getP99() {
    return p99;
  }

  public void setP99(Integer p99) {
    this.p99 = p99;
  }

  public java.math.BigDecimal getErrRate() {
    return errRate;
  }

  public void setErrRate(java.math.BigDecimal errRate) {
    this.errRate = errRate;
  }
}

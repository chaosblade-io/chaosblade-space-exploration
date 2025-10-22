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

package com.chaosblade.svc.taskexecutor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/** 认证服务测试 */
class AuthenticationServiceTest {

  private AuthenticationService authenticationService;
  private RestTemplate mockRestTemplate;

  @BeforeEach
  void setUp() {
    authenticationService = new AuthenticationService();
    mockRestTemplate = mock(RestTemplate.class);

    // 使用反射设置私有字段
    ReflectionTestUtils.setField(authenticationService, "restTemplate", mockRestTemplate);
  }

  @Test
  void testSuccessfulLogin() {
    // 准备模拟响应
    AuthenticationService.LoginResponse loginResponse = new AuthenticationService.LoginResponse();
    loginResponse.setStatus(1);
    loginResponse.setMsg("login success");

    AuthenticationService.LoginData loginData = new AuthenticationService.LoginData();
    loginData.setUserId("4d2a46c7-71cb-4cf1-b5bb-b68406d9da6f");
    loginData.setUsername("fdse_microservice");
    loginData.setToken("eyJhbGciOiJIUzI1NiJ9.test.token");

    loginResponse.setData(loginData);

    ResponseEntity<AuthenticationService.LoginResponse> responseEntity =
        new ResponseEntity<>(loginResponse, HttpStatus.OK);

    // 模拟RestTemplate调用
    when(mockRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(AuthenticationService.LoginResponse.class)))
        .thenReturn(responseEntity);

    // 执行测试
    String token = authenticationService.getValidToken();

    // 验证结果
    assertNotNull(token);
    assertEquals("eyJhbGciOiJIUzI1NiJ9.test.token", token);
    assertTrue(authenticationService.isTokenValid());

    // 验证RestTemplate被调用
    verify(mockRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(AuthenticationService.LoginResponse.class));
  }

  @Test
  void testCreateAuthenticatedHeaders() {
    // 准备模拟响应
    AuthenticationService.LoginResponse loginResponse = new AuthenticationService.LoginResponse();
    loginResponse.setStatus(1);
    loginResponse.setMsg("login success");

    AuthenticationService.LoginData loginData = new AuthenticationService.LoginData();
    loginData.setToken("test-token-123");
    loginResponse.setData(loginData);

    ResponseEntity<AuthenticationService.LoginResponse> responseEntity =
        new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(mockRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(AuthenticationService.LoginResponse.class)))
        .thenReturn(responseEntity);

    // 执行测试
    HttpHeaders headers = authenticationService.createAuthenticatedHeaders();

    // 验证结果
    assertNotNull(headers);
    assertEquals("application/json", headers.getContentType().toString());
    assertEquals("Bearer test-token-123", headers.getFirst("Authorization"));
  }

  @Test
  void testClearToken() {
    // 先获取token
    AuthenticationService.LoginResponse loginResponse = new AuthenticationService.LoginResponse();
    loginResponse.setStatus(1);
    loginResponse.setMsg("login success");

    AuthenticationService.LoginData loginData = new AuthenticationService.LoginData();
    loginData.setToken("test-token");
    loginResponse.setData(loginData);

    ResponseEntity<AuthenticationService.LoginResponse> responseEntity =
        new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(mockRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(AuthenticationService.LoginResponse.class)))
        .thenReturn(responseEntity);

    // 获取token
    authenticationService.getValidToken();
    assertTrue(authenticationService.isTokenValid());

    // 清除token
    authenticationService.clearToken();
    assertFalse(authenticationService.isTokenValid());
  }

  @Test
  void testLoginFailure() {
    // 模拟登录失败响应
    AuthenticationService.LoginResponse loginResponse = new AuthenticationService.LoginResponse();
    loginResponse.setStatus(0);
    loginResponse.setMsg("login failed");

    ResponseEntity<AuthenticationService.LoginResponse> responseEntity =
        new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(mockRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(AuthenticationService.LoginResponse.class)))
        .thenReturn(responseEntity);

    // 执行测试并验证异常
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              authenticationService.getValidToken();
            });

    assertTrue(exception.getMessage().contains("Authentication failed"));
  }
}

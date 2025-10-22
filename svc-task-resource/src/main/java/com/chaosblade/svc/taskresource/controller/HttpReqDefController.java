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

package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import com.chaosblade.svc.taskresource.service.HttpReqDefService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HttpReqDefController {

  private static final Logger logger = LoggerFactory.getLogger(HttpReqDefController.class);

  @Autowired private HttpReqDefService service;

  @GetMapping("/http-req-defs")
  public ApiResponse<PageResponse<HttpReqDef>> pageQuery(
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "method", required = false) HttpReqDef.HttpMethod method,
      @RequestParam(value = "apiId", required = false) Long apiId,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
    logger.info(
        "GET /api/http-req-defs - name: {}, method: {}, apiId: {}, page: {}, size: {}",
        name,
        method,
        apiId,
        page,
        size);
    return ApiResponse.success(service.pageQuery(name, method, apiId, page, size));
  }

  @GetMapping("/http-req-defs/{id}")
  public ApiResponse<HttpReqDef> getById(@PathVariable Long id) {
    logger.info("GET /api/http-req-defs/{}", id);
    return ApiResponse.success(service.getById(id));
  }

  @GetMapping("/http-req-defs/code/{code}")
  public ApiResponse<HttpReqDef> getByCode(@PathVariable String code) {
    logger.info("GET /api/http-req-defs/code/{}", code);
    return service
        .getByCode(code)
        .map(ApiResponse::success)
        .orElseGet(() -> ApiResponse.error("HTTP_REQ_DEF_NOT_FOUND", "请求定义不存在: " + code));
  }

  @PostMapping("/http-req-defs")
  public ApiResponse<HttpReqDef> create(@Valid @RequestBody HttpReqDef def) {
    logger.info("POST /api/http-req-defs - code: {}", def.getCode());
    return ApiResponse.success(service.create(def));
  }

  @PutMapping("/http-req-defs/{id}")
  public ApiResponse<HttpReqDef> update(@PathVariable Long id, @Valid @RequestBody HttpReqDef def) {
    logger.info("PUT /api/http-req-defs/{}", id);
    return ApiResponse.success(service.update(id, def));
  }

  @DeleteMapping("/http-req-defs/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    logger.info("DELETE /api/http-req-defs/{}", id);
    service.delete(id);
    return ApiResponse.success();
  }
}

# CloudSDKHandler 云SDK处理器

## 概述
CloudSDKHandler 是 DJI Cloud SDK 的核心切面处理器，使用 Spring AOP 技术对 SDK API 调用进行拦截和处理。它负责版本兼容性检查、请求参数验证、响应数据验证等关键功能。

## 核心功能

### 1. 版本兼容性检查 (checkCloudSDK)
**切面**: `@Before("execution(public * com.dji.sdk.cloudapi.*.api.*.*(com.dji.sdk.config.version.GatewayManager, ..))")`

**功能**:
- 检查设备类型是否支持当前API
- 检查设备版本是否支持当前API
- 通过 `@CloudSDKVersion` 注解进行版本控制

**检查逻辑**:
```java
@Before("execution(public * com.dji.sdk.cloudapi.*.api.*.*(com.dji.sdk.config.version.GatewayManager, ..))")
public void checkCloudSDK(JoinPoint point) {
    GatewayManager deviceSDK = (GatewayManager) point.getArgs()[0];
    CloudSDKVersion since = ((MethodSignature) point.getSignature()).getMethod().getDeclaredAnnotation(CloudSDKVersion.class);
    
    if (Objects.isNull(since)) {
        return; // 没有版本注解，跳过检查
    }
    
    // 检查设备类型支持
    if (!deviceSDK.isTypeSupport(since)) {
        throw new CloudSDKException(CloudSDKErrorEnum.DEVICE_TYPE_NOT_SUPPORT);
    }
    
    // 检查设备版本支持
    if (!deviceSDK.isVersionSupport(since)) {
        throw new CloudSDKException(CloudSDKErrorEnum.DEVICE_VERSION_NOT_SUPPORT);
    }
}
```

### 2. 请求参数验证 (checkRequest)
**切面**: `@Before("execution(public * com.dji.sdk.cloudapi.*.api.*.*(com.dji.sdk.config.version.GatewayManager, com.dji.sdk.common.BaseModel+))")`

**功能**:
- 验证请求参数的有效性
- 确保BaseModel参数符合要求

**验证逻辑**:
```java
@Before("execution(public * com.dji.sdk.cloudapi.*.api.*.*(com.dji.sdk.config.version.GatewayManager, com.dji.sdk.common.BaseModel+))")
public void checkRequest(JoinPoint point) {
    Common.validateModel((BaseModel) point.getArgs()[1], (GatewayManager) point.getArgs()[0]);
}
```

### 3. 响应数据验证 (checkResponse)
**切面**: `@AfterReturning(value = "execution(public com.dji.sdk.common.HttpResultResponse+ com.dji.sdk.cloudapi.*.api.*.*(..))", returning = "response")`

**功能**:
- 验证响应数据的完整性
- 检查返回类型是否匹配
- 处理空数据情况
- 验证分页数据的正确性

**验证逻辑**:
```java
@AfterReturning(value = "execution(public com.dji.sdk.common.HttpResultResponse+ com.dji.sdk.cloudapi.*.api.*.*(..))", returning = "response")
public void checkResponse(JoinPoint point, HttpResultResponse response) {
    if (null == response) {
        throw new CloudSDKException(CloudSDKErrorEnum.INVALID_PARAMETER, "The return value cannot be null.");
    }
    
    Method method = ((MethodSignature) point.getSignature()).getMethod();
    if (method.getGenericReturnType() instanceof Class) {
        if (null == response.getData()) {
            response.setData(""); // 设置默认空字符串
        }
        return;
    }
    
    // 检查泛型类型
    checkClassType((ParameterizedType) method.getGenericReturnType(), response);
    // 验证数据内容
    validData(response.getData(), point.getArgs()[0]);
}
```

## 辅助方法

### 1. checkClassType - 类型检查
**功能**: 检查响应数据的类型是否与期望类型匹配
**处理**:
- 处理空数据情况，设置默认值
- 验证类型兼容性
- 支持List和自定义对象类型

### 2. validData - 数据验证
**功能**: 验证响应数据的内容有效性
**支持类型**:
- **BaseModel**: 调用 `Common.validateModel()` 验证
- **PaginationData**: 验证分页数据，处理空列表情况

**分页数据处理**:
```java
if (data instanceof PaginationData) {
    List<BaseModel> list = ((PaginationData) data).getList();
    if (null == list) {
        ((PaginationData) data).setList(Collections.EMPTY_LIST);
        // 设置分页信息
        ((PaginationData) data).setPagination(
            new Pagination().setPage((int) page.get(arg)).setPageSize((int) pageSize.get(arg))
        );
    }
    // 验证列表中的每个BaseModel
    for (BaseModel model : list) {
        Common.validateModel(model);
    }
}
```

## 切面拦截范围

### 1. API方法拦截
- **包路径**: `com.dji.sdk.cloudapi.*.api.*.*`
- **方法签名**: 包含 `GatewayManager` 参数的方法
- **返回类型**: `HttpResultResponse` 及其子类

### 2. 拦截时机
- **Before**: 方法执行前进行版本和参数检查
- **AfterReturning**: 方法执行后进行响应验证

## 异常处理

### 异常类型
1. **CloudSDKException**: SDK相关异常
   - `DEVICE_TYPE_NOT_SUPPORT`: 设备类型不支持
   - `DEVICE_VERSION_NOT_SUPPORT`: 设备版本不支持
   - `INVALID_PARAMETER`: 参数无效

### 异常场景
- 设备类型不匹配
- 设备版本过低
- 请求参数无效
- 响应数据为空或类型不匹配
- 分页数据异常

## 设计特点

### 1. AOP切面编程
- 使用Spring AOP实现横切关注点
- 非侵入式地添加验证逻辑
- 统一处理所有API调用

### 2. 版本控制
- 通过注解实现版本兼容性检查
- 支持设备类型和版本双重验证
- 灵活的版本管理机制

### 3. 数据完整性
- 自动处理空数据情况
- 验证数据类型的正确性
- 确保分页数据的完整性

### 4. 异常安全
- 完善的异常处理机制
- 清晰的错误信息提示
- 优雅的降级处理

## 使用示例

### API方法定义
```java
@CloudSDKVersion(since = "1.0.0")
public HttpResultResponse<DeviceInfo> getDeviceInfo(GatewayManager gatewayManager, DeviceQueryParam param) {
    // API实现
}
```

### 自动验证流程
1. **调用前**: 检查设备类型和版本支持
2. **参数验证**: 验证 `DeviceQueryParam` 参数有效性
3. **执行API**: 执行具体的API逻辑
4. **返回验证**: 验证 `HttpResultResponse<DeviceInfo>` 响应数据

## 注意事项
- 所有API方法都会自动应用这些验证规则
- 版本注解是可选的，没有注解的方法会跳过版本检查
- 响应数据会自动处理空值情况
- 分页数据会自动设置默认的分页信息
- 异常会中断API调用并返回相应的错误信息

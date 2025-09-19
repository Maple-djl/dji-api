# CloudSDKVersion 注解类笔记

## 概述
`CloudSDKVersion` 是一个用于标记 Cloud SDK 版本兼容性的注解类，主要用于字段和方法上，用于指定功能的版本支持范围。

## 基本信息
- **包路径**: `com.dji.sdk.annotations`
- **作者**: sean
- **版本**: 1.7
- **创建日期**: 2023/5/22

## 注解元数据
- `@Documented`: 注解信息会被包含在 JavaDoc 中
- `@Retention(RetentionPolicy.RUNTIME)`: 注解在运行时保留，可以通过反射获取
- `@Target({ElementType.FIELD, ElementType.METHOD})`: 注解可以应用于字段和方法

## 注解属性

### 1. since() - 起始版本
- **类型**: `CloudSDKVersionEnum`
- **默认值**: `CloudSDKVersionEnum.V0_0_1`
- **作用**: 指定该功能从哪个版本开始支持
- **用途**: 标记功能的引入版本，用于向后兼容性检查

### 2. deprecated() - 废弃版本
- **类型**: `CloudSDKVersionEnum`
- **默认值**: `CloudSDKVersionEnum.V99`
- **作用**: 指定该功能从哪个版本开始被废弃
- **用途**: 标记功能的废弃版本，用于向前兼容性检查

### 3. include() - 包含的网关类型
- **类型**: `GatewayTypeEnum[]`
- **默认值**: `{}` (空数组)
- **作用**: 指定该功能支持哪些网关类型
- **用途**: 白名单机制，只在这些网关类型中启用功能

### 4. exclude() - 排除的网关类型
- **类型**: `GatewayTypeEnum[]`
- **默认值**: `{}` (空数组)
- **作用**: 指定该功能不支持哪些网关类型
- **用途**: 黑名单机制，在这些网关类型中禁用功能

## 使用场景

### 1. 版本兼容性管理
```java
@CloudSDKVersion(since = CloudSDKVersionEnum.V1_2_0)
public void newFeature() {
    // 新功能，从 1.2.0 版本开始支持
}
```

### 2. 功能废弃标记
```java
@CloudSDKVersion(deprecated = CloudSDKVersionEnum.V2_0_0)
public void oldFeature() {
    // 旧功能，在 2.0.0 版本被废弃
}
```

### 3. 网关类型限制
```java
@CloudSDKVersion(
    since = CloudSDKVersionEnum.V1_0_0,
    include = {GatewayTypeEnum.GATEWAY_A, GatewayTypeEnum.GATEWAY_B}
)
public void gatewaySpecificFeature() {
    // 只在特定网关类型中支持的功能
}
```

### 4. 排除特定网关
```java
@CloudSDKVersion(
    since = CloudSDKVersionEnum.V1_0_0,
    exclude = {GatewayTypeEnum.GATEWAY_C}
)
public void limitedFeature() {
    // 在除 GATEWAY_C 外的所有网关中支持
}
```

## 设计模式
- **装饰器模式**: 通过注解为类成员添加版本元数据
- **策略模式**: 通过 include/exclude 属性实现不同的网关支持策略

## 相关依赖
- `CloudSDKVersionEnum`: 版本枚举，定义具体的版本号
- `GatewayTypeEnum`: 网关类型枚举，定义支持的网关类型
- `CloudSDKHandler`: 版本兼容性检查的AOP切面处理器
  - **文件路径**: `E:\dji\dji-api\cloud-sdk\src\main\java\com\dji\sdk\config\CloudSDKHandler.java`
  - **作用**: 在方法调用前自动检查版本兼容性
  - **关键功能**: 
    - 拦截所有 Cloud API 方法调用
    - 检查 `@CloudSDKVersion` 注解的版本和网关类型支持
    - 不满足条件时抛出相应的异常

## 注意事项
1. **版本范围**: since 和 deprecated 属性定义了功能的生命周期
2. **网关过滤**: include 和 exclude 不能同时使用，应该选择其中一种策略
3. **默认行为**: 如果不指定 include/exclude，功能在所有网关类型中都可用
4. **运行时检查**: 注解信息在运行时保留，可以用于动态功能检查

## 扩展性
该注解设计具有良好的扩展性：
- 可以轻松添加新的版本号到 `CloudSDKVersionEnum`
- 可以添加新的网关类型到 `GatewayTypeEnum`
- 注解属性设计灵活，支持多种使用场景

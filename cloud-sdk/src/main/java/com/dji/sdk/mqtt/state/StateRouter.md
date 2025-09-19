# StateRouter 状态路由器

## 概述
StateRouter 是 DJI Cloud SDK 中负责处理设备状态数据的核心路由器组件。它使用 Spring Integration 框架实现 MQTT 消息的路由和处理，将设备上报的状态数据按照设备类型进行分发和处理。

## 核心功能

### 1. 状态数据路由 (stateDataRouterFlow)
**功能**: 处理设备上报的状态数据，按照数据类型进行路由分发

**处理流程**:
```java
@Bean
public IntegrationFlow stateDataRouterFlow() {
    return IntegrationFlows
        .from(ChannelName.INBOUND_STATE)  // 从入站状态通道接收数据
        .transform(Message.class, source -> {
            try {
                // 解析MQTT消息为TopicStateRequest对象
                TopicStateRequest response = Common.getObjectMapper().readValue(
                    (byte[]) source.getPayload(), 
                    new TypeReference<TopicStateRequest>() {}
                );
                
                // 从MQTT主题中提取设备序列号
                String topic = String.valueOf(source.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
                String from = topic.substring(
                    (THING_MODEL_PRE + PRODUCT).length(), 
                    topic.indexOf(STATE_SUF)
                );
                
                // 设置设备序列号并转换数据类型
                return response.setFrom(from)
                    .setData(Common.getObjectMapper().convertValue(
                        response.getData(), 
                        getTypeReference(response.getGateway(), response.getData())
                    ));
            } catch (IOException e) {
                throw new CloudSDKException(e);
            }
        }, null)
        // 根据数据类型进行路由
        .<TopicStateRequest, StateDataKeyEnum>route(
            response -> StateDataKeyEnum.find(response.getData().getClass()),
            mapping -> Arrays.stream(StateDataKeyEnum.values())
                .forEach(key -> mapping.channelMapping(key, key.getChannelName()))
        )
        .get();
}
```

**关键步骤**:
1. **消息接收**: 从 `INBOUND_STATE` 通道接收MQTT消息
2. **数据解析**: 将字节数组解析为 `TopicStateRequest` 对象
3. **设备识别**: 从MQTT主题中提取设备序列号
4. **类型转换**: 根据设备类型转换数据为对应的状态数据类型
5. **路由分发**: 根据数据类型路由到相应的处理通道

### 2. 状态响应处理 (replySuccessState)
**功能**: 处理状态数据的响应，发布处理结果

**处理流程**:
```java
@Bean
public IntegrationFlow replySuccessState() {
    return IntegrationFlows
        .from(ChannelName.OUTBOUND_STATE)  // 从出站状态通道接收数据
        .handle(this::publish)             // 发布响应
        .nullChannel();                    // 结束流程
}
```

**发布逻辑**:
```java
private TopicStateResponse publish(TopicStateResponse request, MessageHeaders headers) {
    if (Objects.isNull(request) || Objects.isNull(request.getData())) {
        return null;  // 空数据直接返回
    }
    gatewayPublish.publishReply(request, headers);  // 发布响应
    return request;
}
```

### 3. 类型引用获取 (getTypeReference)
**功能**: 根据设备类型和数据键值确定对应的数据类型

**类型判断逻辑**:
```java
private Class getTypeReference(String gatewaySn, Object data) {
    Set<String> keys = ((Map<String, Object>) data).keySet();
    switch (SDKManager.getDeviceSDK(gatewaySn).getType()) {
        case RC:
            return RcStateDataKeyEnum.find(keys).getClassType();  // RC设备状态类型
        case DOCK:
        case DOCK2:
            return DockStateDataKeyEnum.find(keys).getClassType(); // Dock设备状态类型
        default:
            throw new CloudSDKException(CloudSDKErrorEnum.WRONG_DATA, 
                "Unexpected value: " + SDKManager.getDeviceSDK(gatewaySn).getType());
    }
}
```

## 技术架构

### 1. Spring Integration 框架
- **IntegrationFlow**: 定义消息处理流程
- **Channel**: 消息通道，用于数据传输
- **Router**: 消息路由器，根据条件分发消息
- **Transformer**: 消息转换器，处理数据格式转换

### 2. MQTT 消息处理
- **Topic 解析**: 从MQTT主题中提取设备信息
- **Payload 解析**: 将字节数组转换为Java对象
- **Headers 处理**: 处理MQTT消息头信息

### 3. 设备类型支持
- **RC 设备**: 遥控器设备状态数据
- **DOCK 设备**: 机场设备状态数据
- **DOCK2 设备**: 机场2代设备状态数据

## 数据流向

### 1. 入站流程
```
MQTT消息 → INBOUND_STATE通道 → 数据解析 → 类型转换 → 路由分发 → 各处理通道
```

### 2. 出站流程
```
处理结果 → OUTBOUND_STATE通道 → 响应发布 → MQTT发布
```

## 关键组件

### 1. TopicStateRequest
- **from**: 设备序列号
- **gateway**: 网关序列号
- **data**: 状态数据内容

### 2. StateDataKeyEnum
- **RC状态类型**: RcStateDataKeyEnum
- **DOCK状态类型**: DockStateDataKeyEnum
- **通道映射**: 每种状态类型对应一个处理通道

### 3. ChannelName
- **INBOUND_STATE**: 入站状态通道
- **OUTBOUND_STATE**: 出站状态通道

## 异常处理

### 1. 数据解析异常
```java
try {
    // JSON解析
} catch (IOException e) {
    throw new CloudSDKException(e);
}
```

### 2. 设备类型异常
```java
default:
    throw new CloudSDKException(CloudSDKErrorEnum.WRONG_DATA, 
        "Unexpected value: " + SDKManager.getDeviceSDK(gatewaySn).getType());
```

### 3. 空数据检查
```java
if (Objects.isNull(request) || Objects.isNull(request.getData())) {
    return null;
}
```

## 设计特点

### 1. 类型安全
- 严格的类型检查和转换
- 基于设备类型的动态类型确定
- 泛型支持确保类型安全

### 2. 可扩展性
- 基于枚举的状态类型管理
- 支持新设备类型的添加
- 灵活的路由配置

### 3. 异步处理
- 基于Spring Integration的异步消息处理
- 非阻塞的消息路由
- 高并发支持

### 4. 错误处理
- 完善的异常处理机制
- 优雅的错误降级
- 详细的错误信息

## 使用场景

### 1. 设备状态监控
- 实时接收设备状态数据
- 按设备类型分类处理
- 状态变化通知

### 2. 数据路由分发
- 根据数据类型路由到不同处理器
- 支持多种设备类型
- 灵活的处理流程

### 3. 响应管理
- 处理状态数据的响应
- 发布处理结果
- 状态同步

## 设备类型异常详解

### 异常触发条件
设备类型异常在以下情况下会触发：

#### 1. 不支持的设备类型
当设备类型不是以下任何一种时：
- `RC` (遥控器)
- `DOCK` (机场)
- `DOCK2` (机场2代)

**不支持的设备类型示例**：
- `DRONE` (无人机) - 无人机设备本身不直接处理状态数据
- `PAYLOAD` (载荷设备)
- `UNKNOWN` (未知设备类型)
- 其他新增但未在switch中处理的设备类型

#### 2. 异常触发位置
```java
private Class getTypeReference(String gatewaySn, Object data) {
    Set<String> keys = ((Map<String, Object>) data).keySet();
    switch (SDKManager.getDeviceSDK(gatewaySn).getType()) {
        case RC:
            return RcStateDataKeyEnum.find(keys).getClassType();
        case DOCK:
        case DOCK2:
            return DockStateDataKeyEnum.find(keys).getClassType();
        default:
            // 这里会触发异常
            throw new CloudSDKException(CloudSDKErrorEnum.WRONG_DATA, 
                "Unexpected value: " + SDKManager.getDeviceSDK(gatewaySn).getType());
    }
}
```

#### 3. 异常信息格式
```java
CloudSDKException(CloudSDKErrorEnum.WRONG_DATA, 
    "Unexpected value: " + SDKManager.getDeviceSDK(gatewaySn).getType())
```

**异常信息示例**：
- `"Unexpected value: DRONE"`
- `"Unexpected value: PAYLOAD"`
- `"Unexpected value: null"`

### 触发时机

#### 1. 设备状态数据上报时
- 设备通过MQTT上报状态数据
- StateRouter尝试解析数据类型
- 发现设备类型不在支持列表中

#### 2. 状态数据路由过程中
- `stateDataRouterFlow` 调用 `getTypeReference`
- 根据设备类型确定数据类型
- 遇到不支持的设备类型

### 实际应用场景

#### 1. 新设备类型支持
当DJI发布新的设备类型时，如果StateRouter没有及时更新支持，就会触发此异常。

#### 2. 设备类型识别错误
- 设备注册时类型设置错误
- SDK版本不匹配
- 设备固件版本问题

#### 3. 开发测试环境
- 使用模拟设备进行测试
- 设备类型配置错误
- 测试数据格式不正确

### 解决方案

#### 1. 代码层面解决方案
```java
// 在switch语句中添加新的设备类型支持
case NEW_DEVICE_TYPE:
    return NewDeviceStateDataKeyEnum.find(keys).getClassType();
```

#### 2. 配置层面解决方案
- 确保设备正确注册到SDKManager
- 验证设备类型配置
- 检查SDK版本兼容性

#### 3. 异常处理方案
```java
try {
    Class typeClass = getTypeReference(gatewaySn, data);
    // 处理状态数据
} catch (CloudSDKException e) {
    log.error("不支持的设备类型: {}", e.getMessage());
    // 降级处理或跳过该数据
}
```

### 预防措施

#### 1. 设备注册验证
```java
// 在设备注册时验证类型
if (!isSupportedDeviceType(deviceType)) {
    throw new IllegalArgumentException("不支持的设备类型: " + deviceType);
}
```

#### 2. 类型检查
```java
// 在处理状态数据前检查设备类型
GatewayManager gateway = SDKManager.getDeviceSDK(gatewaySn);
if (!isStateDataSupported(gateway.getType())) {
    log.warn("设备类型 {} 不支持状态数据处理", gateway.getType());
    return;
}
```

#### 3. 版本兼容性检查
```java
// 检查SDK版本兼容性
if (!isVersionCompatible(gateway.getThingVersion())) {
    throw new CloudSDKException(CloudSDKErrorEnum.VERSION_NOT_SUPPORTED);
}
```

## 注意事项
- 确保设备已注册到SDKManager
- 正确处理MQTT主题格式
- 注意数据类型转换的准确性
- 处理网络异常和超时情况
- 确保状态数据的完整性验证
- **重要**: 新增设备类型时及时更新StateRouter支持
- **重要**: 处理设备类型异常，避免系统崩溃
- **重要**: 定期检查设备类型配置的正确性

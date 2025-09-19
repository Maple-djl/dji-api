# SDKManager 设备管理器

## 概述
SDKManager 是 DJI Cloud SDK 的核心管理类，负责管理所有设备的 SDK 实例。采用单例模式设计，确保全局只有一个管理器实例。

## 核心功能

### 1. 设备注册 (registerDevice)
- **作用**: 将设备注册到 SDK 系统中，创建对应的 GatewayManager 实例
- **支持多种注册方式**:
  - 通过设备域名、类型、子类型注册
  - 通过网关类型枚举注册  
  - 直接注册 GatewayManager 实例

### 2. 设备SDK获取 (getDeviceSDK)
- **作用**: 根据网关序列号获取对应的 GatewayManager 实例
- **异常处理**: 如果设备未注册会抛出 CloudSDKException

### 3. 设备注销 (logoutDevice)
- **作用**: 从 SDK 系统中移除指定设备

## 内部实现

### 存储结构
```java
private static final ConcurrentHashMap<String, GatewayManager> SDK_MAP = new ConcurrentHashMap<>(16);
```
- 使用 ConcurrentHashMap 保证线程安全
- Key: 网关序列号 (gatewaySn)
- Value: GatewayManager 实例

### 设计特点
- **单例模式**: 构造函数私有化
- **线程安全**: 使用 ConcurrentHashMap
- **异常处理**: 完善的错误处理机制
- **版本管理**: 支持网关和无人机的 Thing 版本

## 设备注册机制详解

### 注册流程
1. **设备信息收集**: 收集网关序列号、无人机序列号、设备类型等信息
2. **GatewayManager 创建**: 根据设备信息创建对应的 GatewayManager 实例
3. **存储到映射表**: 将实例存储到 SDK_MAP 中
4. **返回管理器**: 返回创建的 GatewayManager 实例供后续使用

### 注册参数说明
- `gatewaySn`: 网关序列号，作为唯一标识
- `droneSn`: 无人机序列号
- `domain`: 设备域名枚举
- `type`: 设备类型枚举
- `subType`: 设备子类型枚举
- `gatewayThingVersion`: 网关 Thing 版本
- `droneThingVersion`: 无人机 Thing 版本

### 使用示例
```java
// 注册设备
GatewayManager gateway = SDKManager.registerDevice(
    "gateway_sn_123", 
    "drone_sn_456", 
    DeviceDomainEnum.DRONE,
    DeviceTypeEnum.M30,
    DeviceSubTypeEnum.M30T,
    "1.0.0",
    "1.0.0"
);

// 获取设备SDK
GatewayManager sdk = SDKManager.getDeviceSDK("gateway_sn_123");

// 注销设备
SDKManager.logoutDevice("gateway_sn_123");
```

## 设备注册入口分析

### 1. 应用启动时自动注册 (ApplicationBootInitial)
**位置**: `com.dji.sample.component.ApplicationBootInitial`
**触发时机**: Spring Boot应用启动时自动执行
**注册逻辑**:
```java
@Override
public void run(String... args) throws Exception {
    // 从Redis获取所有在线设备
    RedisOpsUtils.getAllKeys(RedisConst.DEVICE_ONLINE_PREFIX + "*")
        .stream()
        .map(key -> key.substring(start))
        .map(deviceRedisService::getDeviceOnline)
        .map(Optional::get)
        .filter(device -> DeviceDomainEnum.DRONE != device.getDomain()) // 排除无人机设备
        .forEach(device -> deviceService.subDeviceOnlineSubscribeTopic(
            SDKManager.registerDevice(device.getDeviceSn(), device.getChildDeviceSn(), 
                device.getDomain(), device.getType(), device.getSubType(), 
                device.getThingVersion(), 
                deviceRedisService.getDeviceOnline(device.getChildDeviceSn())
                    .map(DeviceDTO::getThingVersion).orElse(null))
        ));
}
```

### 2. 设备上线时动态注册 (SDKDeviceService)
**位置**: `com.dji.sample.manage.service.impl.SDKDeviceService`
**触发时机**: 设备上线时通过MQTT消息触发

#### 2.1 设备上线注册 (updateTopoOnline)
```java
@Override
public TopicStatusResponse<MqttReply> updateTopoOnline(TopicStatusRequest<UpdateTopo> request, MessageHeaders headers) {
    UpdateTopoSubDevice updateTopoSubDevice = request.getData().getSubDevices().get(0);
    String deviceSn = updateTopoSubDevice.getSn();
    
    // 注册设备到SDKManager
    GatewayManager gatewayManager = SDKManager.registerDevice(
        request.getFrom(), deviceSn,
        request.getData().getDomain(), 
        request.getData().getType(),
        request.getData().getSubType(), 
        request.getData().getThingVersion(), 
        updateTopoSubDevice.getThingVersion()
    );
    
    // 订阅相关主题
    deviceService.gatewayOnlineSubscribeTopic(gatewayManager);
    deviceService.subDeviceOnlineSubscribeTopic(gatewayManager);
}
```

#### 2.2 设备下线注册 (updateTopoOffline)
```java
@Override
public TopicStatusResponse<MqttReply> updateTopoOffline(TopicStatusRequest<UpdateTopo> request, MessageHeaders headers) {
    // 注册网关设备（子设备为空）
    GatewayManager gatewayManager = SDKManager.registerDevice(
        request.getFrom(), null,
        request.getData().getDomain(), 
        request.getData().getType(),
        request.getData().getSubType(), 
        request.getData().getThingVersion(), 
        null
    );
    deviceService.gatewayOnlineSubscribeTopic(gatewayManager);
}
```

### 3. DRC模式进入时注册 (DrcRcServiceImpl)
**位置**: `com.dji.sample.control.service.impl.DrcRcServiceImpl`
**触发时机**: 进入DRC模式时检查设备注册状态

#### 3.1 Pilot设备DRC模式检查
```java
private void checkDrcModeConditionForPilot(String workspaceId, String pilotSn) {
    // 检查设备是否已注册
    try {
        SDKManager.getDeviceSDK(pilotSn);
    } catch (Exception e) {
        // 设备未注册，尝试从Redis获取设备信息并注册
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(pilotSn);
        if (deviceOpt.isPresent()) {
            DeviceDTO device = deviceOpt.get();
            SDKManager.registerDevice(
                device.getDeviceSn(), 
                device.getChildDeviceSn(), 
                device.getDomain(), 
                device.getType(), 
                device.getSubType(), 
                device.getThingVersion(), 
                deviceRedisService.getDeviceOnline(device.getChildDeviceSn())
                    .map(DeviceDTO::getThingVersion).orElse(null)
            );
        }
    }
}
```

## 注册流程总结

### 注册时机
1. **应用启动时**: 自动注册Redis中所有在线设备
2. **设备上线时**: 通过MQTT消息动态注册新上线设备
3. **DRC模式进入时**: 检查并确保设备已注册

### 注册数据来源
- **设备信息**: 从MQTT消息的`UpdateTopo`数据中获取
- **Redis缓存**: 从Redis中获取设备在线状态和基本信息
- **数据库**: 从数据库中获取设备详细配置信息

### 注册后的操作
1. **主题订阅**: 订阅设备相关的MQTT主题
2. **状态更新**: 更新设备在线状态
3. **权限管理**: 设置设备访问权限
4. **数据推送**: 向Web端推送设备状态变化

## 注意事项
- 设备必须先注册才能使用
- 网关序列号必须唯一
- 注册失败会抛出相应异常
- 注销设备会清理相关资源
- 应用重启时会自动恢复已注册设备

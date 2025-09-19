# RC设备模式支持修复

## 问题描述

用户报告了两个主要问题：

1. **设备注册错误**: `Error Code: 210001, Error Msg: Device is not registered.. The device has not been registered, please call the 'SDKManager.registerDevice()' method to register the device first.`

2. **设备模式获取问题**: `deviceService.getDeviceMode(pilotSn)` 方法需要支持RC设备，当前只支持Dock设备。

3. **无人机OSD报文**: 用户提供了无人机OSD报文示例，显示 `mode_code: 0` 对应 `DroneModeCodeEnum.IDLE` 状态。

## 解决方案

### 1. 修改 `getDeviceMode` 方法支持RC设备

**文件**: `E:\dji\dji-api\sample\src\main\java\com\dji\sample\manage\service\impl\DeviceServiceImpl.java`

**修改内容**:
```java
@Override
public DroneModeCodeEnum getDeviceMode(String deviceSn) {
    // 首先尝试获取 Dock 设备的 OSD 数据
    Optional<OsdDockDrone> dockOsdOpt = deviceRedisService.getDeviceOsd(deviceSn, OsdDockDrone.class);
    if (dockOsdOpt.isPresent()) {
        return dockOsdOpt.get().getModeCode();
    }
    
    // 如果 Dock OSD 不存在，尝试获取 RC 设备的 OSD 数据
    Optional<OsdRcDrone> rcOsdOpt = deviceRedisService.getDeviceOsd(deviceSn, OsdRcDrone.class);
    if (rcOsdOpt.isPresent()) {
        return rcOsdOpt.get().getModeCode();
    }
    
    // 如果都没有找到，返回断开连接状态
    return DroneModeCodeEnum.DISCONNECTED;
}
```

**说明**: 该方法现在支持两种设备类型：
- Dock设备：使用 `OsdDockDrone` 类
- RC设备：使用 `OsdRcDrone` 类

两种OSD类都使用相同的 `DroneModeCodeEnum`，因此不需要创建新的枚举。

### 2. 添加设备注册检查

**文件**: `E:\dji\dji-api\sample\src\main\java\com\dji\sample\control\service\impl\DrcRcServiceImpl.java`

**修改内容**: 在 `checkDrcModeConditionForPilot` 方法中添加设备注册检查：

```java
// 确保设备已注册到SDKManager
try {
    SDKManager.getDeviceSDK(pilotSn);
    log.info("设备已注册到SDKManager: pilotSn={}", pilotSn);
} catch (Exception e) {
    log.warn("设备未注册到SDKManager，尝试注册: pilotSn={}, error={}", pilotSn, e.getMessage());
    
    // 尝试从Redis获取设备信息并注册
    Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(pilotSn);
    if (deviceOpt.isPresent()) {
        DeviceDTO device = deviceOpt.get();
        try {
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
            log.info("设备注册成功: pilotSn={}", pilotSn);
        } catch (Exception regException) {
            log.error("设备注册失败: pilotSn={}, error={}", pilotSn, regException.getMessage(), regException);
            throw new RuntimeException("设备未注册且注册失败: " + regException.getMessage());
        }
    } else {
        log.error("设备不在线，无法注册: pilotSn={}", pilotSn);
        throw new RuntimeException("设备不在线，无法进入DRC模式");
    }
}
```

**说明**: 
- 首先尝试获取已注册的设备
- 如果设备未注册，从Redis获取设备信息并自动注册
- 如果设备不在线，抛出异常

### 3. 添加必要的导入

**文件**: `E:\dji\dji-api\sample\src\main\java\com\dji\sample\control\service\impl\DrcRcServiceImpl.java`

**添加的导入**:
```java
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.config.version.GatewayManager;
```

## 无人机OSD报文分析

从用户提供的OSD报文可以看到：

```json
{
  "data": {
    "mode_code": 0,
    "elevation": 0,
    "height": 101.43196868896484,
    "latitude": 0,
    "longitude": 0,
    // ... 其他字段
  }
}
```

- `mode_code: 0` 对应 `DroneModeCodeEnum.IDLE`
- 这个状态是支持DRC模式的（在 `isPilotModeSupportedForDrc` 方法中已包含）

## 验证逻辑

当前的DRC模式验证逻辑支持以下模式：
- `IDLE` (0) - 空闲状态
- `TAKEOFF_FINISHED` (2) - 起飞完成
- `MANUAL` (3) - 手动模式
- `TAKEOFF_AUTO` (4) - 自动起飞
- `WAYLINE` (5) - 航线模式
- `PANORAMIC_SHOT` (6) - 全景拍摄
- `ACTIVE_TRACK` (7) - 主动跟踪
- `APAS` (15) - APAS模式
- `VIRTUAL_JOYSTICK` (16) - 虚拟摇杆
- `LIVE_FLIGHT_CONTROLS` (17) - 实时飞行控制
- `POI` (20) - 兴趣点模式

## 测试建议

1. **测试设备注册**: 确保RC设备能够正确注册到SDKManager
2. **测试模式获取**: 验证 `getDeviceMode` 方法能够正确返回RC设备的模式
3. **测试DRC模式**: 验证RC设备在 `IDLE` 状态下能够成功进入DRC模式
4. **测试错误处理**: 验证设备未注册时的错误处理逻辑

## 注意事项

1. **RC设备特性**: RC设备是遥控器，不是飞行器，因此不需要严格的OSD数据校验（如高度校验）
2. **设备类型兼容**: 修改后的代码同时支持Dock和RC设备，不会影响现有功能
3. **错误处理**: 添加了详细的日志记录，便于问题排查
4. **自动注册**: 如果设备未注册，系统会尝试自动注册，提高用户体验

## 相关文件

- `DeviceServiceImpl.java` - 设备服务实现，包含 `getDeviceMode` 方法
- `DrcRcServiceImpl.java` - DRC服务实现，包含设备注册检查逻辑
- `OsdRcDrone.java` - RC设备OSD数据类
- `DroneModeCodeEnum.java` - 无人机模式枚举

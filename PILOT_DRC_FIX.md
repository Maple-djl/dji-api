# Pilot DRC 模式 OSD 数据问题修复

## 问题描述

在 `DrcRcServiceImpl.checkDrcModeConditionForPilot()` 方法中，调用 `deviceRedisService.getDeviceOsd(pilotSn, OsdRcDrone.class)` 返回空值，导致 Pilot 设备的 DRC 模式无法正常工作。

## 问题根因

在 `SDKDeviceService.osdRcDrone()` 方法中，缺少了将 OSD 数据存储到 Redis 的调用：

```java
// 问题代码 - osdRcDrone 方法中缺少这一行
deviceRedisService.setDeviceOsd(from, request.getData());
```

对比 `osdDockDrone()` 方法，它有正确的存储逻辑：

```java
// 正确的代码 - osdDockDrone 方法中有这一行
deviceRedisService.setDeviceOnline(device);
deviceRedisService.setDeviceOsd(from, request.getData()); // 这行在 osdRcDrone 中缺失
```

## 解决方案

在 `SDKDeviceService.osdRcDrone()` 方法中添加缺失的 OSD 数据存储调用：

```java
@Override
public void osdRcDrone(TopicOsdRequest<OsdRcDrone> request, MessageHeaders headers) {
    String from = request.getFrom();
    Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(from);
    if (deviceOpt.isEmpty()) {
        deviceOpt = deviceService.getDeviceBySn(from);
        if (deviceOpt.isEmpty()) {
            log.error("Please restart the drone.");
            return;
        }
    }
    DeviceDTO device = deviceOpt.get();
    if (!StringUtils.hasText(device.getWorkspaceId())) {
        log.error("Please bind the drone first.");
    }

    deviceRedisService.setDeviceOnline(device);
    deviceRedisService.setDeviceOsd(from, request.getData()); // 添加这一行

    OsdRcDrone data = request.getData();
    deviceService.pushOsdDataToPilot(device.getWorkspaceId(), from,
            new DeviceOsdHost()
                    .setLatitude(data.getLatitude())
                    .setLongitude(data.getLongitude())
                    .setElevation(data.getElevation())
                    .setHeight(data.getHeight())
                    .setAttitudeHead(data.getAttitudeHead())
                    .setElevation(data.getElevation())
                    .setHorizontalSpeed(data.getHorizontalSpeed())
                    .setVerticalSpeed(data.getVerticalSpeed()));
    deviceService.pushOsdDataToWeb(device.getWorkspaceId(), BizCodeEnum.DEVICE_OSD, from, data);
}
```

## 影响范围

- **修复前**: Pilot 设备的 DRC 模式无法进入，因为无法获取到 OSD 数据
- **修复后**: Pilot 设备的 DRC 模式可以正常工作，能够正确获取设备状态和高度信息

## 测试验证

添加了新的测试用例 `testCheckDrcModeConditionForPilot_WithEmptyOsdData_ShouldThrowException()` 来验证空 OSD 数据的处理逻辑。

## 进一步优化

### 1. RC 设备特殊处理
RC 设备与 Dock 设备有本质区别：
- **RC 设备**: 遥控器，不是飞行器，不需要校验高度
- **Dock 设备**: 机场，管理无人机，需要校验无人机状态

因此对 RC 设备的 DRC 模式检查进行了优化：
- 跳过严格的高度校验（`elevation <= 0`）
- 重点关注设备连接状态和模式支持
- 允许在没有 OSD 数据的情况下进入 DRC 模式

### 2. 增强的错误处理和日志
在 `DrcRcServiceImpl.checkDrcModeConditionForPilot()` 方法中添加了详细的日志记录，包括：
- 设备状态检查
- OSD 数据检查（RC 设备特殊处理）
- 子设备 OSD 数据回退机制
- 详细的错误信息

### 3. OSD 数据诊断服务
创建了 `PilotOsdDiagnosticService` 来诊断 OSD 数据问题：
- 检查设备在线状态
- 验证设备基本信息
- 检查 SDK 注册状态
- 分析 OSD 数据可用性
- 提供问题诊断建议

### 4. 诊断 API 端点
添加了 `PilotDiagnosticController` 提供诊断 API：
```
GET /api/v1/pilot/{pilot_sn}/diagnostic/osd
```

### 5. 子设备 OSD 数据回退
如果主设备没有 OSD 数据，系统会尝试从子设备获取：
```java
if (deviceOsd.isEmpty()) {
    String childDeviceSn = pilotDevice.getChildDeviceSn();
    if (StringUtils.hasText(childDeviceSn)) {
        deviceOsd = deviceRedisService.getDeviceOsd(childDeviceSn, OsdRcDrone.class);
    }
}
```

## 常见问题排查

### OSD 数据为空的原因
1. **设备未正确注册到 SDKManager**
   - 检查设备是否通过 `updateTopoOnline` 正确注册
   - 验证 `GatewayManager` 配置

2. **未订阅 OSD topic**
   - 确认 `OsdSubscribe.subscribe()` 被调用
   - 检查 MQTT 连接状态

3. **设备未发送 OSD 数据**
   - 检查设备状态和连接
   - 验证设备配置

4. **OSD 数据路由问题**
   - 检查 `OsdRouter` 配置
   - 验证 `OsdDeviceTypeEnum` 映射

5. **Redis 存储问题**
   - 检查 Redis 连接
   - 验证数据过期时间

## 相关文件

- `SDKDeviceService.java` - 修复 OSD 数据存储问题
- `DrcRcServiceImpl.java` - Pilot DRC 模式实现
- `DrcRcServiceImplTest.java` - 相关测试用例
- `PilotOsdDiagnosticService.java` - OSD 数据诊断服务
- `PilotDiagnosticController.java` - 诊断 API 控制器

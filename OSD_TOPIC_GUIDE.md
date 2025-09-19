# OSD 数据 MQTT 主题说明

## OSD 主题格式

OSD 数据通过以下 MQTT 主题格式上传：

```
thing/product/{device_sn}/osd
```

### 主题组成

- **前缀**: `thing/` - 物模型前缀
- **产品标识**: `product/` - 产品标识符
- **设备SN**: `{device_sn}` - 具体设备的序列号
- **后缀**: `/osd` - OSD 数据后缀

### 具体示例

对于不同的设备类型，OSD 主题如下：

#### RC/Pilot 设备
```
thing/product/RC123456789/osd
```

#### Dock 设备
```
thing/product/DOCK123456789/osd
```

#### 子设备（无人机）
```
thing/product/DRONE123456789/osd
```

## OSD 数据路由流程

### 1. MQTT 消息接收
- 系统通过 `MqttConfiguration.mqttInbound()` 订阅 OSD 主题
- 消息通过 `InboundMessageRouter` 路由到 `ChannelName.INBOUND_OSD`

### 2. OSD 数据解析
- `OsdRouter.osdRouterFlow()` 处理 OSD 消息
- 根据设备类型和网关类型确定 OSD 数据类型：
  - `RC` → `OsdRemoteControl`
  - `RC_DRONE` → `OsdRcDrone`
  - `DOCK` → `OsdDock`
  - `DOCK_DRONE` → `OsdDockDrone`

### 3. 数据存储
- 解析后的 OSD 数据存储到 Redis
- 键格式：`osd:{device_sn}`
- 过期时间：设备存活时间

## 设备类型判断

OSD 路由根据以下条件判断设备类型：

```java
OsdDeviceTypeEnum typeEnum = OsdDeviceTypeEnum.find(gateway.getType(), response.getFrom().equals(response.getGateway()));
```

### 判断逻辑
- **网关设备** (`isGateway = true`): 设备SN 等于网关SN
- **子设备** (`isGateway = false`): 设备SN 不等于网关SN

### 设备类型映射
| 网关类型 | 是否为网关 | OSD 类型 | 处理类 |
|---------|-----------|---------|--------|
| RC | true | RC | OsdRemoteControl |
| RC | false | RC_DRONE | OsdRcDrone |
| DOCK | true | DOCK | OsdDock |
| DOCK | false | DOCK_DRONE | OsdDockDrone |

## 订阅管理

### 自动订阅
设备上线时，系统会自动订阅相关 OSD 主题：

```java
// OsdSubscribe.subscribe()
topicService.subscribe(String.format(TOPIC, gateway.getGatewaySn())); // 网关设备
if (null != gateway.getDroneSn()) {
    topicService.subscribe(String.format(TOPIC, gateway.getDroneSn())); // 子设备
}
```

### 订阅示例
对于 RC 设备 `RC123456789` 和其子设备 `DRONE123456789`：

```java
// 订阅网关设备 OSD
topicService.subscribe("thing/product/RC123456789/osd");

// 订阅子设备 OSD
topicService.subscribe("thing/product/DRONE123456789/osd");
```

## OSD 数据结构

### OsdRcDrone 结构
```java
public class OsdRcDrone {
    private Double latitude;        // 纬度
    private Double longitude;       // 经度
    private Double elevation;       // 海拔高度
    private Double height;          // 相对高度
    private Double attitudeHead;    // 航向角
    private Double horizontalSpeed; // 水平速度
    private Double verticalSpeed;   // 垂直速度
    // ... 其他字段
}
```

## 常见问题

### 1. OSD 数据为空
**可能原因**：
- 设备未正确注册到 `SDKManager`
- 未订阅 OSD 主题
- 设备未发送 OSD 数据
- OSD 数据路由配置错误

**排查步骤**：
1. 检查设备是否在线：`deviceRedisService.checkDeviceOnline(deviceSn)`
2. 检查设备注册：`SDKManager.getDeviceSDK(deviceSn)`
3. 检查 OSD 数据：`deviceRedisService.getDeviceOsd(deviceSn, OsdRcDrone.class)`
4. 使用诊断 API：`GET /api/v1/pilot/{pilot_sn}/diagnostic/osd`

### 2. 主题订阅失败
**检查项**：
- MQTT 连接状态
- 主题格式是否正确
- 设备权限配置
- 网络连接状态

### 3. 数据路由错误
**检查项**：
- 设备类型配置
- 网关类型映射
- OSD 数据类型匹配

## 调试工具

### 1. 诊断服务
```java
@Autowired
private PilotOsdDiagnosticService diagnosticService;

// 诊断 OSD 数据问题
String report = diagnosticService.diagnosePilotOsd(deviceSn);
```

### 2. 诊断 API
```bash
# 诊断 Pilot 设备 OSD 数据
curl -X GET "http://localhost:8080/api/v1/pilot/RC123456789/diagnostic/osd"
```

### 3. 日志监控
启用 DEBUG 日志查看 MQTT 消息：
```yaml
logging:
  level:
    com.dji.sdk.mqtt: DEBUG
```

## 相关配置

### MQTT 配置
```yaml
cloud-sdk:
  mqtt:
    inbound-topic: "thing/product/+/osd"  # 通配符订阅所有设备 OSD
```

### Redis 配置
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
```

## 总结

OSD 数据通过 `thing/product/{device_sn}/osd` 主题上传，系统会自动根据设备类型路由到相应的处理类，并存储到 Redis 中。如果遇到 OSD 数据为空的问题，可以使用提供的诊断工具来快速定位问题。

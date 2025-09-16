# DJI Cloud SDK 使用指南笔记

## 📋 目录
- [项目概述](#项目概述)
- [接入步骤](#接入步骤)
- [MQTT连接配置](#mqtt连接配置)
- [SDK方法实现](#sdk方法实现)
- [SDK方法调用](#sdk方法调用)
- [HTTP接口实现](#http接口实现)
- [WebSocket接入](#websocket接入)
- [API文档查看](#api文档查看)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

## 📖 项目概述

DJI Cloud SDK 是大疆创新提供的云端集成解决方案，主要解决开发者重复造轮子的问题。对于不需要深度定制APP的开发者，可以直接使用 DJI Pilot2 与第三方云平台通信，开发者可以专注于云服务接口的开发和实现。

**⚠️ 重要提醒**：该项目已于2025年4月10日终止维护，仅作为参考实现，不建议在生产环境中直接使用。

### 技术架构
- **后端**：Spring Boot 2.7.12 + Java 11
- **数据库**：MySQL 8.0 + Redis
- **消息队列**：MQTT (Eclipse Mosquitto)
- **对象存储**：支持阿里云OSS、AWS S3、MinIO
- **前端**：Vue 3 + TypeScript + Vite

## 🚀 接入步骤

### 1. 组件扫描配置
在Spring Boot应用中，需要在组件扫描中增加包名：
```java
@ComponentScan(basePackages = {"com.dji.sdk", "your.package.name"})
```

### 2. MQTT连接配置
### 3. 实现SDK的方法
### 4. 调用SDK的方法

## 🔌 MQTT连接配置

### 配置步骤
1. **注入MQTT配置类**
   在Spring容器中注入`MqttConnectOptions`和`MqttPahoClientFactory`：
   ```java
   @Configuration
   public class MqttConfig {
       @Bean
       public MqttConnectOptions mqttConnectOptions() {
           MqttConnectOptions options = new MqttConnectOptions();
           options.setServerURIs(new String[]{"tcp://localhost:1883"});
           options.setUserName("your_username");
           options.setPassword("your_password".toCharArray());
           return options;
       }
       
       @Bean
       public MqttPahoClientFactory mqttPahoClientFactory() {
           DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
           factory.setConnectionOptions(mqttConnectOptions());
           return factory;
       }
   }
   ```

2. **配置文件设置**
   在`application.yml`中配置：
   ```yaml
   cloud-sdk:
     mqtt:
       inbound-topic: "your/topic/pattern"
   ```
   **注意**：未配置则不进行初始化订阅。

## 🛠️ SDK方法实现

### 实现步骤
1. **定义服务类**：继承`com.dji.sdk.cloudapi.*.api`包中的抽象类
2. **重写方法**：实现具体的业务功能
3. **Spring管理**：将定义的类放入Spring容器中

### 设备上线示例
```java
@Service
public class SDKDeviceService extends AbstractDeviceService {
    
    @Override
    public void updateTopoOnline(String deviceSn, String workspaceId) {
        // 实现设备上线逻辑
        log.info("设备上线: {}, 工作空间: {}", deviceSn, workspaceId);
        
        // 更新设备状态到数据库
        deviceRepository.updateDeviceStatus(deviceSn, DeviceStatus.ONLINE);
        
        // 发送设备上线通知
        notificationService.sendDeviceOnlineNotification(deviceSn);
    }
}
```

### 其他常用实现示例
```java
// 设备下线处理
@Override
public void updateTopoOffline(String deviceSn, String workspaceId) {
    log.info("设备下线: {}, 工作空间: {}", deviceSn, workspaceId);
    deviceRepository.updateDeviceStatus(deviceSn, DeviceStatus.OFFLINE);
}

// 设备状态更新
@Override
public void updateDeviceStatus(String deviceSn, DeviceStatus status) {
    log.info("设备状态更新: {}, 状态: {}", deviceSn, status);
    deviceRepository.updateDeviceStatus(deviceSn, status);
}
```

## 📞 SDK方法调用

### 调用步骤
1. **定义服务类**：继承相应的抽象类
2. **注入服务**：在需要调用的类中注入该服务
3. **调用方法**：调用具体的业务方法

### 航线预下发命令示例
```java
@Service
public class SDKWaylineService extends AbstractWaylineService {
    
    @Override
    public void waylineJobCreate(String jobId, String workspaceId, WaylineJobCreateRequest request) {
        // 实现航线任务创建逻辑
        log.info("创建航线任务: {}, 工作空间: {}", jobId, workspaceId);
        
        // 验证航线文件
        validateWaylineFile(request.getWaylineId());
        
        // 创建任务记录
        WaylineJob job = new WaylineJob();
        job.setJobId(jobId);
        job.setWorkspaceId(workspaceId);
        job.setWaylineId(request.getWaylineId());
        job.setStatus(JobStatus.PENDING);
        
        waylineJobRepository.save(job);
    }
}
```

### 在业务类中调用
```java
@Service
public class WaylineJobServiceImpl implements WaylineJobService {
    
    @Autowired
    private SDKWaylineService sdkWaylineService;
    
    public void createWaylineJob(String jobId, String workspaceId, WaylineJobCreateRequest request) {
        // 调用SDK方法
        sdkWaylineService.waylineJobCreate(jobId, workspaceId, request);
        
        // 其他业务逻辑
        scheduleJobExecution(jobId);
    }
}
```

## 🌐 HTTP接口实现

### 实现步骤
1. **定义接口实现类**：实现`com.dji.sdk.cloudapi.*.api`包中的HTTP接口类
2. **重写方法**：实现具体的接口逻辑，无需定义请求地址和方法等数据

### 示例实现
```java
@RestController
public class DeviceController implements DeviceApi {
    
    @Override
    public ResponseEntity<DeviceListResponse> getDevices(String workspaceId, Integer page, Integer pageSize) {
        // 实现获取设备列表逻辑
        List<Device> devices = deviceService.getDevicesByWorkspace(workspaceId, page, pageSize);
        
        DeviceListResponse response = new DeviceListResponse();
        response.setDevices(devices);
        response.setTotal(deviceService.getDeviceCount(workspaceId));
        
        return ResponseEntity.ok(response);
    }
    
    @Override
    public ResponseEntity<DeviceDetailResponse> getDeviceDetail(String workspaceId, String deviceSn) {
        // 实现获取设备详情逻辑
        Device device = deviceService.getDeviceBySn(deviceSn);
        
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        
        DeviceDetailResponse response = new DeviceDetailResponse();
        response.setDevice(device);
        
        return ResponseEntity.ok(response);
    }
}
```

## 🔌 WebSocket接入

### 默认配置
- CloudSDK 已经定义了WebSocket服务
- 默认地址：`http://localhost:6789/api/v1/ws`
- 没有实现WebSocket管理功能

### 自定义接入
参考实现：`com.dji.sample.component.websocket.config`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CustomWebSocketHandler(), "/api/v1/ws")
                .setAllowedOrigins("*");
    }
}

@Component
public class CustomWebSocketHandler extends TextWebSocketHandler {
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接建立: {}", session.getId());
        // 连接建立后的处理逻辑
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("收到WebSocket消息: {}", message.getPayload());
        // 处理接收到的消息
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接关闭: {}", session.getId());
        // 连接关闭后的清理逻辑
    }
}
```

## 📚 API文档查看

### Swagger UI访问
1. **启动程序**
2. **浏览器访问**：`http://localhost:6789/swagger-ui/index.html`

### API文档功能
- 查看所有HTTP接口定义
- 在线测试API接口
- 查看请求参数和响应格式
- 下载API文档

## 💡 最佳实践

### 1. 错误处理
```java
@Service
public class DeviceServiceImpl extends AbstractDeviceService {
    
    @Override
    public void updateTopoOnline(String deviceSn, String workspaceId) {
        try {
            // 业务逻辑
            processDeviceOnline(deviceSn, workspaceId);
        } catch (Exception e) {
            log.error("设备上线处理失败: {}", deviceSn, e);
            // 错误处理逻辑
            handleDeviceOnlineError(deviceSn, e);
        }
    }
}
```

### 2. 日志记录
```java
@Slf4j
@Service
public class WaylineServiceImpl extends AbstractWaylineService {
    
    @Override
    public void waylineJobCreate(String jobId, String workspaceId, WaylineJobCreateRequest request) {
        log.info("开始创建航线任务: jobId={}, workspaceId={}, waylineId={}", 
                jobId, workspaceId, request.getWaylineId());
        
        // 业务逻辑
        
        log.info("航线任务创建完成: jobId={}", jobId);
    }
}
```

### 3. 参数验证
```java
@Override
public void waylineJobCreate(String jobId, String workspaceId, WaylineJobCreateRequest request) {
    // 参数验证
    Assert.hasText(jobId, "任务ID不能为空");
    Assert.hasText(workspaceId, "工作空间ID不能为空");
    Assert.notNull(request, "请求参数不能为空");
    Assert.hasText(request.getWaylineId(), "航线ID不能为空");
    
    // 业务逻辑
}
```

### 4. 事务管理
```java
@Override
@Transactional
public void waylineJobCreate(String jobId, String workspaceId, WaylineJobCreateRequest request) {
    // 创建任务记录
    WaylineJob job = createWaylineJob(jobId, workspaceId, request);
    waylineJobRepository.save(job);
    
    // 更新航线文件状态
    waylineFileRepository.updateStatus(request.getWaylineId(), WaylineFileStatus.IN_USE);
    
    // 发送任务创建通知
    notificationService.sendJobCreatedNotification(job);
}
```

## ❓ 常见问题

### Q1: MQTT连接失败
**问题**：设备无法连接到MQTT服务器
**解决方案**：
1. 检查MQTT服务器是否启动
2. 验证连接参数（服务器地址、端口、用户名、密码）
3. 检查网络连接和防火墙设置
4. 查看MQTT服务器日志

### Q2: SDK方法未被调用
**问题**：实现了SDK方法但没有被调用
**解决方案**：
1. 确认类已正确继承抽象类
2. 检查类是否被Spring容器管理（添加@Service注解）
3. 验证组件扫描配置
4. 查看应用启动日志

### Q3: HTTP接口返回404
**问题**：调用HTTP接口返回404错误
**解决方案**：
1. 确认接口实现类正确实现了API接口
2. 检查请求路径是否正确
3. 验证HTTP方法（GET、POST、PUT、DELETE）
4. 查看Swagger UI确认接口定义

### Q4: WebSocket连接失败
**问题**：WebSocket连接建立失败
**解决方案**：
1. 检查WebSocket配置是否正确
2. 验证允许的源地址设置
3. 检查网络连接和代理设置
4. 查看浏览器控制台错误信息

### Q5: 数据库连接问题
**问题**：数据库操作失败
**解决方案**：
1. 检查数据库连接配置
2. 验证数据库服务是否启动
3. 检查数据库用户权限
4. 查看数据库连接池配置

## 📝 总结

DJI Cloud SDK 提供了完整的云端集成解决方案，通过标准化的API接口和MQTT通信协议，实现了云端平台与DJI设备的无缝集成。开发者可以专注于业务逻辑的实现，而无需关心底层的通信细节。

**关键要点**：
1. 正确配置组件扫描和MQTT连接
2. 继承相应的抽象类并实现业务逻辑
3. 使用Spring容器管理服务生命周期
4. 遵循最佳实践进行错误处理和日志记录
5. 利用Swagger UI进行API测试和文档查看

通过本指南，开发者可以快速上手DJI Cloud SDK的开发工作，构建稳定可靠的无人机云端应用。

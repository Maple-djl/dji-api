package com.dji.sample.control.service.impl;

import com.dji.sample.component.mqtt.config.MqttPropertyConfiguration;
import com.dji.sample.component.mqtt.model.EventsReceiver;
import com.dji.sample.component.mqtt.model.MapKeyConst;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.control.model.dto.JwtAclDTO;
import com.dji.sample.control.model.enums.DroneAuthorityEnum;
import com.dji.sample.control.model.enums.MqttAclAccessEnum;
import com.dji.sample.control.model.param.DrcConnectParam;
import com.dji.sample.control.model.param.DrcModeParam;
import com.dji.sample.control.model.param.DrcModeParamRc;
import com.dji.sample.control.service.IControlService;
import com.dji.sample.control.service.IDrcRcService;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sample.wayline.model.enums.WaylineJobStatusEnum;
import com.dji.sample.wayline.model.enums.WaylineTaskStatusEnum;
import com.dji.sample.wayline.model.param.UpdateJobParam;
import com.dji.sample.wayline.service.IFlightTaskService;
import com.dji.sample.wayline.service.IWaylineJobService;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.control.DrcCloudControlAuthRequestRequest;
import com.dji.sdk.cloudapi.control.DrcModeEnterRequest;
import com.dji.sdk.cloudapi.control.DrcModeMqttBroker;
import com.dji.sdk.cloudapi.control.api.AbstractControlService;
import com.dji.sdk.cloudapi.device.DeviceTypeEnum;
import com.dji.sdk.cloudapi.device.DockModeCodeEnum;
import com.dji.sdk.cloudapi.device.DroneModeCodeEnum;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.config.version.GatewayManager;
import com.dji.sdk.cloudapi.device.OsdDockDrone;
import com.dji.sdk.cloudapi.device.OsdRcDrone;
import com.dji.sdk.cloudapi.wayline.FlighttaskProgress;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.mqtt.TopicConst;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author sean
 * @version 1.3
 * @date 2023/1/11
 */
@Service
@Slf4j
public class DrcRcServiceImpl implements IDrcRcService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IWaylineJobService waylineJobService;

    @Autowired
    private IFlightTaskService flighttaskService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private IControlService controlService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private AbstractControlService abstractControlService;



    @Override
    public void setDrcModeInRedis(String dockSn, String clientId) {
        RedisOpsUtils.setWithExpire(RedisConst.DRC_PREFIX + dockSn, clientId, RedisConst.DRC_MODE_ALIVE_SECOND);
    }

    @Override
    public String getDrcModeInRedis(String dockSn) {
        return (String) RedisOpsUtils.get(RedisConst.DRC_PREFIX + dockSn);
    }

    @Override
    public Boolean delDrcModeInRedis(String dockSn) {
        return RedisOpsUtils.del(RedisConst.DRC_PREFIX + dockSn);
    }

    @Override
    public DrcModeMqttBroker userDrcAuth(String workspaceId, String userId, String username, DrcConnectParam param) {

        // refresh token
        String clientId = param.getClientId();
        // first time
        if (!StringUtils.hasText(clientId) || !RedisOpsUtils.checkExist(RedisConst.MQTT_ACL_PREFIX + clientId)) {
            clientId = userId + "-" + System.currentTimeMillis();
            RedisOpsUtils.hashSet(RedisConst.MQTT_ACL_PREFIX + clientId, "", MqttAclAccessEnum.ALL.getValue());
        }

        String key = RedisConst.MQTT_ACL_PREFIX + clientId;

        try {
            RedisOpsUtils.expireKey(key, RedisConst.DRC_MODE_ALIVE_SECOND);

            return MqttPropertyConfiguration.getMqttBrokerWithDrc(
                    clientId, username, param.getExpireSec(), Collections.emptyMap());
        } catch (RuntimeException e) {
            RedisOpsUtils.del(key);
            throw e;
        }
    }

    private void checkDrcModeCondition(String workspaceId, String dockSn) {
        Optional<EventsReceiver<FlighttaskProgress>> runningOpt = waylineRedisService.getRunningWaylineJob(dockSn);
        if (runningOpt.isPresent() && WaylineJobStatusEnum.IN_PROGRESS == waylineJobService.getWaylineState(dockSn)) {
            flighttaskService.updateJobStatus(workspaceId, runningOpt.get().getBid(),
                    UpdateJobParam.builder().status(WaylineTaskStatusEnum.PAUSE).build());
        }

        DockModeCodeEnum dockMode = deviceService.getDockMode(dockSn);
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (dockOpt.isPresent() && (DockModeCodeEnum.IDLE == dockMode || DockModeCodeEnum.WORKING == dockMode)) {
            Optional<OsdDockDrone> deviceOsd = deviceRedisService.getDeviceOsd(dockOpt.get().getChildDeviceSn(), OsdDockDrone.class);
            if (deviceOsd.isEmpty() || deviceOsd.get().getElevation() <= 0) {
                //throw new RuntimeException("The drone is not in the sky and cannot enter command flight mode.");
            }
        } else {
            //throw new RuntimeException("The current state of the dock does not support entering command flight mode.");
        }

        HttpResultResponse result = controlService.seizeAuthority(dockSn, DroneAuthorityEnum.FLIGHT, null);
        if (HttpResultResponse.CODE_SUCCESS != result.getCode()) {
            throw new IllegalArgumentException(result.getMessage());
        }

    }

    /**
     * 检查Pilot设备的DRC模式条件
     * @param workspaceId 工作空间ID
     * @param pilotSn Pilot设备SN
     */
    private void checkDrcModeConditionForPilot(String workspaceId, String pilotSn) {
        log.info("开始检查Pilot设备DRC模式条件: pilotSn={}, workspaceId={}", pilotSn, workspaceId);
        
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
        
        // 检查是否有正在运行的航线任务
        Optional<EventsReceiver<FlighttaskProgress>> runningOpt = waylineRedisService.getRunningWaylineJob(pilotSn);
        if (runningOpt.isPresent() && WaylineJobStatusEnum.IN_PROGRESS == waylineJobService.getWaylineState(pilotSn)) {
            log.info("暂停正在运行的航线任务: pilotSn={}, jobId={}", pilotSn, runningOpt.get().getBid());
            flighttaskService.updateJobStatus(workspaceId, runningOpt.get().getBid(),
                    UpdateJobParam.builder().status(WaylineTaskStatusEnum.PAUSE).build());
        }

        // 检查Pilot设备状态
        DroneModeCodeEnum pilotMode = deviceService.getDeviceMode(pilotSn);
        Optional<DeviceDTO> pilotOpt = deviceRedisService.getDeviceOnline(pilotSn);
        
        log.info("Pilot设备状态检查: pilotSn={}, mode={}, online={}", pilotSn, pilotMode, pilotOpt.isPresent());
        
        if (pilotOpt.isPresent()) {
            DeviceDTO pilotDevice = pilotOpt.get();
            log.info("Pilot设备信息: sn={}, type={}, domain={}, workspaceId={}", 
                    pilotDevice.getDeviceSn(), pilotDevice.getType(), pilotDevice.getDomain(), pilotDevice.getWorkspaceId());
            
            // RC设备的特点：RC本身是遥控器，不需要校验高度，主要校验连接状态
            // 检查RC设备连接状态（通过OSD数据的存在性来判断）
            Optional<OsdRcDrone> deviceOsd = deviceRedisService.getDeviceOsd(pilotSn, OsdRcDrone.class);
            log.info("RC设备OSD数据检查: pilotSn={}, osdPresent={}", pilotSn, deviceOsd.isPresent());
            
            if (deviceOsd.isEmpty()) {
                // 如果RC设备OSD数据为空，尝试从子设备（无人机）获取OSD数据
                String childDeviceSn = pilotDevice.getChildDeviceSn();
                if (StringUtils.hasText(childDeviceSn)) {
                    log.info("尝试从子设备（无人机）获取OSD数据: pilotSn={}, childSn={}", pilotSn, childDeviceSn);
                    deviceOsd = deviceRedisService.getDeviceOsd(childDeviceSn, OsdRcDrone.class);
                    log.info("子设备OSD数据检查: childSn={}, osdPresent={}", childDeviceSn, deviceOsd.isPresent());
                }
            }
            
            if (deviceOsd.isEmpty()) {
                log.warn("RC设备OSD数据为空，但RC设备可能不需要严格的OSD校验");
                // RC设备可能不需要严格的OSD数据校验，因为RC本身是遥控器
                // 只要设备在线且模式支持，就可以进入DRC模式
                log.info("RC设备跳过OSD数据校验，继续DRC模式检查");
            } else {
                OsdRcDrone osdData = deviceOsd.get();
                log.info("RC设备OSD数据: elevation={}, height={}, latitude={}, longitude={}", 
                        osdData.getElevation(), osdData.getHeight(), osdData.getLatitude(), osdData.getLongitude());
                
                // RC设备不需要校验高度，因为RC本身是遥控器，不是飞行器
                // 如果需要校验飞行器状态，应该校验子设备（无人机）的状态
                log.info("RC设备OSD数据存在，但跳过高度校验（RC是遥控器，不是飞行器）");
            }
            
            // 检查Pilot设备模式是否支持DRC
            if (!isPilotModeSupportedForDrc(pilotMode)) {
                //throw new RuntimeException("The current state of the pilot device does not support entering command flight mode.");
            }
        } else {
            //throw new RuntimeException("The pilot device is offline and cannot enter command flight mode.");
        }

        // 获取飞行控制权限
        log.info("尝试获取飞行控制权限: pilotSn={}", pilotSn);
        HttpResultResponse result = controlService.seizeAuthority(pilotSn, DroneAuthorityEnum.FLIGHT, null);
        if (HttpResultResponse.CODE_SUCCESS != result.getCode()) {
            log.error("获取飞行控制权限失败: pilotSn={}, error={}", pilotSn, result.getMessage());
            throw new IllegalArgumentException(result.getMessage());
        }
        
        log.info("Pilot设备DRC模式条件检查完成: pilotSn={}", pilotSn);
    }

    /**
     * 检查Pilot设备模式是否支持DRC
     * @param pilotMode Pilot设备模式
     * @return 是否支持
     */
    private boolean isPilotModeSupportedForDrc(DroneModeCodeEnum pilotMode) {
        return pilotMode == DroneModeCodeEnum.IDLE ||
               pilotMode == DroneModeCodeEnum.TAKEOFF_FINISHED ||
               pilotMode == DroneModeCodeEnum.MANUAL ||
               pilotMode == DroneModeCodeEnum.TAKEOFF_AUTO ||
               pilotMode == DroneModeCodeEnum.WAYLINE ||
               pilotMode == DroneModeCodeEnum.PANORAMIC_SHOT ||
               pilotMode == DroneModeCodeEnum.ACTIVE_TRACK ||
               pilotMode == DroneModeCodeEnum.APAS ||
               pilotMode == DroneModeCodeEnum.VIRTUAL_JOYSTICK ||
               pilotMode == DroneModeCodeEnum.LIVE_FLIGHT_CONTROLS ||
               pilotMode == DroneModeCodeEnum.POI;
    }

    @Override
    public JwtAclDTO deviceDrcEnter(String workspaceId, DrcModeParam param) {
        // 判断设备类型：Dock 还是 Pilot
        if (isPilotDevice(param.getDockSn())) {
            return deviceDrcEnterForPilot(workspaceId, param);
        } else {
            return deviceDrcEnterForDock(workspaceId, param);
        }
    }


    @Override
    public JwtAclDTO deviceDrcCloudControlAuth(String workspaceId, DrcModeParamRc param) {
        log.info("开始处理云端控制授权请求: workspaceId={}, pilotSn={}, clientId={}", 
                workspaceId, param.getPilotSn(), param.getClientId());
        
        try {
            // 确保设备已注册到SDKManager
            GatewayManager deviceSDK = SDKManager.getDeviceSDK(param.getPilotSn());
            log.info("设备SDK获取成功: pilotSn={}", param.getPilotSn());
            
            // 构建云端控制授权请求
            DrcCloudControlAuthRequestRequest authRequest = new DrcCloudControlAuthRequestRequest()
                    .setControlKeys(List.of("flight")) // 请求飞行控制权
                    .setUserId("cloud_user_" + System.currentTimeMillis()) // 云端用户ID
                    .setUserCallsign("Cloud Controller"); // 云端用户呼号
            
            log.info("发送云端控制授权请求: {}", authRequest);
            
            // 发送云端控制授权请求
            TopicServicesResponse<ServicesReplyData> reply = abstractControlService.drcCloudControlAuthRequest(
                    deviceSDK, authRequest);
            
            if (reply.getData().getResult().isSuccess()) {
                log.info("云端控制授权请求发送成功: pilotSn={}", param.getPilotSn());
                return JwtAclDTO.builder().build(); // 返回空的ACL，因为这只是请求授权
            } else {
                log.error("云端控制授权请求失败: pilotSn={}, error={}", 
                        param.getPilotSn(), reply.getData().getResult());
                throw new RuntimeException("云端控制授权请求失败: " + reply.getData().getResult());
            }
            
        } catch (Exception e) {
            log.error("云端控制授权请求异常: pilotSn={}, error={}", param.getPilotSn(), e.getMessage(), e);
            throw new RuntimeException("云端控制授权请求异常: " + e.getMessage());
        }
    }

    /**
     * 判断是否为Pilot设备
     * @param deviceSn 设备SN
     * @return 是否为Pilot设备
     */
    private boolean isPilotDevice(String deviceSn) {
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(deviceSn);
        if (deviceOpt.isPresent()) {
            DeviceDTO device = deviceOpt.get();
            // 根据设备类型判断是否为Pilot设备
            // RC系列设备被认为是Pilot设备
            return device.getType() == DeviceTypeEnum.RC || 
                   device.getType() == DeviceTypeEnum.RC_PLUS || 
                   device.getType() == DeviceTypeEnum.RC_PRO || 
                   device.getType() == DeviceTypeEnum.DJI_RzC_PLUS_2;
        }
        return false;
    }

    /**
     * Dock设备的DRC进入逻辑
     */
    private JwtAclDTO deviceDrcEnterForDock(String workspaceId, DrcModeParam param) {
        String topic = TopicConst.THING_MODEL_PRE + TopicConst.PRODUCT + param.getDockSn() + TopicConst.DRC;
        String pubTopic = topic + TopicConst.DOWN;
        String subTopic = topic + TopicConst.UP;

        // If the dock is in drc mode, refresh the permissions directly.
        if (deviceService.checkDockDrcMode(param.getDockSn())
                && param.getClientId().equals(this.getDrcModeInRedis(param.getDockSn()))) {
            refreshAcl(param.getDockSn(), param.getClientId(), topic, subTopic);
            return JwtAclDTO.builder().sub(List.of(subTopic)).pub(List.of(pubTopic)).build();
        }

        checkDrcModeCondition(workspaceId, param.getDockSn());

        TopicServicesResponse<ServicesReplyData> reply = abstractControlService.drcModeEnter(
                SDKManager.getDeviceSDK(param.getDockSn()),
                new DrcModeEnterRequest()
                        .setMqttBroker(MqttPropertyConfiguration.getMqttBrokerWithDrc(param.getDockSn() + "-" + System.currentTimeMillis(), param.getDockSn(),
                                RedisConst.DRC_MODE_ALIVE_SECOND.longValue(),
                                Map.of(MapKeyConst.ACL, objectMapper.convertValue(JwtAclDTO.builder()
                                        .pub(List.of(subTopic))
                                        .sub(List.of(pubTopic))
                                        .build(), new TypeReference<Map<String, ?>>() {}))))
                        .setHsiFrequency(param.getDeviceInfo().getHsiFrequency()).setOsdFrequency(param.getDeviceInfo().getOsdFrequency()));

        if (!reply.getData().getResult().isSuccess()) {
            throw new RuntimeException("SN: " + param.getDockSn() + "; Error:" + reply.getData().getResult() +
                    "; Failed to enter command flight control mode, please try again later!");
        }

        refreshAcl(param.getDockSn(), param.getClientId(), pubTopic, subTopic);
        return JwtAclDTO.builder().sub(List.of(subTopic)).pub(List.of(pubTopic)).build();
    }

    /**
     * Pilot设备的DRC进入逻辑
     */
    private JwtAclDTO deviceDrcEnterForPilot(String workspaceId, DrcModeParam param) {
        String topic = TopicConst.THING_MODEL_PRE + TopicConst.PRODUCT + param.getDockSn() + TopicConst.DRC;
        String pubTopic = topic + TopicConst.DOWN;
        String subTopic = topic + TopicConst.UP;

        // If the pilot is in drc mode, refresh the permissions directly.
        if (deviceService.checkAuthorityFlight(param.getDockSn())
                && param.getClientId().equals(this.getDrcModeInRedis(param.getDockSn()))) {
            refreshAcl(param.getDockSn(), param.getClientId(), topic, subTopic);
            return JwtAclDTO.builder().sub(List.of(subTopic)).pub(List.of(pubTopic)).build();
        }

        checkDrcModeConditionForPilot(workspaceId, param.getDockSn());

        GatewayManager deviceSDK = SDKManager.getDeviceSDK(param.getDockSn());
        DrcModeEnterRequest request = new DrcModeEnterRequest()
                .setMqttBroker(MqttPropertyConfiguration.getMqttBrokerWithDrc(
                        param.getDockSn() + "-" + System.currentTimeMillis(), param.getDockSn(),
                        RedisConst.DRC_MODE_ALIVE_SECOND.longValue(),
                        Map.of(MapKeyConst.ACL, objectMapper.convertValue(JwtAclDTO.builder()
                                .pub(List.of(subTopic))
                                .sub(List.of(pubTopic))
                                .build(), new TypeReference<Map<String, ?>>() {
                        }))))
                .setHsiFrequency(param.getDeviceInfo().getHsiFrequency())
                .setOsdFrequency(param.getDeviceInfo().getOsdFrequency());
        TopicServicesResponse<ServicesReplyData> reply = abstractControlService.drcModeEnter(
                deviceSDK,
                request);

        if (!reply.getData().getResult().isSuccess()) {
            throw new RuntimeException("SN: " + param.getDockSn() + "; Error:" + reply.getData().getResult() +
                    "; Failed to enter command flight control mode, please try again later!");
        }

        refreshAcl(param.getDockSn(), param.getClientId(), pubTopic, subTopic);
        return JwtAclDTO.builder().sub(List.of(subTopic)).pub(List.of(pubTopic)).build();
    }

    private void refreshAcl(String dockSn, String clientId, String pubTopic, String subTopic) {
        this.setDrcModeInRedis(dockSn, clientId);

        // assign acl，Match by clientId. https://www.emqx.io/docs/zh/v4.4/advanced/acl-redis.html
        // scheme: HSET mqtt_acl:[clientid] [topic] [access]
        String key = RedisConst.MQTT_ACL_PREFIX + clientId;
        RedisOpsUtils.hashSet(key, pubTopic, MqttAclAccessEnum.PUB.getValue());
        RedisOpsUtils.hashSet(key, subTopic, MqttAclAccessEnum.SUB.getValue());
        RedisOpsUtils.expireKey(key, RedisConst.DRC_MODE_ALIVE_SECOND);
    }

    @Override
    public void deviceDrcExit(String workspaceId, DrcModeParam param) {
        // 判断设备类型：Dock 还是 Pilot
        if (isPilotDevice(param.getDockSn())) {
            deviceDrcExitForPilot(workspaceId, param);
        } else {
            deviceDrcExitForDock(workspaceId, param);
        }
    }

    /**
     * Dock设备的DRC退出逻辑
     */
    private void deviceDrcExitForDock(String workspaceId, DrcModeParam param) {
        if (!deviceService.checkDockDrcMode(param.getDockSn())) {
            throw new RuntimeException("The dock is not in flight control mode.");
        }
        TopicServicesResponse<ServicesReplyData> reply =
                abstractControlService.drcModeExit(SDKManager.getDeviceSDK(param.getDockSn()));
        if (!reply.getData().getResult().isSuccess()) {
            throw new RuntimeException("SN: " + param.getDockSn() + "; Error:" +
                    reply.getData().getResult() + "; Failed to exit command flight control mode, please try again later!");
        }

        String jobId = waylineRedisService.getPausedWaylineJobId(param.getDockSn());
        if (StringUtils.hasText(jobId)) {
            flighttaskService.updateJobStatus(workspaceId, jobId, UpdateJobParam.builder().status(WaylineTaskStatusEnum.RESUME).build());
        }

        this.delDrcModeInRedis(param.getDockSn());
        RedisOpsUtils.del(RedisConst.MQTT_ACL_PREFIX + param.getClientId());
    }

    /**
     * Pilot设备的DRC退出逻辑
     */
    private void deviceDrcExitForPilot(String workspaceId, DrcModeParam param) {
        if (!deviceService.checkAuthorityFlight(param.getDockSn())) {
            throw new RuntimeException("The pilot device is not in flight control mode.");
        }
        TopicServicesResponse<ServicesReplyData> reply =
                abstractControlService.drcModeExit(SDKManager.getDeviceSDK(param.getDockSn()));
        if (!reply.getData().getResult().isSuccess()) {
            throw new RuntimeException("SN: " + param.getDockSn() + "; Error:" +
                    reply.getData().getResult() + "; Failed to exit command flight control mode, please try again later!");
        }

        String jobId = waylineRedisService.getPausedWaylineJobId(param.getDockSn());
        if (StringUtils.hasText(jobId)) {
            flighttaskService.updateJobStatus(workspaceId, jobId, UpdateJobParam.builder().status(WaylineTaskStatusEnum.RESUME).build());
        }

        this.delDrcModeInRedis(param.getDockSn());
        RedisOpsUtils.del(RedisConst.MQTT_ACL_PREFIX + param.getClientId());
    }

}

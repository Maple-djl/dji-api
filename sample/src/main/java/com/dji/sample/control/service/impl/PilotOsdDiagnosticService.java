package com.dji.sample.control.service.impl;

import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sdk.cloudapi.device.OsdRcDrone;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.config.version.GatewayManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Pilot设备OSD数据诊断服务
 * 用于调试和诊断Pilot设备OSD数据问题
 */
@Service
@Slf4j
public class PilotOsdDiagnosticService {

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IDeviceService deviceService;

    /**
     * 诊断Pilot设备OSD数据问题
     * @param pilotSn Pilot设备SN
     * @return 诊断结果
     */
    public String diagnosePilotOsd(String pilotSn) {
        StringBuilder result = new StringBuilder();
        result.append("=== Pilot设备OSD数据诊断报告 ===\n");
        result.append("设备SN: ").append(pilotSn).append("\n\n");

        // 1. 检查设备在线状态
        result.append("1. 设备在线状态检查:\n");
        boolean isOnline = deviceRedisService.checkDeviceOnline(pilotSn);
        result.append("   在线状态: ").append(isOnline ? "在线" : "离线").append("\n");

        if (!isOnline) {
            result.append("   ❌ 设备离线，无法获取OSD数据\n");
            return result.toString();
        }

        // 2. 检查设备基本信息
        result.append("\n2. 设备基本信息检查:\n");
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(pilotSn);
        if (deviceOpt.isPresent()) {
            DeviceDTO device = deviceOpt.get();
            result.append("   设备名称: ").append(device.getDeviceName()).append("\n");
            result.append("   设备类型: ").append(device.getType()).append("\n");
            result.append("   设备域: ").append(device.getDomain()).append("\n");
            result.append("   子设备SN: ").append(device.getChildDeviceSn()).append("\n");
            result.append("   工作空间ID: ").append(device.getWorkspaceId()).append("\n");
        } else {
            result.append("   ❌ 无法获取设备基本信息\n");
            return result.toString();
        }

        // 3. 检查SDK注册状态
        result.append("\n3. SDK注册状态检查:\n");
        try {
            GatewayManager gateway = SDKManager.getDeviceSDK(pilotSn);
            result.append("   ✅ 设备已注册到SDKManager\n");
            result.append("   网关类型: ").append(gateway.getType()).append("\n");
            result.append("   无人机SN: ").append(gateway.getDroneSn()).append("\n");
        } catch (Exception e) {
            result.append("   ❌ 设备未注册到SDKManager: ").append(e.getMessage()).append("\n");
        }

        // 4. 检查OSD数据
        result.append("\n4. OSD数据检查:\n");
        Optional<OsdRcDrone> osdOpt = deviceRedisService.getDeviceOsd(pilotSn, OsdRcDrone.class);
        if (osdOpt.isPresent()) {
            OsdRcDrone osd = osdOpt.get();
            result.append("   ✅ 找到OSD数据\n");
            result.append("   高度: ").append(osd.getElevation()).append("m\n");
            result.append("   相对高度: ").append(osd.getHeight()).append("m\n");
            result.append("   纬度: ").append(osd.getLatitude()).append("\n");
            result.append("   经度: ").append(osd.getLongitude()).append("\n");
            result.append("   水平速度: ").append(osd.getHorizontalSpeed()).append("m/s\n");
            result.append("   垂直速度: ").append(osd.getVerticalSpeed()).append("m/s\n");
            result.append("   ℹ️  注意：RC设备是遥控器，高度信息可能来自连接的无人机\n");
        } else {
            result.append("   ⚠️  未找到RC设备OSD数据\n");
            
            // 尝试从子设备获取OSD数据
            DeviceDTO device = deviceOpt.get();
            String childSn = device.getChildDeviceSn();
            if (StringUtils.hasText(childSn)) {
                result.append("   尝试从子设备（无人机）获取OSD数据: ").append(childSn).append("\n");
                Optional<OsdRcDrone> childOsdOpt = deviceRedisService.getDeviceOsd(childSn, OsdRcDrone.class);
                if (childOsdOpt.isPresent()) {
                    OsdRcDrone childOsd = childOsdOpt.get();
                    result.append("   ✅ 从子设备找到OSD数据\n");
                    result.append("   高度: ").append(childOsd.getElevation()).append("m\n");
                    result.append("   ℹ️  这是无人机的OSD数据，RC设备通过无人机获取状态信息\n");
                } else {
                    result.append("   ❌ 子设备也没有OSD数据\n");
                }
            } else {
                result.append("   ℹ️  RC设备没有子设备，这是正常的（RC可能独立工作）\n");
            }
        }

        // 5. 检查设备模式
        result.append("\n5. 设备模式检查:\n");
        try {
            var deviceMode = deviceService.getDeviceMode(pilotSn);
            result.append("   设备模式: ").append(deviceMode).append("\n");
        } catch (Exception e) {
            result.append("   ❌ 无法获取设备模式: ").append(e.getMessage()).append("\n");
        }

        // 6. 建议
        result.append("\n6. 问题诊断和建议:\n");
        if (!osdOpt.isPresent()) {
            result.append("   🔍 RC设备OSD数据缺失的可能原因:\n");
            result.append("   1. RC设备未正确注册到SDKManager\n");
            result.append("   2. 未订阅OSD相关的MQTT topic\n");
            result.append("   3. RC设备未发送OSD数据\n");
            result.append("   4. OSD数据路由配置问题\n");
            result.append("   5. Redis存储问题\n");
            result.append("\n   💡 RC设备特殊说明:\n");
            result.append("   1. RC设备是遥控器，不是飞行器\n");
            result.append("   2. RC设备可能不需要严格的OSD数据校验\n");
            result.append("   3. RC设备的OSD数据可能来自连接的无人机\n");
            result.append("   4. 只要RC设备在线且模式支持，就可以进入DRC模式\n");
            result.append("\n   💡 建议检查:\n");
            result.append("   1. 确认RC设备已正确上线并注册\n");
            result.append("   2. 检查MQTT连接和topic订阅\n");
            result.append("   3. 查看RC设备日志确认连接状态\n");
            result.append("   4. 检查是否有连接的无人机设备\n");
        } else {
            result.append("   ✅ RC设备OSD数据正常，可以继续DRC模式操作\n");
            result.append("   ℹ️  注意：RC设备的高度信息可能来自连接的无人机\n");
        }

        return result.toString();
    }

    /**
     * 打印诊断结果到日志
     * @param pilotSn Pilot设备SN
     */
    public void printDiagnosticReport(String pilotSn) {
        String report = diagnosePilotOsd(pilotSn);
        log.info("\n{}", report);
    }
}

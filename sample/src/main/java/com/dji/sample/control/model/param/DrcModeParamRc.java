package com.dji.sample.control.model.param;

import com.dji.sample.component.redis.RedisConst;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * DRC模式参数类 - 专门用于RC/Pilot设备
 * @author sean
 * @version 1.3
 * @date 2023/1/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrcModeParamRc {

    @NotBlank
    private String clientId;

    @NotBlank
    private String pilotSn;  // 使用pilotSn替代dockSn

    @Range(min = 1800, max = 86400)
    @Builder.Default
    private long expireSec = RedisConst.DRC_MODE_ALIVE_SECOND;

    @Valid
    @Builder.Default
    private DeviceDrcInfoParam deviceInfo = new DeviceDrcInfoParam();
}

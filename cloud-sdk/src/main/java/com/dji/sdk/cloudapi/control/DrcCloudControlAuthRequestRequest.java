package com.dji.sdk.cloudapi.control;

import com.dji.sdk.common.BaseModel;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author sean
 * @version 1.3
 * @date 2023/1/12
 */
public class DrcCloudControlAuthRequestRequest extends BaseModel {

    @NotEmpty
    private List<String> controlKeys;

    @NotNull
    private String userCallsign;

    @NotNull
    private String userId;

    public DrcCloudControlAuthRequestRequest() {
    }

    @Override
    public String toString() {
        return "DrcCloudControlAuthRequestRequest{" +
                "controlKeys=" + controlKeys +
                ", userCallsign='" + userCallsign + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }

    public List<String> getControlKeys() {
        return controlKeys;
    }

    public DrcCloudControlAuthRequestRequest setControlKeys(List<String> controlKeys) {
        this.controlKeys = controlKeys;
        return this;
    }

    public String getUserCallsign() {
        return userCallsign;
    }

    public DrcCloudControlAuthRequestRequest setUserCallsign(String userCallsign) {
        this.userCallsign = userCallsign;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public DrcCloudControlAuthRequestRequest setUserId(String userId) {
        this.userId = userId;
        return this;
    }
}

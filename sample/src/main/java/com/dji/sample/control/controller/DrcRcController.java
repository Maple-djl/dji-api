package com.dji.sample.control.controller;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.control.model.dto.JwtAclDTO;
import com.dji.sample.control.model.param.DrcConnectParam;
import com.dji.sample.control.model.param.DrcModeParam;
import com.dji.sample.control.model.param.DrcModeParamRc;
import com.dji.sample.control.service.IDrcRcService;
import com.dji.sdk.cloudapi.control.DrcModeMqttBroker;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;

/**
 * @author sean
 * @version 1.3
 * @date 2023/1/11
 * pilot drc controller
 */
@RestController
@Slf4j
@RequestMapping("${url.control.prefix}${url.control.version}")
public class DrcRcController {

    @Autowired
    private IDrcRcService iDrcRcService;

    @PostMapping("/workspaces/{workspace_id}/rc/drc/connect")
    public HttpResultResponse drcConnect(@PathVariable("workspace_id") String workspaceId, HttpServletRequest request, @Valid @RequestBody DrcConnectParam param) {
        CustomClaim claims = (CustomClaim) request.getAttribute(TOKEN_CLAIM);

        DrcModeMqttBroker brokerDTO = iDrcRcService.userDrcAuth(workspaceId, claims.getId(), claims.getUsername(), param);
        return HttpResultResponse.success(brokerDTO);
    }

    @PostMapping("/workspaces/{workspace_id}/rc/drc/enter")
    public HttpResultResponse drcEnter(@PathVariable("workspace_id") String workspaceId, @Valid @RequestBody DrcModeParam param) {
        JwtAclDTO acl = iDrcRcService.deviceDrcEnter(workspaceId, param);
        return HttpResultResponse.success(acl);
    }

    @PostMapping("/workspaces/{workspace_id}/rc/drc/cloudControlAuth")
    public HttpResultResponse cloudControlAuth(@PathVariable("workspace_id") String workspaceId,
                                               @Valid @RequestBody DrcModeParamRc param) {
        JwtAclDTO acl = iDrcRcService.deviceDrcCloudControlAuth(workspaceId, param);
        return HttpResultResponse.success(acl);
    }

    @PostMapping("/workspaces/{workspace_id}/rc/drc/exit")
    public HttpResultResponse drcExit(@PathVariable("workspace_id") String workspaceId, @Valid @RequestBody DrcModeParam param) {
        iDrcRcService.deviceDrcExit(workspaceId, param);

        return HttpResultResponse.success();
    }


}

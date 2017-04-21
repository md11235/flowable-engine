/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.rest.dmn.service.api.decision;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.StringUtils;
import org.flowable.dmn.api.DmnRuleService;
import org.flowable.engine.common.api.FlowableIllegalArgumentException;
import org.flowable.engine.common.api.FlowableObjectNotFoundException;
import org.flowable.rest.dmn.service.api.DmnRestResponseFactory;
import org.flowable.rest.variable.EngineRestVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yvo Swillens
 */
@RestController
@Api(tags = { "DMN Rule Service" }, description = "Execute DMN Decision", authorizations = { @Authorization(value = "basicAuth") })
public class DmnRuleServiceResource {

    @Autowired
    protected DmnRestResponseFactory dmnRestResponseFactory;

    @Autowired
    protected DmnRuleService dmnRuleService;


    @ApiOperation(value = "Execute a Decision", tags = {"DMN Rule Service"})
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Indicates the Decision has been executed")
    })
    @RequestMapping(value = "/dmn-rule/execute", method = RequestMethod.POST, produces = "application/json")
    public DmnRuleServiceResponse executeDecision(@ApiParam("request") @RequestBody DmnRuleServiceRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        if (request.getDecisionKey() == null) {
            throw new FlowableIllegalArgumentException("Decision key is required.");
        }

        Map<String, Object> inputVariables = composeInputVariables(request.getInputVariables());

        try {
            // TODO: add audit trail info

            List<Map<String, Object>> executionResult;
            if (StringUtils.isNotEmpty(request.getTenantId())) {
                if (StringUtils.isNotEmpty(request.getParentDeploymentId())) {
                    executionResult = dmnRuleService.executeDecisionByKeyParentDeploymentIdAndTenantId(
                        request.getDecisionKey(), request.getParentDeploymentId(), inputVariables, request.getTenantId());
                } else {
                    executionResult = dmnRuleService.executeDecisionByKeyAndTenantId(request.getDecisionKey(), inputVariables, request.getTenantId());
                }
            } else {
                if (StringUtils.isNotEmpty(request.getParentDeploymentId())) {
                    executionResult = dmnRuleService.executeDecisionByKeyAndParentDeploymentId(request.getDecisionKey(), request.getParentDeploymentId(), inputVariables);
                } else {
                    executionResult = dmnRuleService.executeDecisionByKey(request.getDecisionKey(), inputVariables);
                }
            }
            response.setStatus(HttpStatus.CREATED.value());

            return dmnRestResponseFactory.createDmnRuleServiceResponse(executionResult);

        } catch (FlowableObjectNotFoundException fonfe) {
            throw new FlowableIllegalArgumentException(fonfe.getMessage(), fonfe);
        }
    }

    @ApiOperation(value = "Execute a Decision expecting a single result", tags = {"DMN Rule Service"})
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Indicates the Decision has been executed"),
        @ApiResponse(code = 500, message = "Indicates the Decision returned multiple results")
    })
    @RequestMapping(value = "/dmn-rule/execute/single-result", method = RequestMethod.POST, produces = "application/json")
    public DmnRuleServiceSingleResponse executeDecisionByKeySingleResult(@ApiParam("request") @RequestBody DmnRuleServiceRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        if (request.getDecisionKey() == null) {
            throw new FlowableIllegalArgumentException("Decision key is required.");
        }

        Map<String, Object> inputVariables = composeInputVariables(request.getInputVariables());

        try {
            // TODO: add audit trail info
            Map<String, Object> executionResult;
            if (StringUtils.isNotEmpty(request.getTenantId())) {
                if (StringUtils.isNotEmpty(request.getParentDeploymentId())) {
                    executionResult = dmnRuleService.executeDecisionByKeyParentDeploymentIdAndTenantIdSingleResult(
                        request.getDecisionKey(), request.getParentDeploymentId(), inputVariables, request.getTenantId());
                } else {
                    executionResult = dmnRuleService.executeDecisionByKeyAndTenantIdSingleResult(request.getDecisionKey(), inputVariables, request.getTenantId());
                }
            } else {
                if (StringUtils.isNotEmpty(request.getParentDeploymentId())) {
                    executionResult = dmnRuleService.executeDecisionByKeyAndParentDeploymentIdSingleResult(request.getDecisionKey(), request.getParentDeploymentId(), inputVariables);
                } else {
                    executionResult = dmnRuleService.executeDecisionByKeySingleResult(request.getDecisionKey(), inputVariables);
                }
            }
            response.setStatus(HttpStatus.CREATED.value());

            return dmnRestResponseFactory.createDmnRuleServiceResponse(executionResult);
        } catch (FlowableObjectNotFoundException fonfe) {
            throw new FlowableIllegalArgumentException(fonfe.getMessage(), fonfe);
        }
    }

    protected Map<String, Object> composeInputVariables(List<EngineRestVariable> restVariables) {
        Map<String, Object> inputVariables = null;
        if (restVariables != null) {
            inputVariables = new HashMap<>();
            for (EngineRestVariable variable : restVariables) {
                if (variable.getName() == null) {
                    throw new FlowableIllegalArgumentException("Variable name is required.");
                }
                inputVariables.put(variable.getName(), dmnRestResponseFactory.getVariableValue(variable));
            }
        }
        return inputVariables;
    }
}
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
package org.flowable.engine.impl.cmd;

import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.business.ActionToBuildSubProcess;
import org.flowable.engine.impl.context.Context;
import org.flowable.engine.impl.dynamic.BaseDynamicSubProcessInjectUtil;
import org.flowable.engine.impl.dynamic.DqDynamicEmbeddedSubProcessBuilder;
import org.flowable.engine.impl.persistence.entity.*;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DqInjectEmbeddedSubProcessInProcessInstanceCmd extends AbstractDynamicInjectionCmd implements Command<Void> {

    protected String processInstanceId;
    protected DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder;
    private List<Activity> addedActivities;
    private FlowElementsContainer parentContainer;

    public DqInjectEmbeddedSubProcessInProcessInstanceCmd(
            String processInstanceId,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder) {
        this.processInstanceId = processInstanceId;
        this.dynamicEmbeddedSubProcessBuilder = dynamicEmbeddedSubProcessBuilder;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        createDerivedProcessDefinitionForProcessInstance(commandContext, processInstanceId);
        return null;
    }

    @Override
    protected void updateBpmnProcess(CommandContext commandContext, Process process,
            BpmnModel bpmnModel, ProcessDefinitionEntity originalProcessDefinitionEntity, DeploymentEntity newDeploymentEntity) {
//        // 查找用于插入新的SubProcess或者UserTask的parent container
//        FlowElementsContainer parentContainer = null;
//        if(process.getId().equals(dynamicEmbeddedSubProcessBuilder.getParentSubProcessDefKey())) {
//            parentContainer = process;
//        } else {
//            List<SubProcess> subProcessList = process.findFlowElementsOfType(SubProcess.class, true);
//            for(SubProcess aSubProcess : subProcessList) {
//                if(aSubProcess.getId().equals(dynamicEmbeddedSubProcessBuilder.getParentSubProcessDefKey())) {
//                    parentContainer = aSubProcess;
//                    break;
//                }
//            }
//        }
//
//        this.parentContainer = parentContainer;
//
//        // 往parent container里插入新的SubProcess
//        // actionToBuildSubProcessCallback.build 返回的是新的子流程里的StartEvent的ID
//        if(this.actionToBuildSubProcessCallback == null) {
//            throw new FlowableException("构建子流程的回调接口为空。");
//        }
//        this.addedActivities =
//                this.actionToBuildSubProcessCallback.build(
//                        parentContainer,
//                        this.dynamicEmbeddedSubProcessBuilder);

        BaseDynamicSubProcessInjectUtil.processFlowElements(commandContext, process,
                bpmnModel, originalProcessDefinitionEntity, newDeploymentEntity);
    }

    @Override
    protected void updateExecutions(CommandContext commandContext, ProcessDefinitionEntity processDefinitionEntity,
                                    ExecutionEntity processInstance, List<ExecutionEntity> childExecutions) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinitionEntity.getId());

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);

        List<ActivityInstanceEntity> maybeSubProcessActivityIE = CommandContextUtil
                .getActivityInstanceEntityManager(commandContext)
                .findActivityInstancesByActivityId(
                        this.dynamicEmbeddedSubProcessBuilder.getParentSubProcessDefKey());

        List<ActivityInstanceEntity> subProcessActivityIE = maybeSubProcessActivityIE.stream().filter(ie -> {
            return ie.getDeleteReason() == null;
        }).collect(Collectors.toList());

        ExecutionEntity subProcessExecutionEntity = executionEntityManager.findById(subProcessActivityIE.get(0).getExecutionId());

        ExecutionEntity execution = executionEntityManager.createChildExecution(subProcessExecutionEntity);

        FlowElement nextExecutionTarget = bpmnModel
                .getProcessById(processDefinitionEntity.getKey())
                .getFlowElement(
                this.dynamicEmbeddedSubProcessBuilder.getDynamicChildSubProcessDefKey(), true);
        execution.setCurrentFlowElement(nextExecutionTarget);

        Context.getAgenda().planContinueProcessOperation(execution);
    }
}
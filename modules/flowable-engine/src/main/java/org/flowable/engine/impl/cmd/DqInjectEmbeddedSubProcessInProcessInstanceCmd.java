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
import org.flowable.engine.impl.persistence.entity.DeploymentEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.util.List;

public class DqInjectEmbeddedSubProcessInProcessInstanceCmd extends AbstractDynamicInjectionCmd implements Command<Void> {

    protected String processInstanceId;
    protected DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder;
    protected ActionToBuildSubProcess actionToBuildSubProcessCallback;
    protected String firstActivityIdInNewSubProcess;

    public DqInjectEmbeddedSubProcessInProcessInstanceCmd(
            String processInstanceId,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder,
            ActionToBuildSubProcess actionToBuildSubProcess) {
        this.processInstanceId = processInstanceId;
        this.dynamicEmbeddedSubProcessBuilder = dynamicEmbeddedSubProcessBuilder;
        this.actionToBuildSubProcessCallback = actionToBuildSubProcess;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        createDerivedProcessDefinitionForProcessInstance(commandContext, processInstanceId);
        return null;
    }

    @Override
    protected void updateBpmnProcess(CommandContext commandContext, Process process,
            BpmnModel bpmnModel, ProcessDefinitionEntity originalProcessDefinitionEntity, DeploymentEntity newDeploymentEntity) {
        // 查找新的SubProcess的parent container
        FlowElementsContainer parentContainer = null;

        if(process.getId().equals(dynamicEmbeddedSubProcessBuilder.getDynamicSubProcessParentDefKey())) {
            parentContainer = process;
        } else {
            List<SubProcess> subProcessList = process.findFlowElementsOfType(SubProcess.class, true);
            for(SubProcess aSubProcess : subProcessList) {
                if(aSubProcess.getId().equals(dynamicEmbeddedSubProcessBuilder.getDynamicSubProcessParentDefKey())) {
                    parentContainer = aSubProcess;
                    break;
                }
            }
        }

        // 往parent container里插入新的SubProcess
        // actionToBuildSubProcessCallback.build 返回的是新的子流程里的StartEvent的ID
        if(this.actionToBuildSubProcessCallback == null) {
            throw new FlowableException("构建子流程的回调接口为空。");
        }
        this.firstActivityIdInNewSubProcess =
                this.actionToBuildSubProcessCallback.build(
                        parentContainer,
                        this.dynamicEmbeddedSubProcessBuilder);

        BaseDynamicSubProcessInjectUtil.processFlowElements(commandContext, process, bpmnModel, originalProcessDefinitionEntity, newDeploymentEntity);
    }

    @Override
    protected void updateExecutions(CommandContext commandContext, ProcessDefinitionEntity processDefinitionEntity,
                                    ExecutionEntity processInstance, List<ExecutionEntity> childExecutions) {

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinitionEntity.getId());
        SubProcess newDynamicSubProcess = (SubProcess) bpmnModel.getFlowElement(dynamicEmbeddedSubProcessBuilder.getDynamicSubProcessId());
        ExecutionEntity subProcessExecution = executionEntityManager.createChildExecution(processInstance);
        subProcessExecution.setScope(true);
        subProcessExecution.setCurrentFlowElement(newDynamicSubProcess);
        CommandContextUtil.getActivityInstanceEntityManager(commandContext).recordActivityStart(subProcessExecution);
        
        ExecutionEntity childExecution = executionEntityManager.createChildExecution(subProcessExecution);
        
        StartEvent initialEvent = null;
        for (FlowElement subElement : newDynamicSubProcess.getFlowElements()) {
            if (subElement instanceof StartEvent) {
                StartEvent startEvent = (StartEvent) subElement;
                if (startEvent.getEventDefinitions().size() == 0) {
                    initialEvent = startEvent;
                    break;
                }
            }
        }
        
        if (initialEvent == null) {
            throw new FlowableException("Could not find a none start event in dynamic sub process");
        }
        
        childExecution.setCurrentFlowElement(initialEvent);
        
        Context.getAgenda().planContinueProcessOperation(childExecution);
    }

}
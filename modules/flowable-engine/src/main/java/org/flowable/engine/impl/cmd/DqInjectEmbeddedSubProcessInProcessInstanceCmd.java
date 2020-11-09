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

import java.util.List;

public class DqInjectEmbeddedSubProcessInProcessInstanceCmd extends AbstractDynamicInjectionCmd implements Command<Void> {

    protected String processInstanceId;
    protected DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder;
    protected ActionToBuildSubProcess actionToBuildSubProcessCallback;
    private FlowElementsContainer parentContainerOfSubProcessHierarchy;

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
        this.parentContainerOfSubProcessHierarchy =
                this.actionToBuildSubProcessCallback.build(
                        parentContainer,
                        this.dynamicEmbeddedSubProcessBuilder);

        BaseDynamicSubProcessInjectUtil.processFlowElements(commandContext, this.parentContainerOfSubProcessHierarchy,
                bpmnModel, originalProcessDefinitionEntity, newDeploymentEntity);
    }

    @Override
    protected void updateExecutions(CommandContext commandContext, ProcessDefinitionEntity processDefinitionEntity,
                                    ExecutionEntity processInstance, List<ExecutionEntity> childExecutions) {

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        
        // BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinitionEntity.getId());
        // SubProcess newDynamicSubProcess = (SubProcess) bpmnModel.getFlowElement(dynamicEmbeddedSubProcessBuilder.getDynamicSubProcessId());

        FlowElementsContainer currentContainer = this.parentContainerOfSubProcessHierarchy;

        SubProcess nextLevelSubProcess = null;
        ExecutionEntity nextLevelSubProcessExecution = null;
        while(currentContainer != null) {
            nextLevelSubProcess = getNextLevelSubProcess(currentContainer);

            if(nextLevelSubProcess == null) {
                break;
            }

            ExecutionEntity currentExecutionEntity = getCurrentContainerExecutionEntity(
                    commandContext, processInstance, executionEntityManager, currentContainer);

            nextLevelSubProcessExecution = executionEntityManager.createChildExecution(currentExecutionEntity);
            nextLevelSubProcessExecution.setScope(true);
            nextLevelSubProcessExecution.setCurrentFlowElement(nextLevelSubProcess);
            CommandContextUtil.getActivityInstanceEntityManager(commandContext).recordActivityStart(nextLevelSubProcessExecution);

            ExecutionEntity nextLevelSubProcessChildExecution = executionEntityManager.createChildExecution(nextLevelSubProcessExecution);

            StartEvent nextLevelSubProcessStartEvent = null;
            for (FlowElement subElement : nextLevelSubProcess.getFlowElements()) {
                if (subElement instanceof StartEvent) {
                    StartEvent startEvent = (StartEvent) subElement;
                    if (startEvent.getEventDefinitions().size() == 0) {
                        nextLevelSubProcessStartEvent = startEvent;
                        break;
                    }
                }
            }

            if (nextLevelSubProcessStartEvent == null) {
                throw new FlowableException("Could not find a none start event in dynamic sub process");
            }

            nextLevelSubProcessChildExecution.setCurrentFlowElement(nextLevelSubProcessStartEvent);

            Context.getAgenda().planContinueProcessOperation(nextLevelSubProcessChildExecution);

            currentContainer = nextLevelSubProcess;
        }
    }

    private ExecutionEntity getCurrentContainerExecutionEntity(CommandContext commandContext, ExecutionEntity processInstance, ExecutionEntityManager executionEntityManager, FlowElementsContainer currentContainer) {
        ExecutionEntity currentExecutionEntity = null;
        if(currentContainer instanceof SubProcess) {
            List<ActivityInstanceEntity> currentContainerActivityIE = CommandContextUtil
                    .getActivityInstanceEntityManager(commandContext).findActivityInstancesByActivityId(((SubProcess) currentContainer).getId());
            currentExecutionEntity = executionEntityManager.findById(currentContainerActivityIE.get(0).getExecutionId());
        } else if(currentContainer instanceof Process) {
            currentExecutionEntity = processInstance;
        }
        return currentExecutionEntity;
    }

    private SubProcess getNextLevelSubProcess(FlowElementsContainer currentContainer) {
        List<SubProcess> subProcessList = null;
        SubProcess nextLevelSubProcess = null;
        if(currentContainer instanceof Process) {
            subProcessList = ((Process) currentContainer).findFlowElementsOfType(SubProcess.class);
            if(subProcessList.size() != 1) {
                throw new FlowableException("子流程数据格式格式不正确。");
            }
            nextLevelSubProcess = subProcessList.get(0);
        } else if(currentContainer instanceof SubProcess) {
            subProcessList = ((SubProcess) currentContainer).findAllSubFlowElementInFlowMapOfType(SubProcess.class);
            for(SubProcess aSubProcess : subProcessList) {
                if(aSubProcess.getParentContainer().equals(currentContainer)) {
                    nextLevelSubProcess = aSubProcess;
                    break;
                }
            }
        }

        return nextLevelSubProcess;
    }
}
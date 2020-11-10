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
    protected ActionToBuildSubProcess actionToBuildSubProcessCallback;
    private List<Activity> addedActivities;
    private FlowElementsContainer parentContainer;

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
        // 查找用于插入新的SubProcess或者UserTask的parent container
        FlowElementsContainer parentContainer = null;
        if(process.getId().equals(dynamicEmbeddedSubProcessBuilder.getParentDefKeyOfNewDynamicActivity())) {
            parentContainer = process;
        } else {
            List<SubProcess> subProcessList = process.findFlowElementsOfType(SubProcess.class, true);
            for(SubProcess aSubProcess : subProcessList) {
                if(aSubProcess.getId().equals(dynamicEmbeddedSubProcessBuilder.getParentDefKeyOfNewDynamicActivity())) {
                    parentContainer = aSubProcess;
                    break;
                }
            }
        }

        this.parentContainer = parentContainer;

        // 往parent container里插入新的SubProcess
        // actionToBuildSubProcessCallback.build 返回的是新的子流程里的StartEvent的ID
        if(this.actionToBuildSubProcessCallback == null) {
            throw new FlowableException("构建子流程的回调接口为空。");
        }
        this.addedActivities =
                this.actionToBuildSubProcessCallback.build(
                        parentContainer,
                        this.dynamicEmbeddedSubProcessBuilder);

        BaseDynamicSubProcessInjectUtil.processFlowElements(commandContext, process,
                bpmnModel, originalProcessDefinitionEntity, newDeploymentEntity);
    }

    private void func1(
            CommandContext commandContext, ExecutionEntity processInstance,
            ExecutionEntityManager executionEntityManager,
            List<SubProcess> nextLevelSubProcesses) {
        nextLevelSubProcesses.stream().forEach(nextLevelSubProcess -> {
            ExecutionEntity parentExecutionEntity = getCurrentContainerExecutionEntity(
                    commandContext, processInstance, executionEntityManager, nextLevelSubProcess.getParentContainer());

            ExecutionEntity nextLevelSubProcessExecution = executionEntityManager.createChildExecution(parentExecutionEntity);
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

//            func1(commandContext,
//                    processInstance,
//                    executionEntityManager,
//                    getNextLevelSubProcess(nextLevelSubProcess));
        });
    }

    @Override
    protected void updateExecutions(CommandContext commandContext, ProcessDefinitionEntity processDefinitionEntity,
                                    ExecutionEntity processInstance, List<ExecutionEntity> childExecutions) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinitionEntity.getId());

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);

        this.actionToBuildSubProcessCallback.setAddedActivities(
                this.addedActivities.stream().map(act -> {
                    return (Activity)bpmnModel.getFlowElement(act.getId());
                }).collect(Collectors.toList())
        );

        // 递归处理直接添加在this.parentContainer之下的SubProcess's
        List<SubProcess> subProcessList = this.addedActivities.stream().filter(act -> {
            return (act instanceof SubProcess);
        }).map(act -> {
            return (SubProcess)bpmnModel.getFlowElement(act.getId());
        }).collect(Collectors.toList());
        func1(commandContext, processInstance, executionEntityManager, subProcessList);
//
//        this.addedActivities.stream().filter(act -> {
//            return (act instanceof SubProcess);
//        }).forEach(subProcess -> {
//            List<SubProcess> nextLevelSubProcesses = new ArrayList<>();
//            nextLevelSubProcesses.add((SubProcess)subProcess);
//
//            func1(commandContext,
//                    processInstance,
//                    executionEntityManager,
//                    nextLevelSubProcesses);
//        });

        // 处理直接添加在this.parentContainer之下的UserTask's
        // 根据跟生成逻辑紧密绑定的流程图定义来获取UserTask的前置的前置，也即是waitingTimer
        this.addedActivities.stream().filter(act -> {
            return (act instanceof UserTask);
        }).forEach(activity -> {
            UserTask userTask = (UserTask)bpmnModel.getFlowElement(activity.getId());

            List<SequenceFlow> userTaskIncomingFlows = userTask.getIncomingFlows();
            if(userTaskIncomingFlows.size() != 1) {
                throw new FlowableException("新增的用户任务入向边数不符合要求（=1）。");
            }

            FlowElement maybePGW = userTaskIncomingFlows.get(0).getSourceFlowElement();
            if(!(maybePGW instanceof ParallelGateway)) {
                throw new FlowableException("新增的用户任务的前置节点不是ParallelGateway。");
            }

            List<SequenceFlow> pgwIncomingFlows = ((ParallelGateway) maybePGW).getIncomingFlows();
            if(pgwIncomingFlows.size() != 1) {
                throw new FlowableException("新增的用户任务的前置节点的入向边数不符合要求（=1）。");
            }

            FlowElement maybeWaitingTimer = pgwIncomingFlows.get(0).getSourceFlowElement();
            if(!(maybeWaitingTimer instanceof IntermediateCatchEvent)) {
                throw new FlowableException("新增的用户任务的前置节点的前置节点不是定时器。");
            }

            ExecutionEntity parentExecutionEntity = getCurrentContainerExecutionEntity(
                    commandContext, processInstance, executionEntityManager, userTask.getParentContainer());

            ExecutionEntity userTaskExecutionEntity = executionEntityManager.createChildExecution(parentExecutionEntity);
            userTaskExecutionEntity.setCurrentFlowElement(maybeWaitingTimer);

            Context.getAgenda().planContinueProcessOperation(userTaskExecutionEntity);
        });
    }

    private ExecutionEntity getCurrentContainerExecutionEntity(
            CommandContext commandContext,
            ExecutionEntity processInstance, ExecutionEntityManager executionEntityManager,
            FlowElementsContainer currentContainer) {
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

    private List<SubProcess> getNextLevelSubProcess(FlowElementsContainer currentContainer) {
        List<SubProcess> subProcessList = null;
        List<SubProcess> nextLevelSubProcesses = new ArrayList<>();
        if(currentContainer instanceof SubProcess) {
            subProcessList = ((SubProcess) currentContainer).findAllSubFlowElementInFlowMapOfType(SubProcess.class);
            for(SubProcess aSubProcess : subProcessList) {
                if(aSubProcess.getParentContainer().equals(currentContainer)) {
                    nextLevelSubProcesses.add(aSubProcess);
                    break;
                }
            }
        } else {
            throw new FlowableException("错误数据类型:" + currentContainer.getClass());
        }

        return nextLevelSubProcesses;
    }
}
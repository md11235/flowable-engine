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
import org.flowable.engine.impl.context.Context;
import org.flowable.engine.impl.dynamic.BaseDynamicSubProcessInjectUtil;
import org.flowable.engine.impl.dynamic.DynamicUserTaskBuilder;
import org.flowable.engine.impl.persistence.entity.*;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.util.Arrays;
import java.util.List;

/*
 author: sen.zhang@gmail.com
 */
public class InjectUserTaskInSubProcessInstanceCmd extends AbstractDynamicInjectionCmd implements Command<Void> {

    private final String subProcessActivityId;
    protected String processInstanceId;
    protected DynamicUserTaskBuilder dynamicUserTaskBuilder;

    public InjectUserTaskInSubProcessInstanceCmd(String processInstanceId,
                                                 String subProcessActivityId,
                                                 DynamicUserTaskBuilder dynamicUserTaskBuilder) {
        this.processInstanceId = processInstanceId;
        this.subProcessActivityId = subProcessActivityId;
        this.dynamicUserTaskBuilder = dynamicUserTaskBuilder;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        createDerivedProcessDefinitionForProcessInstance(commandContext, processInstanceId);
        return null;
    }

    private SubProcess findTargetSubProcess(Process process) {
        List<SubProcess> subProcesses = process.findFlowElementsOfType(SubProcess.class, true);

        SubProcess targetSubProcess = null;
        for(SubProcess aSubProcess: subProcesses) {
            if(aSubProcess.getId().equals(this.subProcessActivityId)) {
                targetSubProcess = aSubProcess;
                break;
            }
        }

        if(targetSubProcess == null) {
            throw new FlowableException("无法找到ID为\"" + subProcessActivityId + "\"的子流程。");
        }

        return targetSubProcess;
    }

    // func1
    private void addUserTaskToSubProcess(
            //CommandContext commandContext,
            Process process //,
            //BpmnModel bpmnModel
            ) {
        SubProcess targetSubProcess = findTargetSubProcess(process);

        ParallelGateway fork = targetSubProcess.findFirstSubFlowElementInFlowMapOfType(ParallelGateway.class);

        if(fork == null) {
            throw new FlowableException("无法在ID为\"" + subProcessActivityId + "\"的子流程内找到开始分支。");
        }

        List<ParallelGateway> parGWs = targetSubProcess.findAllSubFlowElementInFlowMapOfType(ParallelGateway.class);
        ParallelGateway join = parGWs.get(parGWs.size()-1);

        UserTask newUserTask = new UserTask();
        if (dynamicUserTaskBuilder.getId() != null) {
            newUserTask.setId(dynamicUserTaskBuilder.getId());
        } else {
            newUserTask.setId(dynamicUserTaskBuilder.nextTaskId(process.getFlowElementMap()));
        }
        dynamicUserTaskBuilder.setDynamicTaskId(newUserTask.getId());
        newUserTask.setName(dynamicUserTaskBuilder.getName());
        newUserTask.setCandidateGroups(Arrays.asList(dynamicUserTaskBuilder.getAssignee().split(",")));
        targetSubProcess.addFlowElement(newUserTask);

        SequenceFlow forkFlow1 = new SequenceFlow(fork.getId(), newUserTask.getId());
        forkFlow1.setId(dynamicUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
        targetSubProcess.addFlowElement(forkFlow1);

        SequenceFlow joinFlow1 = new SequenceFlow(newUserTask.getId(), join.getId());
        joinFlow1.setId(dynamicUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
        targetSubProcess.addFlowElement(joinFlow1);
    }

    @Override
    protected void updateBpmnProcess(CommandContext commandContext, Process process,
            BpmnModel bpmnModel, ProcessDefinitionEntity originalProcessDefinitionEntity, DeploymentEntity newDeploymentEntity) {
// 步骤1：查找process内 def key 为 subProcessDefinitionKey 的SubProcess，
// 1.1 如果找到，设为 targetSubProcess

        // 输入的目标SubProcess的 subProcessFQName 为 "A.B.C.D.E.F.G"；
        // 在FlowableTaskExtraInfo里寻找 activity_name==subProcessFQName；
        // func1 如果找到，那么得到 subProcessDefinitionKey，以及对应的已经存在的 targetSubProcess；
        //     获取 targetSubProcess 里的 startParallelGateway 和 endParallelGateway；
        //     然后新建目标userTask，将其插入到startParallelGateway 和 endParallelGateway之间；
        //     创建 targetSubProcess 的childExecution，关联到userTask
        //
        // 没有找到该 SubProcess，那么寻找 activity_name == "A.B.C.D.E.F" 的 subProcessDefinitionKey 和 targetSubProcess；
        // 如果找到，
        //     func2 在 targetSubProcess 内创建 name=="A.B.C.D.E.F.G"、包含 startEvent、startParGW、endParGW、endEvent 的 SubProcess
        //     调用 func1 插入 userTask
        // 如果没找到，那么寻找 activity_name == "A.B.C.D.E" 的 subProcessDefinitionKey 和 targetSubProcess
        //      如果找到 那么 调用 func2 相继插入 activity_name == "A.B.C.D.E.F" 和 activity_name == "A.B.C.D.E.F.G"
        //       调用 func1 插入 userTask
        //
        addUserTaskToSubProcess(process);

        BaseDynamicSubProcessInjectUtil.processFlowElements(commandContext, process, bpmnModel, originalProcessDefinitionEntity, newDeploymentEntity);
    }

    @Override
    protected void updateExecutions(CommandContext commandContext, ProcessDefinitionEntity processDefinitionEntity, 
            ExecutionEntity processInstance, List<ExecutionEntity> childExecutions) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinitionEntity.getId());

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);

        List<ActivityInstanceEntity> subProcessActivityIE = CommandContextUtil.getActivityInstanceEntityManager(commandContext).findActivityInstancesByActivityId(this.subProcessActivityId);

        ExecutionEntity subProcessExecutionEntity = executionEntityManager.findById(subProcessActivityIE.get(0).getExecutionId());

        ExecutionEntity execution = executionEntityManager.createChildExecution(subProcessExecutionEntity);
        
        UserTask userTask = (UserTask) bpmnModel.getProcessById(processDefinitionEntity.getKey()).getFlowElement(dynamicUserTaskBuilder.getDynamicTaskId(), true);
        execution.setCurrentFlowElement(userTask);

        Context.getAgenda().planContinueProcessOperation(execution);
    }

}
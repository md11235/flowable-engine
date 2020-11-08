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
import org.flowable.engine.impl.dynamic.DqDynamicUserTaskBuilder;
import org.flowable.engine.impl.dynamic.DynamicUserTaskBuilder;
import org.flowable.engine.impl.persistence.entity.*;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/*
 author: sen.zhang@gmail.com
 */
public class DqInjectUserTaskInSubProcessInstanceCmd extends AbstractDynamicInjectionCmd implements Command<Void> {
    public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private final String subProcessActivityId;
    protected String processInstanceId;
    protected DqDynamicUserTaskBuilder dynamicUserTaskBuilder;
    private String firstActivityId;

    public DqInjectUserTaskInSubProcessInstanceCmd(String processInstanceId,
                                                   String subProcessActivityId,
                                                   DqDynamicUserTaskBuilder dynamicUserTaskBuilder) {
        this.processInstanceId = processInstanceId;
        this.subProcessActivityId = subProcessActivityId;
        this.dynamicUserTaskBuilder = dynamicUserTaskBuilder;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        createDerivedProcessDefinitionForProcessInstance(commandContext, processInstanceId);
        return null;
    }

    protected static HttpServiceTask buildReminderTask(String parentProcessUUID, String taskId,
                                                       String parentProcessName, String taskName,
                                                       String notificationContentJson, String reminderStr, String wechatNotificationServiceUrl) {
        HttpServiceTask sendNotificationTask = new HttpServiceTask();
        sendNotificationTask.setId("ID-notificationTask-" + UUID.nameUUIDFromBytes((taskId + parentProcessUUID).getBytes()));
        sendNotificationTask.setName(parentProcessName + "." + taskName + "." + reminderStr);
        sendNotificationTask.setType("http");
        List<FieldExtension> extensions1 = new ArrayList<>();

        FieldExtension httpRequestMethod1 = new FieldExtension();
        httpRequestMethod1.setFieldName("requestMethod");
        httpRequestMethod1.setStringValue("POST");
        extensions1.add(httpRequestMethod1);

        FieldExtension httpRequestUrl1 = new FieldExtension();
        httpRequestUrl1.setFieldName("requestUrl");
        httpRequestUrl1.setStringValue(wechatNotificationServiceUrl);
        extensions1.add(httpRequestUrl1);

        FieldExtension httpRequestHeaders1 = new FieldExtension();
        httpRequestHeaders1.setFieldName("requestHeaders");
        // TODO: change into flowable:expression
        httpRequestHeaders1.setStringValue("Content-Type: application/json");
        extensions1.add(httpRequestHeaders1);

        FieldExtension httpRequestBody1 = new FieldExtension();
        httpRequestBody1.setFieldName("requestBody");
        httpRequestBody1.setStringValue(notificationContentJson);
        extensions1.add(httpRequestBody1);

        sendNotificationTask.setFieldExtensions(extensions1);
        return sendNotificationTask;
    }

    public String buildActivitiesForUserTask(
            FlowElementsContainer parentProcess, String parentProcessUUIDBasedOnFQProcessName,
            String parentProcessName, ParallelGateway innerStartEventParallelGateway, ParallelGateway innerEndEventParallelGateway) {
        String taskId = dynamicUserTaskBuilder.getId();
        String taskName = dynamicUserTaskBuilder.getName();
        String taskStartDateStr = dynamicUserTaskBuilder.getStartDateStr();
        String taskDueDateStr = dynamicUserTaskBuilder.getDueDateStr();
        String _wechatNotificationServiceUrl = dynamicUserTaskBuilder.getWechatNotificationServiceUrl();
        List<String> userRoles = dynamicUserTaskBuilder.getCandidateUserRoles();
        String constructionCandidateGroupsStr = dynamicUserTaskBuilder.getConstructionCandidateGroupsStr();
        List<CustomProperty> customProperties = dynamicUserTaskBuilder.getTaskCustomProperties();
        int taskMeasurementTimeSpan = dynamicUserTaskBuilder.getTaskMeasurementTimeSpan();
        // dynamicUserTaskBuilder.getParentProcessUUID();

        // in order to notify that this task has started.
        IntermediateCatchEvent userTaskWaitingTimer = new IntermediateCatchEvent();
        userTaskWaitingTimer.setId("ID-waitingTimer-" + taskId + parentProcessUUIDBasedOnFQProcessName);

        TimerEventDefinition startEventTED = new TimerEventDefinition();

        startEventTED.setTimeDate(taskStartDateStr);

        userTaskWaitingTimer.addEventDefinition(startEventTED);
        parentProcess.addFlowElement(userTaskWaitingTimer);

        String notificationContent = "{\"role\": \"" + constructionCandidateGroupsStr +"\", \"title\": \"请开始"+ taskName +"的施工\", \"message\": \"谢谢配合\"}";
        HttpServiceTask sendConstructionNotificationTask = buildReminderTask(
                parentProcessUUIDBasedOnFQProcessName, taskId,
                parentProcessName, taskName,
                notificationContent,"开始施工提醒", _wechatNotificationServiceUrl);
        parentProcess.addFlowElement(sendConstructionNotificationTask);

        SequenceFlow flow1 = new SequenceFlow(innerStartEventParallelGateway.getId(), userTaskWaitingTimer.getId());
        parentProcess.addFlowElement(flow1);

        // 执行任务的时间到了之后，并行出去：第一条走用户任务，另一条发送通知
        ParallelGateway waitingTimerOutgoingPGW = new ParallelGateway();
        waitingTimerOutgoingPGW.setId("ID-waitingTimerOutgoingPGW-" + taskId + parentProcessUUIDBasedOnFQProcessName);
        parentProcess.addFlowElement(waitingTimerOutgoingPGW);
        SequenceFlow flow6 = new SequenceFlow(userTaskWaitingTimer.getId(), waitingTimerOutgoingPGW.getId());
        parentProcess.addFlowElement(flow6);

        SequenceFlow flow5 = new SequenceFlow(waitingTimerOutgoingPGW.getId(), sendConstructionNotificationTask.getId());
        parentProcess.addFlowElement(flow5);

        EndEvent constructionNotificationEndEvent = new EndEvent();
        constructionNotificationEndEvent.setId("ID-constructionNotificationEndEvent-" + taskId + parentProcessUUIDBasedOnFQProcessName);
        parentProcess.addFlowElement(constructionNotificationEndEvent);

        SequenceFlow flow2 = new SequenceFlow(sendConstructionNotificationTask.getId(), constructionNotificationEndEvent.getId());
        parentProcess.addFlowElement(flow2);

        // TODO: 每一个新的UserTask，根据其id去查询tb_flw_task_extra_info的task_def_key
        //  如果匹配，则更新信息；如果没有则插入新的纪录。
        UserTask userTask = new UserTask();
        userTask.setId("ID-userTask-" + taskId + parentProcessUUIDBasedOnFQProcessName);
        userTask.setName(((SubProcess)parentProcess).getName() + "."+ taskName);

        userTask.setDueDate(taskDueDateStr);
        userTask.setCandidateGroups(userRoles);

        userTask.setCustomProperties(customProperties);

        parentProcess.addFlowElement(userTask);


        SequenceFlow flow7 = new SequenceFlow(waitingTimerOutgoingPGW.getId(), userTask.getId());
        parentProcess.addFlowElement(flow7);

        if(taskMeasurementTimeSpan >= 0) {
            HttpServiceTask sendMeasurementNotificationTask = new HttpServiceTask();
            sendMeasurementNotificationTask.setId("ID-measurementNotificationTask-" + taskId + parentProcessUUIDBasedOnFQProcessName);
            sendMeasurementNotificationTask.setName(taskName + "." + "实测实量提醒");
            sendMeasurementNotificationTask.setType("http");
            List<FieldExtension> extensions = new ArrayList<>();

            FieldExtension httpRequestMethod = new FieldExtension();
            httpRequestMethod.setFieldName("requestMethod");
            httpRequestMethod.setStringValue("POST");
            extensions.add(httpRequestMethod);

            FieldExtension httpRequestUrl = new FieldExtension();
            httpRequestUrl.setFieldName("requestUrl");
            httpRequestUrl.setStringValue(_wechatNotificationServiceUrl);
            extensions.add(httpRequestUrl);

            FieldExtension httpRequestHeaders = new FieldExtension();
            httpRequestHeaders.setFieldName("requestHeaders");
            // TODO: change into flowable:expression
            httpRequestHeaders.setStringValue("Content-Type: application/json");
            extensions.add(httpRequestHeaders);

            FieldExtension httpRequestBody = new FieldExtension();
            httpRequestBody.setFieldName("requestBody");
            httpRequestBody.setStringValue("{\"role\": \"measurement_worker\", \"days_allocated\": "+taskMeasurementTimeSpan+", \"title\": \"请于"+ taskMeasurementTimeSpan +"天内完成" + taskName + "的实测实量\", \"message\": \"谢谢配合\"}");
            extensions.add(httpRequestBody);

//                FieldExtension httpResultVariablePrefix = new FieldExtension();
//                httpResultVariablePrefix.setFieldName();
//                //  resultVariablePrefix

            sendMeasurementNotificationTask.setFieldExtensions(extensions);
            parentProcess.addFlowElement(sendMeasurementNotificationTask);

            SequenceFlow flow3 = new SequenceFlow(userTask.getId(), sendMeasurementNotificationTask.getId());
            parentProcess.addFlowElement(flow3);

            SequenceFlow flow4 = new SequenceFlow(sendMeasurementNotificationTask.getId(), innerEndEventParallelGateway.getId());
            parentProcess.addFlowElement(flow4);
        }

        String firstActivityId = userTaskWaitingTimer.getId();

        return firstActivityId;
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

        String subProcessDefDescription = "ID为 " + this.subProcessActivityId + " 的子流程的定义";

        List<StartEvent> startEventList = targetSubProcess.findAllSubFlowElementInFlowMapOfType(StartEvent.class);
        if(startEventList.size() != 1) {
            throw new FlowableException(subProcessDefDescription + "包含的StartEvent对象数不等于1。");
        }
        // 不管StartEvent后接的是UserTask还是ParallelGateway，都应该只有一个。
        StartEvent startEvent = startEventList.get(0);
        List<SequenceFlow> startEventOutgoingFlows = startEvent.getOutgoingFlows();
        if(startEventOutgoingFlows.size() != 1) {
            throw new FlowableException(subProcessDefDescription + "包含的StartEvent对象连接的后续活动数量不等于1。");
        }
        FlowElement startEventTargetFlowElement = startEventOutgoingFlows.get(0).getTargetFlowElement();
        if(!(startEventTargetFlowElement instanceof ParallelGateway)) {
            throw new FlowableException(subProcessDefDescription + "在StartEvent对象后的节点不是ParallelGateway。");
        }
        ParallelGateway subProcessInnerStartPGW = (ParallelGateway) startEventTargetFlowElement;

        List<EndEvent> endEventList = targetSubProcess.findAllSubFlowElementInFlowMapOfType(EndEvent.class).stream().filter(
                _endEvent -> {
                    return (_endEvent != null) && (_endEvent.getName() != null) && _endEvent.getName().contains("ID-innerEndEvent-");
                }).collect(Collectors.toList());
        if(endEventList.size() != 1) {
            throw new FlowableException(subProcessDefDescription + "包含的 EndEvent 对象数不等于1。");
        }
        // 不管EndEvent的前置节点是UserTask还是ParallelGateway，都应该只有一个。
        EndEvent endEvent = endEventList.get(0);
        List<SequenceFlow> endEventIncomingFlows = endEvent.getIncomingFlows();
        if(endEventIncomingFlows.size() != 1) {
            throw new FlowableException(subProcessDefDescription + "包含的 EndEvent 对象连接的前置活动数量不等于1。");
        }
        FlowElement endEventSourceFlowElement = endEventIncomingFlows.get(0).getSourceFlowElement();
        if(!(endEventSourceFlowElement instanceof ParallelGateway)) {
            throw new FlowableException(subProcessDefDescription + "在 EndEvent 对象前的节点不是 ParallelGateway。");
        }
        ParallelGateway subProcessInnerEndPGW = (ParallelGateway) endEventSourceFlowElement;

        final String parentProcessUUIDBasedOnFQProcessName = dynamicUserTaskBuilder.getParentProcessUUID();
        final String parentProcessName = targetSubProcess.getName();

        this.firstActivityId = buildActivitiesForUserTask(targetSubProcess, parentProcessUUIDBasedOnFQProcessName,
                parentProcessName, subProcessInnerStartPGW, subProcessInnerEndPGW);

//        ParallelGateway fork = targetSubProcess.findFirstSubFlowElementInFlowMapOfType(ParallelGateway.class);
//
//        if(fork == null) {
//            throw new FlowableException("无法在ID为 " + subProcessActivityId + " 的子流程内找到开始分支。");
//        }
//
//        List<ParallelGateway> parGWs = targetSubProcess.findAllSubFlowElementInFlowMapOfType(ParallelGateway.class);
//        ParallelGateway join = parGWs.get(parGWs.size()-1);
//
//        UserTask newUserTask = new UserTask();
//        if (dynamicUserTaskBuilder.getId() != null) {
//            newUserTask.setId(dynamicUserTaskBuilder.getId());
//        } else {
//            newUserTask.setId(dynamicUserTaskBuilder.nextTaskId(process.getFlowElementMap()));
//        }
//        dynamicUserTaskBuilder.setDynamicTaskId(newUserTask.getId());
//        newUserTask.setName(dynamicUserTaskBuilder.getName());
//        newUserTask.setCandidateGroups(Arrays.asList(dynamicUserTaskBuilder.getAssignee().split(",")));
//        targetSubProcess.addFlowElement(newUserTask);
//
//        SequenceFlow forkFlow1 = new SequenceFlow(fork.getId(), newUserTask.getId());
//        forkFlow1.setId(dynamicUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
//        targetSubProcess.addFlowElement(forkFlow1);
//
//        SequenceFlow joinFlow1 = new SequenceFlow(newUserTask.getId(), join.getId());
//        joinFlow1.setId(dynamicUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
//        targetSubProcess.addFlowElement(joinFlow1);
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
        
        FlowElement nextExecutionTarget = bpmnModel.getProcessById(processDefinitionEntity.getKey()).getFlowElement(this.firstActivityId, true);
        execution.setCurrentFlowElement(nextExecutionTarget);

        Context.getAgenda().planContinueProcessOperation(execution);
    }

}
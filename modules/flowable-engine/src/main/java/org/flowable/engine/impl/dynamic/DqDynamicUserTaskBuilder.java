package org.flowable.engine.impl.dynamic;

import org.flowable.bpmn.model.CustomProperty;

import java.util.List;

public class DqDynamicUserTaskBuilder extends DynamicUserTaskBuilder{
    private String parentProcessUUID;
    private List<CustomProperty> customProperties;
    private int measurementTimeSpan;
    private String startDateStr;
    private String dueDateStr;
    private String wechatNotificationServiceUrl;
    private List<String> candidateUserRoles;

    public List<CustomProperty> getTaskCustomProperties() {
        return customProperties;
    }

    public int getTaskMeasurementTimeSpan() {
        return measurementTimeSpan;
    }

    public String getParentProcessUUID() {
        return parentProcessUUID;
    }

    public void setParentProcessUUID(String parentProcessUUID) {
        this.parentProcessUUID = parentProcessUUID;
    }

    public String getStartDateStr() {
        return startDateStr;
    }

    public void setStartDateStr(String startDateStr) {
        this.startDateStr = startDateStr;
    }

    public String getDueDateStr() {
        return dueDateStr;
    }

    public void setDueDateStr(String dueDateStr) {
        this.dueDateStr = dueDateStr;
    }

    public String getWechatNotificationServiceUrl() {
        return wechatNotificationServiceUrl;
    }

    public void setWechatNotificationServiceUrl(String wechatNotificationServiceUrl) {
        this.wechatNotificationServiceUrl = wechatNotificationServiceUrl;
    }

    public List<String> getCandidateUserRoles() {
        return candidateUserRoles;
    }

    public void setCandidateUserRoles(List<String> candidateUserRoles) {
        this.candidateUserRoles = candidateUserRoles;
    }

    public void setCustomProperties(List<CustomProperty> customProperties) {
        this.customProperties = customProperties;
    }

    public void setMeasurementTimeSpan(int measurementTimeSpan) {
        this.measurementTimeSpan = measurementTimeSpan;
    }
}

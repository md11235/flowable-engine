package org.flowable.engine.business;

import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.engine.impl.dynamic.DqDynamicEmbeddedSubProcessBuilder;

import java.util.List;

public interface ActionToBuildSubProcess {
    List<Activity> build(
            FlowElementsContainer parentProcess,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder);
}


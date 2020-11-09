package org.flowable.engine.business;

import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.engine.impl.dynamic.DqDynamicEmbeddedSubProcessBuilder;
import org.flowable.engine.impl.dynamic.DqDynamicUserTaskBuilder;

public interface ActionToBuildSubProcess {
    String build(
            FlowElementsContainer parentProcess,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder);
}


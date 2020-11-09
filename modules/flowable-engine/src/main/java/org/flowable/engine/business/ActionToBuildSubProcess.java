package org.flowable.engine.business;

import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.engine.impl.dynamic.DqDynamicEmbeddedSubProcessBuilder;

public interface ActionToBuildSubProcess {
    FlowElementsContainer build(
            FlowElementsContainer parentProcess,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder);
}


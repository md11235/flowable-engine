package org.flowable.engine.business;

import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.engine.impl.dynamic.DqDynamicUserTaskBuilder;

public interface ActionToBuildUserTask {
    String build(
            FlowElementsContainer parentProcess,
            String parentProcessUUIDBasedOnFQProcessName,
            String parentProcessName,
            ParallelGateway innerStartEventParallelGateway,
            ParallelGateway innerEndEventParallelGateway,
            DqDynamicUserTaskBuilder dynamicUserTaskBuilder);
}

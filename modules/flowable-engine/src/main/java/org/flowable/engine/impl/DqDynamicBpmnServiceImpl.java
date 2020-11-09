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

package org.flowable.engine.impl;

import org.flowable.engine.DqDynamicBpmnService;
import org.flowable.engine.DynamicBpmnConstants;
import org.flowable.engine.business.ActionToBuildSubProcess;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.cmd.DqInjectEmbeddedSubProcessInProcessInstanceCmd;
import org.flowable.engine.impl.cmd.DqInjectUserTaskInSubProcessInstanceCmd;
import org.flowable.engine.impl.dynamic.DqDynamicEmbeddedSubProcessBuilder;
import org.flowable.engine.impl.dynamic.DqDynamicUserTaskBuilder;

/**
 * @author sen.zhang@gmail.com
 */
public class DqDynamicBpmnServiceImpl extends DynamicBpmnServiceImpl implements DqDynamicBpmnService, DynamicBpmnConstants {

    public DqDynamicBpmnServiceImpl(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    @Override
    public void injectUserTaskInSubProcessInstance(
            String processInstanceId,
            String subProcessActivityId,
            DqDynamicUserTaskBuilder dynamicUserTaskBuilder) {
        commandExecutor.execute(new DqInjectUserTaskInSubProcessInstanceCmd(
                processInstanceId,
                subProcessActivityId,
                dynamicUserTaskBuilder
        ));
    }

    @Override
    public void injectSubProcessInProcessInstance(
            String processInstanceId,
            DqDynamicEmbeddedSubProcessBuilder dynamicEmbeddedSubProcessBuilder,
            ActionToBuildSubProcess actionToBuildSubProcess) {
        commandExecutor.execute(new DqInjectEmbeddedSubProcessInProcessInstanceCmd(
                processInstanceId,
                dynamicEmbeddedSubProcessBuilder,
                actionToBuildSubProcess
        ));
    }
}

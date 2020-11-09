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
package org.flowable.engine.impl.dynamic;

public class DqDynamicEmbeddedSubProcessBuilder extends DynamicEmbeddedSubProcessBuilder {

    // parent container def key
    protected String parentDefKeyOfNewDynamicActivity;
//    protected String dynamicSubProcessFQName;
//    protected String dynamicSubProcessOnlyUUID;
//    protected String startDateStr;
//    protected String baseCompletionDateStr;
//    protected String managementLevelStr;
//    protected String dynamicSubProcessDefKey;

    public DqDynamicEmbeddedSubProcessBuilder() {

    }

//    public String getDynamicSubProcessFQName() {
//        return dynamicSubProcessFQName;
//    }
//
//    public void setDynamicSubProcessFQName(String dynamicSubProcessFQName) {
//        this.dynamicSubProcessFQName = dynamicSubProcessFQName;
//    }
//
//    public String getDynamicSubProcessOnlyUUID() {
//        return dynamicSubProcessOnlyUUID;
//    }
//
//    public void setDynamicSubProcessOnlyUUID(String dynamicSubProcessOnlyUUID) {
//        this.dynamicSubProcessOnlyUUID = dynamicSubProcessOnlyUUID;
//    }
//
//    public String getStartDateStr() {
//        return startDateStr;
//    }
//
//    public void setStartDateStr(String startDateStr) {
//        this.startDateStr = startDateStr;
//    }
//
//    public String getManagementLevelStr() {
//        return managementLevelStr;
//    }
//
//    public void setManagementLevelStr(String managementLevelStr) {
//        this.managementLevelStr = managementLevelStr;
//    }
//
//    public String getBaseCompletionDateStr() {
//        return baseCompletionDateStr;
//    }
//
//    public void setBaseCompletionDateStr(String baseCompletionDateStr) {
//        this.baseCompletionDateStr = baseCompletionDateStr;
//    }

    public String getParentDefKeyOfNewDynamicActivity() {
        return parentDefKeyOfNewDynamicActivity;
    }

    public void setParentDefKeyOfNewDynamicActivity(String parentDefKeyOfNewDynamicActivity) {
        this.parentDefKeyOfNewDynamicActivity = parentDefKeyOfNewDynamicActivity;
    }

    public DqDynamicEmbeddedSubProcessBuilder dynamicSubProcessParentId(String parentDefKey) {
        this.parentDefKeyOfNewDynamicActivity = parentDefKey;
        return this;
    }

//    public DqDynamicEmbeddedSubProcessBuilder dynamicSubProcessFQName(String name) {
//        this.dynamicSubProcessFQName = name;
//        return this;
//    }
//
//    public DqDynamicEmbeddedSubProcessBuilder dynamicSubProcessUUID(String uuid) {
//        this.dynamicSubProcessOnlyUUID = uuid;
//        return this;
//    }
//
//    public DqDynamicEmbeddedSubProcessBuilder startDateStr(String dateStr) {
//        this.startDateStr = dateStr;
//        return this;
//    }
//
//    public DqDynamicEmbeddedSubProcessBuilder baseCompletionDateStr(String dateStr) {
//        this.baseCompletionDateStr = dateStr;
//        return this;
//    }
//
//    public DqDynamicEmbeddedSubProcessBuilder managementLevelStr(String levelStr) {
//        this.managementLevelStr = levelStr;
//        return this;
//    }
//
//    public DqDynamicEmbeddedSubProcessBuilder dynamicSubProcessDefKey(String defKey) {
//        this.dynamicSubProcessDefKey = defKey;
//        return this;
//    }
//
//    public String getDynamicSubProcessDefKey() {
//        return dynamicSubProcessDefKey;
//    }
//
//    public void setDynamicSubProcessDefKey(String dynamicSubProcessDefKey) {
//        this.dynamicSubProcessDefKey = dynamicSubProcessDefKey;
//    }
}
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
package org.flowable.cmmn.converter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.flowable.cmmn.model.Criterion;
import org.flowable.cmmn.model.PlanFragment;
import org.flowable.cmmn.model.PlanItem;
import org.flowable.cmmn.model.PlanItemDefinition;
import org.flowable.cmmn.model.Sentry;
import org.flowable.cmmn.model.SentryOnPart;

public class PlanItemDependencyUtil {

    /**
     * Returns all {@link PlanItem}s that are referenced by the onParts of the entry criteria of this {@link PlanItem}.
     */
    public static List<PlanItem> getEntryDependencies(PlanItem planItem) {
        return getSourcePlanItems(planItem.getEntryCriteria());
    }

    /**
     * Returns all {@link PlanItem}s that are referenced by the onParts of the exit criteria of this {@link PlanItem}.
     */
    public static List<PlanItem> getExitDependencies(PlanItem planItem) {
        return getSourcePlanItems(planItem.getExitCriteria());
    }

    protected static List<PlanItem> getSourcePlanItems(List<Criterion> criteria) {
        List<PlanItem> planItems = new ArrayList<>();
        if (!criteria.isEmpty()) {
            for (Criterion entryCriterion : criteria) {
                Sentry sentry = entryCriterion.getSentry();
                if (sentry.getOnParts() != null && !sentry.getOnParts().isEmpty()) {
                    for (SentryOnPart sentryOnPart : sentry.getOnParts()) {
                        planItems.add(sentryOnPart.getSource());
                    }
                }
            }
        }
        return planItems;
    }

}

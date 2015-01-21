/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.gradle.model.internal.registry;

import org.gradle.api.Action;

import java.util.List;

class RemoveFromBindersThenFire<T> implements Action<RuleBinder<T>> {
    private final List<RuleBinder<?>> binders;
    private final Action<? super RuleBinder<T>> onBind;

    public RemoveFromBindersThenFire(List<RuleBinder<?>> binders, Action<? super RuleBinder<T>> onBind) {
        this.binders = binders;
        this.onBind = onBind;
    }

    public void execute(RuleBinder<T> binder) {
        // Note: if the binder fired immediate (i.e. in it's constructor, then it never made it into the 'binders' collection
        binders.remove(binder);
        onBind.execute(binder);
    }
}

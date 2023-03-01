/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

/**
 * The roles here are all meant to be temporary roles used for migration only, to be removed in Gradle 9.0.
 * <p>
 * While we currently (Gradle 8.x) have to support the legacy behavior of configurations, we want to encode
 * the knowledge of what the minimally required intended behavior is, so that we easily migrate to the
 * {@link #eventualRole} in Gradle 9.0.
 */
public enum ConfigurationRolesForMigration implements ConfigurationRole {
    @Deprecated
    LEGACY_TO_INTENDED_RESOLVABLE_BUCKET(ConfigurationRoles.LEGACY, ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET),
    @Deprecated
    LEGACY_TO_INTENDED_CONSUMABLE(ConfigurationRoles.LEGACY, ConfigurationRoles.INTENDED_CONSUMABLE),

    @Deprecated
    INTENDED_RESOLVABLE_BUCKET_TO_INTENDED_RESOLVABLE(ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET, ConfigurationRoles.INTENDED_RESOLVABLE),
    @Deprecated
    INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE(ConfigurationRoles.INTENDED_CONSUMABLE_BUCKET, ConfigurationRoles.INTENDED_CONSUMABLE);

    private final boolean consumable;
    private final boolean resolvable;
    private final boolean declarableAgainst;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationAgainstDeprecated;

    private final ConfigurationRole initialRole;
    private final ConfigurationRole eventualRole;

    ConfigurationRolesForMigration(ConfigurationRole initialRole, ConfigurationRole eventualRole) {
        this.consumable = initialRole.isConsumable();
        this.resolvable = initialRole.isResolvable();
        this.declarableAgainst = initialRole.isDeclarableAgainst();

        /*
         * For each usage, we'll use the deprecation status from the initial role if it is deprecated.  If it is NOT initially
         * deprecated, but the usage will change from allowed -> disallowed when migrating from the initial role to the
         * eventual role, then we'll also want to mark the usage as deprecated.
         */
        this.consumptionDeprecated = initialRole.isConsumptionDeprecated() || (initialRole.isConsumable() && !eventualRole.isConsumable());
        this.resolutionDeprecated = initialRole.isResolutionDeprecated() || (initialRole.isResolvable() && !eventualRole.isResolvable());
        this.declarationAgainstDeprecated = initialRole.isDeclarationAgainstDeprecated() || (initialRole.isDeclarableAgainst() && !eventualRole.isDeclarableAgainst());

        this.initialRole = initialRole;
        this.eventualRole = eventualRole;
    }

    @Override
    public String getName() {
        return initialRole.getName(); // The display name is the initialRole's name, as this describes the current role
    }

    @Override
    public boolean isConsumable() {
        return consumable;
    }

    @Override
    public boolean isResolvable() {
        return resolvable;
    }

    @Override
    public boolean isDeclarableAgainst() {
        return declarableAgainst;
    }

    @Override
    public boolean isConsumptionDeprecated() {
        return consumptionDeprecated;
    }

    @Override
    public boolean isResolutionDeprecated() {
        return resolutionDeprecated;
    }

    @Override
    public boolean isDeclarationAgainstDeprecated() {
        return declarationAgainstDeprecated;
    }
}

/**
 * Shared kernel used by every module: identifiers, base entities, user context / tenancy, error model, JSON codec.
 * Declared OPEN so its sub-packages are visible to all modules. Keep it small and free of business rules.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.jobsapply.platform.shared;

import org.springframework.modulith.ApplicationModule;

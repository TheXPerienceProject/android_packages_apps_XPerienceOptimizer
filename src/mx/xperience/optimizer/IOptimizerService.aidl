// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer;

/**
 * Internal service that allows privileged system apps to trigger
 * dexopt (ART compilation) on installed packages.
 */
interface IOptimizerService {
    /**
     * @param packageName package to compile
     * @param compilerFilter ART filter, e.g., "speed-profile" or "speed"
     * @return true if the compilation ran successfully
     */
    boolean compilePackage(String packageName, String compilerFilter);
}

package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.sap.cloud.lm.sl.cf.core.security.serialization.SecureSerializationFacade;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.cf.process.util.ModuleDependencyChecker;
import com.sap.cloud.lm.sl.mta.model.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.Module;

@Component("computeNextModulesStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ComputeNextModulesStep extends SyncFlowableStep {

    protected SecureSerializationFacade secureSerializer = new SecureSerializationFacade();

    @Override
    protected StepPhase executeStep(ExecutionWrapper execution) throws Exception {
        getStepLogger().debug(Messages.COMPUTING_NEXT_MODULES_FOR_PARALLEL_ITERATION);
        List<Module> allModulesToDeploy = StepsUtil.getModulesToDeploy(execution.getContext());
        List<Module> completedModules = StepsUtil.getIteratedModulesInParallel(execution.getContext());

        DeploymentDescriptor descriptor = StepsUtil.getDeploymentDescriptor(execution.getContext());
        ModuleDependencyChecker dependencyChecker = new ModuleDependencyChecker(execution.getControllerClient(), descriptor.getModules(),
            allModulesToDeploy, completedModules);

        getStepLogger().debug("Completed modules detected: " + dependencyChecker.getAlreadyDeployedModules());
        getStepLogger().debug("All modules for deploy detected: " + dependencyChecker.getModulesForDeployment());
        getStepLogger().debug("Modules not for deploy detected: " + dependencyChecker.getModulesNotForDeployment());

        // Set next iteration data
        List<Module> modulesForNextIteration = computeApplicationsForNextIteration(allModulesToDeploy, dependencyChecker);
        StepsUtil.setModulesToIterateInParallel(execution.getContext(), modulesForNextIteration);

        // Mark next iteration data as computed
        StepsUtil.setIteratedModulesInParallel(execution.getContext(), ListUtils.union(completedModules, modulesForNextIteration));

        getStepLogger().debug(Messages.COMPUTED_NEXT_MODULES_FOR_PARALLEL_ITERATION, secureSerializer.toJson(modulesForNextIteration));
        return StepPhase.DONE;
    }

    private List<Module> computeApplicationsForNextIteration(List<Module> allModulesToDeploy, ModuleDependencyChecker dependencyChecker) {
        allModulesToDeploy.removeIf(module -> dependencyChecker.getAlreadyDeployedModules()
            .contains(module.getName()));

        return allModulesToDeploy.stream()
            .filter(dependencyChecker::areAllDependenciesSatisfied)
            .collect(Collectors.toList());
    }
}

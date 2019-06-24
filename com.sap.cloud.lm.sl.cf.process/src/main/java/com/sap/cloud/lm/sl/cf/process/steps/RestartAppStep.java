package com.sap.cloud.lm.sl.cf.process.steps;

import com.sap.cloud.lm.sl.cf.core.cf.clients.RecentLogsRetriever;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.StartingInfo;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudApplication.State;
import org.cloudfoundry.client.lib.exception.CloudControllerException;
import org.cloudfoundry.client.lib.exception.CloudOperationException;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

import static java.text.MessageFormat.format;

@Component("restartAppStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RestartAppStep extends TimeoutAsyncFlowableStep {

    @Autowired
    protected RecentLogsRetriever recentLogsRetriever;
    @Autowired
    protected RecentLogsRetriever recentLogsRetrieverFailSafe;
    @Autowired
    protected ApplicationConfiguration configuration;

    @Override
    public StepPhase executeAsyncStep(ExecutionWrapper execution) {
        CloudApplication app = getAppToRestart(execution.getContext());
        try {
            restartApp(execution, app);
        } catch (CloudOperationException coe) {
            CloudControllerException e = new CloudControllerException(coe);
            onError(format(Messages.ERROR_STARTING_APP_1, app.getName()), e);
            throw e;
        }
        return StepPhase.POLL;
    }

    protected CloudApplication getAppToRestart(DelegateExecution context) {
        return StepsUtil.getApp(context);
    }

    private void restartApp(ExecutionWrapper execution, CloudApplication app) {
        CloudControllerClient client = execution.getControllerClient();

        if (isStarted(client, app.getName())) {
            stopApp(client, app);
        }
        StartingInfo startingInfo = startApp(client, app);
        setStartupPollingInfo(execution.getContext(), startingInfo);
    }

    private void setStartupPollingInfo(DelegateExecution context, StartingInfo startingInfo) {
        StepsUtil.setStartingInfo(context, startingInfo);
        if (context.getVariable(Constants.VAR_START_TIME) == null) {
            context.setVariable(Constants.VAR_START_TIME, System.currentTimeMillis());
        }
        if (context.getVariable(Constants.VAR_OFFSET) == null) {
            context.setVariable(Constants.VAR_OFFSET, 0);
        }
    }

    private boolean isStarted(CloudControllerClient client, String appName) {
        try {
            CloudApplication app = client.getApplication(appName);
            return app.getState()
                .equals(State.STARTED);
        } catch (CloudOperationException e) {
            if (e.getStatusCode()
                .equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
                logger.warn(e.getMessage(), e);
                return false;
            }
            throw e;
        }
    }

    private void stopApp(CloudControllerClient client, CloudApplication app) {
        getStepLogger().info(Messages.STOPPING_APP, app.getName());
        client.stopApplication(app.getName());
    }

    private StartingInfo startApp(CloudControllerClient client, CloudApplication app) {
        getStepLogger().info(Messages.STARTING_APP, app.getName());
        return client.startApplication(app.getName());
    }

    protected void onError(String message, Exception e) {
        getStepLogger().error(e, message);
    }

    @Override
    protected List<AsyncExecution> getAsyncStepExecutions(ExecutionWrapper execution) {
        List<AsyncExecution> stepExecutions = new LinkedList<>();

        recentLogsRetrieverFailSafe.setFailSafe(true);
        stepExecutions.add(new PollStartAppStatusExecution(recentLogsRetrieverFailSafe));
        stepExecutions.add(new PollExecuteAppStatusExecution(recentLogsRetriever));
        return stepExecutions;
    }

    @Override
    public Integer getTimeout(DelegateExecution context) {
        return StepsUtil.getInteger(context, Constants.PARAM_START_TIMEOUT, Constants.DEFAULT_START_TIMEOUT);
    }

}

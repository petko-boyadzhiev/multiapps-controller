package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.domain.CloudEvent;

import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudServiceExtended;
import com.sap.cloud.lm.sl.cf.core.cf.clients.EventsGetter;
import com.sap.cloud.lm.sl.cf.core.cf.services.ServiceOperation;
import com.sap.cloud.lm.sl.cf.core.cf.services.ServiceOperationState;
import com.sap.cloud.lm.sl.cf.core.cf.services.ServiceOperationType;

public class PollServiceDeleteOperationsExecution extends PollServiceOperationsExecution implements AsyncExecution {

    private EventsGetter eventsGetter;

    public PollServiceDeleteOperationsExecution(EventsGetter eventsGetter) {
        this.eventsGetter = eventsGetter;
    }

    @Override
    protected List<CloudServiceExtended> computeServicesToPoll(ExecutionWrapper execution,
        Map<String, ServiceOperationType> triggeredServiceOperations) {
        Map<String, CloudServiceExtended> servicesToDeleteData = getServicesData(execution);
        return getServicesWithTriggeredOperations(servicesToDeleteData.values(), triggeredServiceOperations);
    }

    private Map<String, CloudServiceExtended> getServicesData(ExecutionWrapper execution) {
        return StepsUtil.getServicesData(execution.getContext());
    }

    @Override
    protected ServiceOperation getLastServiceOperation(ExecutionWrapper execution, CloudControllerClient client,
        CloudServiceExtended service) {
        return getLastDeleteServiceOperation(execution, service);
    }

    private ServiceOperation getLastDeleteServiceOperation(ExecutionWrapper execution, CloudServiceExtended service) {
        if (service.getMetadata() == null) {
            return null;
        }
        boolean isServiceDeleted = isServiceDeleted(execution, service.getMetadata()
            .getGuid());
        ServiceOperationState operationState = isServiceDeleted ? ServiceOperationState.SUCCEEDED : ServiceOperationState.IN_PROGRESS;
        return new ServiceOperation(ServiceOperationType.DELETE, ServiceOperationType.DELETE.name(), operationState);
    }

    private boolean isServiceDeleted(ExecutionWrapper execution, UUID uuid) {
        List<CloudEvent> serviceEvent = eventsGetter.getEvents(uuid, execution.getControllerClient());
        return serviceEvent.stream()
            .filter(Objects::nonNull)
            .anyMatch(e -> eventsGetter.isDeleteEvent(e.getType()));
    }
}

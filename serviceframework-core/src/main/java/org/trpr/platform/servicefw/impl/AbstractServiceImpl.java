/*
 * Copyright 2012-2015, the original author or authors.
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

package org.trpr.platform.servicefw.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;

import org.trpr.platform.service.model.common.error.BusinessEntityErrorDetail;
import org.trpr.platform.service.model.common.error.ErrorDetail.ErrorBlock;
import org.trpr.platform.service.model.common.error.ErrorSummary;
import org.trpr.platform.service.model.common.error.ServiceRequestErrorDetail;
import org.trpr.platform.service.model.common.event.ServiceAlert;
import org.trpr.platform.service.model.common.event.ServiceEvent;
import org.trpr.platform.service.model.common.platformservicerequest.PlatformServiceRequest;
import org.trpr.platform.service.model.common.platformserviceresponse.PlatformServiceResponse;
import org.trpr.platform.service.model.common.status.Status;
import org.trpr.platform.servicefw.ServiceContext;
import org.trpr.platform.servicefw.common.ServiceException;
import org.trpr.platform.servicefw.common.ServiceFrameworkConstants;
import org.trpr.platform.servicefw.spi.Header;
import org.trpr.platform.servicefw.spi.Service;
import org.trpr.platform.servicefw.spi.ServiceRequest;
import org.trpr.platform.servicefw.spi.ServiceResponse;
import org.trpr.platform.spi.task.Task;
import org.trpr.platform.spi.task.TaskContext;
import org.trpr.platform.spi.task.TaskManager;

/**
 * <code>AbstractServiceImpl<code> is an implementation of the {@link Service} interface that provides common behavior for all services.
 * This implementation implements a template for service request processing. It uses a {@link TaskManager} implementation to process the service
 * request as a number of {@link Task} instances executed sequentially.
 * 
 * @author Regunath B
 * @version 1.0, 14/08/2012
 *
 */
public abstract class AbstractServiceImpl<T extends PlatformServiceRequest, S extends PlatformServiceResponse> implements Service<T, S> {

	/** Constants for string values used locally in this class	 */
	private static final String UNDERSCORE = "_";
	private static final String SERVICE_INVOCATION_TIMESTAMP = "serviceInvocationTimestamp";

	/** The TaskManager to use for Task execution*/
	@SuppressWarnings("rawtypes")
	private TaskManager taskManager;

	/** The ServiceContext to use for all interactions with the ServiceContainer*/
	@SuppressWarnings("rawtypes")
	private ServiceContext serviceContext;
	
	/**
	 * Helper method to populate a ServiceResponse using the specified parameters
	 */
	public static PlatformServiceResponse generatePlatformServiceResponse(
			int statusCode, int errorCode, String errorMessage,
			PlatformServiceRequest platformServiceRequest,
			PlatformServiceResponse platformServiceResponse,
			BusinessEntityErrorDetail businessEntityErrorDetail) {
		Status status = new Status();
		if (statusCode == ServiceFrameworkConstants.SUCCESS_STATUS_CODE) {
			status.setCode(ServiceFrameworkConstants.SUCCESS_STATUS_CODE);
			status.setMessage(ServiceFrameworkConstants.SUCCESS_STATUS_MESSAGE);
			platformServiceResponse.setStatus(status);
		} else if (statusCode == ServiceFrameworkConstants.FAILURE_STATUS_CODE) {
			ErrorSummary errorSummary = new ErrorSummary();
			status.setCode(ServiceFrameworkConstants.FAILURE_STATUS_CODE);
			status.setMessage(ServiceFrameworkConstants.FAILURE_STATUS_MESSAGE);
			platformServiceResponse.setStatus(status);
			platformServiceResponse.setPlatformServiceRequest(platformServiceRequest);
			if (businessEntityErrorDetail != null) {
				errorSummary.getBusinessEntityErrorDetail().add(businessEntityErrorDetail);
			} else {
				ErrorBlock errorBlock = getErrorBlock(errorCode, null,
						errorMessage);
				ServiceRequestErrorDetail serviceRequestErrorDetail = new ServiceRequestErrorDetail();
				serviceRequestErrorDetail.getErrorBlock().add(errorBlock);
				errorSummary.getServiceRequestErrorDetail().add(serviceRequestErrorDetail);
			}
			platformServiceResponse.setErrorSummary(errorSummary);
		}
		return platformServiceResponse;
	}

	/** Helper method to create an ErrorBlock using specified parameters */
	public static ErrorBlock getErrorBlock(int errorCode, String fieldName,
			String errorMessage) {
		ErrorBlock errorBlock = new ErrorBlock();
		errorBlock.setErrorCode(errorCode);
		errorBlock.setFieldName(fieldName);
		errorBlock.setContent(errorMessage);
		return errorBlock;
	}
	
	/** Getter/Setter methods */
	@SuppressWarnings("rawtypes")
	public TaskManager getTaskManager() {
		return this.taskManager;
	}
	@SuppressWarnings("rawtypes")
	public void setTaskManager(TaskManager taskManager) {
		this.taskManager = taskManager;
	}
	@SuppressWarnings("rawtypes")
	public ServiceContext getServiceContext() {
		return this.serviceContext;
	}
	@SuppressWarnings("rawtypes")
	public void setServiceContext(ServiceContext serviceContext) {
		this.serviceContext = serviceContext;
	}
	/** End Getter/Setter methods*/
	
	/**
	 * The method is called by clients to the service to get a response. This
	 * helps prevent direct calls to the service without a security context from
	 * any client. Publishes {@link ServiceInvocationEvent} for each service
	 * invoked.
	 *
	 * @return ServiceResponse The response for the current request.
	 *
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ServiceResponse<S> processRequest(ServiceRequest<T> request) throws ServiceException {
		
		// add the service request time-stamp as a header
		Header[] headers = new Header[] {new Header(SERVICE_INVOCATION_TIMESTAMP, String.valueOf(System.currentTimeMillis()))};
		((ServiceRequestImpl<T>)request).addHeaders(headers);
		
		// signal start of execution to the service context
		this.serviceContext.notifyServiceExecutionStart(request);
		
		ServiceResponse<S> serviceResponse = null;

		// Obtain all the Tasks configured within the Service and execute all the tasks
		TaskContext taskContext = executeTasks(getAllTasks(request));

		// Prepare the service response from the task context and request.
        serviceResponse = prepareServiceResponse(taskContext, request);

		// signal end of execution to the service context
		this.serviceContext.notifyServiceExecutionEnd(request, serviceResponse, Long.valueOf(request.getHeaderByKey(SERVICE_INVOCATION_TIMESTAMP).getValue()), System.currentTimeMillis());

        return serviceResponse;
	}
	
	

	/**
	 * Helper method to publish an event using the publisher configured.
	 */
	protected void publishEvent(ServiceEvent event, ServiceRequest<T> request){
		try {
	        event.setServiceKey(request.getServiceName()+ UNDERSCORE +request.getServiceVersion());
			event.setHostName(InetAddress.getLocalHost().getHostName());
			if (event.getStageStarttime() == null) {
				event.setStageStarttime(Calendar.getInstance());
			}
			if (event.getStageEndtime() == null) {
				event.setStageEndtime(Calendar.getInstance());
			}
		} catch (UnknownHostException e) {
			//ignore the exception
		}
		this.serviceContext.publishEvent(event);
	}
	
	/**
	 * Helper method to publish an alerts using the publisher configured.
	 */
	protected void publishAlert(ServiceAlert alert, ServiceRequest<T> request){
		try {
	        alert.setServiceKey(request.getServiceName()+ UNDERSCORE +request.getServiceVersion());
			alert.setHostName(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			//ignore the exception
		}
		this.serviceContext.publishAlert(alert);
	}

	/**
	 * This is an abstract method to obtain all the tasks.
	 * Individual Service implementations sub-classing the AbstractServiceImpl should provide
	 * proper implementations to return necessary Task
	 *
	 * @param request
	 */
	@SuppressWarnings("rawtypes")
	protected abstract Task[] getAllTasks(ServiceRequest<T> request);

	/**
	 * This is an abstract method. Each service sub-classing should provide the implementation that prepares
	 * the ServiceResponse with the TaskContext returned after execution of the tasks
	 *
	 * @param taskContext
	 * @param serviceRequest
	 */
	@SuppressWarnings("rawtypes")
	protected abstract ServiceResponse<S> prepareServiceResponse(TaskContext taskContext, ServiceRequest<T> serviceRequest);

	/**
	 * Execute the set of tasks by calling TaskManager
	 *
	 * @param tasks
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private TaskContext executeTasks(Task[] tasks){
		TaskContext taskContext = getTaskManager().execute(tasks);
		return taskContext;
	}
		
}

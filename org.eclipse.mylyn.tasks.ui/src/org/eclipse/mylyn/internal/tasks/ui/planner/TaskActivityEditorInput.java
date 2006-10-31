/*******************************************************************************
 * Copyright (c) 2004 - 2006 University Of British Columbia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     University Of British Columbia - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.internal.tasks.ui.planner;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mylar.context.core.MylarStatusHandler;
import org.eclipse.mylar.tasks.core.ITask;
import org.eclipse.mylar.tasks.core.ITaskListElement;
import org.eclipse.mylar.tasks.core.TaskList;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

/**
 * @author Ken Sueda
 * @author Mik Kersten
 */
public class TaskActivityEditorInput implements IEditorInput {

	private static final String TASK_ACTIVITY_REPORT = "Task Activity Report";

	private Set<ITask> completedTasks = new HashSet<ITask>();

	private Set<ITask> inProgressTasks = new HashSet<ITask>();
	
	private Set<ITask> plannedTasks = new HashSet<ITask>();

	private TaskReportGenerator taskReportGenerator = null;

	// private int prevDaysToReport = -1;

	private Date reportStartDate = null;

	public TaskActivityEditorInput(Date reportStartDate, Set<ITaskListElement> chosenCategories, TaskList tlist) {
		this.reportStartDate = reportStartDate;
		taskReportGenerator = new TaskReportGenerator(tlist, chosenCategories);

		ITaskCollector completedTaskCollector = new CompletedTaskCollector(reportStartDate);
		taskReportGenerator.addCollector(completedTaskCollector);

		ITaskCollector inProgressTaskCollector = new InProgressTaskCollector(reportStartDate);
		taskReportGenerator.addCollector(inProgressTaskCollector);

		try {
			IProgressService service = PlatformUI.getWorkbench().getProgressService();
			service.run(false, true, taskReportGenerator);
			while (!taskReportGenerator.isFinished())
				Thread.sleep(500);
		} catch (InvocationTargetException e) {
			// operation was canceled
		} catch (InterruptedException e) {
			MylarStatusHandler.log(e, "Could not generate report");
		}

		completedTasks = completedTaskCollector.getTasks();
		inProgressTasks = inProgressTaskCollector.getTasks();
		
		plannedTasks.addAll(TasksUiPlugin.getTaskListManager().getActivityNextWeek().getChildren());
		
		plannedTasks.addAll(TasksUiPlugin.getTaskListManager().getActivityFuture().getChildren());
				
		//plannedTasks = new HashSet<ITask>();
	}

	public boolean exists() {
		return true;
	}

	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	public String getName() {
		return TASK_ACTIVITY_REPORT;
	}

	public IPersistableElement getPersistable() {
		return null;
	}

	public String getToolTipText() {
		return "Task Planner";
	}

	@SuppressWarnings("unchecked")
	public Object getAdapter(Class adapter) {
		return null;
	}

	public Set<ITask> getCompletedTasks() {
		return completedTasks;
	}

	public Set<ITask> getInProgressTasks() {
		return inProgressTasks;
	}
	
	public Set<ITask> getPlannedTasks() {
		return plannedTasks;
	}

	public long getTotalTimeSpentOnCompletedTasks() {
		long duration = 0;
		for (ITask t : completedTasks) {
			duration += TasksUiPlugin.getTaskListManager().getElapsedTime(t);
		}
		return duration;
	}

	public long getTotalTimeSpentOnInProgressTasks() {
		long duration = 0;
		for (ITask t : inProgressTasks) {
			duration += TasksUiPlugin.getTaskListManager().getElapsedTime(t);
		}
		return duration;
	}

	public TaskReportGenerator getReportGenerator() {
		return taskReportGenerator;
	}

	public boolean createdDuringReportPeriod(ITask task) {
		Date creationDate = task.getCreationDate();
		if (creationDate != null) {
			return creationDate.compareTo(reportStartDate) > 0;
		} else {
			return false;
		}
	}

	public Date getReportStartDate() {
		return reportStartDate;
	}

	public int getTotalTimeEstimated() {
		int duration = 0;
		for (ITask task : inProgressTasks) {
			duration += task.getEstimateTimeHours();
		}
		return duration;
	}
	
	public void removeCompletedTask( ITask task) {
		completedTasks.remove(task);				
	}
	
	public void removeInProgressTask(ITask task) {
		inProgressTasks.remove(task);
	}
	
	public void addPlannedTask(ITask task) {		
		plannedTasks.add(task);		
	}
	
	public void removePlannedTask(ITask task) {
		plannedTasks.remove(task);
	}
	
	public int getPlannedEstimate() {
		int estimated = 0;
		for (ITask task : plannedTasks) {
			estimated += task.getEstimateTimeHours();
		}
		return estimated;
	}

}

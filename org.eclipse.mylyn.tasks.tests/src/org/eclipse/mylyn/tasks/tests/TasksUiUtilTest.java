/*******************************************************************************
 * Copyright (c) 2004, 2008 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.tasks.tests;

import java.lang.reflect.Field;
import java.util.Date;

import junit.framework.TestCase;

import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.core.TaskCategory;
import org.eclipse.mylyn.internal.tasks.core.TaskList;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.mylyn.tasks.core.ITask.PriorityLevel;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.browser.WebBrowserEditor;
import org.eclipse.ui.internal.browser.WebBrowserEditorInput;

/**
 * @author Shawn Minto
 */
public class TasksUiUtilTest extends TestCase {

	private TaskCategory cat1;

	private AbstractTask cat1task1;

	private AbstractTask cat1task2;

	private IWorkbenchPage activePage;

	private TaskList taskList;

	@Override
	public void setUp() throws Exception {
		taskList = TasksUiPlugin.getTaskList();

		TaskTestUtil.resetTaskListAndRepositories();

		cat1 = new TaskCategory("First Category");
		taskList.addCategory(cat1);

		cat1task1 = TasksUiInternal.createNewLocalTask("task 1");
		cat1task1.setPriority(PriorityLevel.P1.toString());
		cat1task1.setCompletionDate(new Date());
		taskList.addTask(cat1task1, cat1);

		cat1task2 = TasksUiInternal.createNewLocalTask("task 2");
		cat1task2.setPriority(PriorityLevel.P2.toString());
		taskList.addTask(cat1task2, cat1);

		assertEquals(cat1.getChildren().size(), 2);

		activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		activePage.closeAllEditors(false);
		assertEquals(0, activePage.getEditorReferences().length);
	}

	@Override
	public void tearDown() throws Exception {
		TaskTestUtil.resetTaskListAndRepositories();
		activePage.closeAllEditors(false);
	}

	public void testOpenTaskFromTask() {
		TasksUiUtil.openTask(cat1task1);
		assertEquals(1, activePage.getEditorReferences().length);
		assertTrue(activePage.getEditorReferences()[0].getEditor(true) instanceof TaskEditor);

		TasksUiUtil.openTask(cat1task2);
		assertEquals(2, activePage.getEditorReferences().length);
		assertTrue(activePage.getEditorReferences()[0].getEditor(true) instanceof TaskEditor);
		assertTrue(activePage.getEditorReferences()[1].getEditor(true) instanceof TaskEditor);
	}

	public void testOpenTaskFromString() {
		TasksUiUtil.openTask((String) null);
		assertEquals(1, activePage.getEditorReferences().length);
		assertTrue(activePage.getEditorReferences()[0].getEditor(true) instanceof WebBrowserEditor);
	}

	public void testOpenUrl() {
		TasksUiUtil.openUrl(null);
		assertEquals(1, activePage.getEditorReferences().length);
		IEditorPart editor = activePage.getEditorReferences()[0].getEditor(true);
		assertTrue(editor instanceof WebBrowserEditor);
		assertTrue(editor.getEditorInput() instanceof WebBrowserEditorInput);
		assertEquals(null, ((WebBrowserEditorInput) editor.getEditorInput()).getURL());

		TasksUiUtil.openUrl("http://eclipse.org/mylyn");
		assertEquals(2, activePage.getEditorReferences().length);
		editor = activePage.getEditorReferences()[0].getEditor(true);
		assertTrue(editor instanceof WebBrowserEditor);
		assertEquals(null, ((WebBrowserEditorInput) editor.getEditorInput()).getURL());

		IEditorPart editor2 = activePage.getEditorReferences()[1].getEditor(true);
		assertTrue(editor2 instanceof WebBrowserEditor);
		assertNotNull(((WebBrowserEditorInput) editor2.getEditorInput()).getURL());
		assertEquals("http://eclipse.org/mylyn", ((WebBrowserEditorInput) editor2.getEditorInput()).getURL().toString());
	}

	public void testFlagNoRichEditor() throws SecurityException, NoSuchFieldException, IllegalArgumentException,
			IllegalAccessException {
		TasksUiUtil.openUrl(null);
		assertEquals(1, activePage.getEditorReferences().length);
		IEditorPart editor = activePage.getEditorReferences()[0].getEditor(true);
		assertTrue(editor instanceof WebBrowserEditor);
		assertTrue(editor.getEditorInput() instanceof WebBrowserEditorInput);
		assertEquals(null, ((WebBrowserEditorInput) editor.getEditorInput()).getURL());
		WebBrowserEditorInput input = ((WebBrowserEditorInput) editor.getEditorInput());
		Field f = input.getClass().getDeclaredField("style");
		f.setAccessible(true);
		int style = (Integer) f.get(input);
		assertFalse((style & TasksUiUtil.FLAG_NO_RICH_EDITOR) == 0);

		TasksUiUtil.openUrl("http://eclipse.org/mylyn");
		assertEquals(2, activePage.getEditorReferences().length);
		editor = activePage.getEditorReferences()[0].getEditor(true);
		assertTrue(editor instanceof WebBrowserEditor);
		assertEquals(null, ((WebBrowserEditorInput) editor.getEditorInput()).getURL());
		input = ((WebBrowserEditorInput) editor.getEditorInput());
		f = input.getClass().getDeclaredField("style");
		f.setAccessible(true);
		style = (Integer) f.get(input);
		assertFalse((style & TasksUiUtil.FLAG_NO_RICH_EDITOR) == 0);

		IEditorPart editor2 = activePage.getEditorReferences()[1].getEditor(true);
		assertTrue(editor2 instanceof WebBrowserEditor);
		assertNotNull(((WebBrowserEditorInput) editor2.getEditorInput()).getURL());
		assertEquals("http://eclipse.org/mylyn", ((WebBrowserEditorInput) editor2.getEditorInput()).getURL().toString());
		input = ((WebBrowserEditorInput) editor.getEditorInput());
		f = input.getClass().getDeclaredField("style");
		f.setAccessible(true);
		style = (Integer) f.get(input);
		assertFalse((style & TasksUiUtil.FLAG_NO_RICH_EDITOR) == 0);

		// open task should not set FLAG_NO_RICH_EDITOR
		TasksUiUtil.openTask("http://eclipse.org/mylyn/test");
		assertEquals(3, activePage.getEditorReferences().length);
		editor = activePage.getEditorReferences()[2].getEditor(true);
		assertTrue(editor instanceof WebBrowserEditor);
		assertEquals("http://eclipse.org/mylyn/test", ((WebBrowserEditorInput) editor.getEditorInput()).getURL()
				.toString());
		input = ((WebBrowserEditorInput) editor.getEditorInput());
		f = input.getClass().getDeclaredField("style");
		f.setAccessible(true);
		style = (Integer) f.get(input);
		assertTrue((style & TasksUiUtil.FLAG_NO_RICH_EDITOR) == 0);
	}
}
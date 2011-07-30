/*******************************************************************************
 * Copyright (c) 2004, 2010 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.bugzilla.ui.editor;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaAttribute;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaCorePlugin;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaCustomField;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaOperation;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaRepositoryConnector;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaRepositoryResponse;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaStatus;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaTaskDataHandler;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaVersion;
import org.eclipse.mylyn.internal.bugzilla.core.IBugzillaConstants;
import org.eclipse.mylyn.internal.bugzilla.core.RepositoryConfiguration;
import org.eclipse.mylyn.internal.bugzilla.ui.BugzillaUiPlugin;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.mylyn.internal.tasks.ui.PersonProposalProvider;
import org.eclipse.mylyn.internal.tasks.ui.editors.PersonAttributeEditor;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorActionPart;
import org.eclipse.mylyn.tasks.core.RepositoryResponse;
import org.eclipse.mylyn.tasks.core.RepositoryStatus;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMetaData;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataModel;
import org.eclipse.mylyn.tasks.core.data.TaskDataModelEvent;
import org.eclipse.mylyn.tasks.core.data.TaskDataModelListener;
import org.eclipse.mylyn.tasks.core.sync.SubmitJobEvent;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.editors.AbstractAttributeEditor;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.mylyn.tasks.ui.editors.AttributeEditorFactory;
import org.eclipse.mylyn.tasks.ui.editors.LayoutHint;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorInput;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.fieldassist.ContentAssistCommandAdapter;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.keys.IBindingService;

/**
 * @author Rob Elves
 * @since 3.0
 */
public class BugzillaTaskEditorPage extends AbstractTaskEditorPage {

	public static final String ID_PART_BUGZILLA_PLANNING = "org.eclipse.mylyn.bugzilla.ui.editors.part.planning"; //$NON-NLS-1$

	public static final String ID_PART_BUGZILLA_FLAGS = "org.eclipse.mylyn.bugzilla.ui.editors.part.flags"; //$NON-NLS-1$

	public static final String PATH_FLAGS = "flags"; //$NON-NLS-1$

	private final Map<TaskAttribute, AbstractAttributeEditor> attributeEditorMap;

	private TaskDataModelListener productListener;

	private final List<ControlDecoration> errorDecorations = new ArrayList<ControlDecoration>();

	private final List<PersonAttributeEditor> editorsWithError = new ArrayList<PersonAttributeEditor>(3);

	private IHandlerActivation contentAssistHandlerActivation;

	public BugzillaTaskEditorPage(TaskEditor editor) {
		this(editor, BugzillaCorePlugin.CONNECTOR_KIND);
	}

	/**
	 * Call this constructor if extending the Bugzilla connector
	 * 
	 * @param editor
	 * @param connectorKind
	 */
	public BugzillaTaskEditorPage(TaskEditor editor, String connectorKind) {
		super(editor, connectorKind);
		this.attributeEditorMap = new HashMap<TaskAttribute, AbstractAttributeEditor>();
		setNeedsPrivateSection(true);
		setNeedsSubmitButton(true);
	}

	@Override
	protected Set<TaskEditorPartDescriptor> createPartDescriptors() {
		Set<TaskEditorPartDescriptor> descriptors = super.createPartDescriptors();
		boolean hasPartComments = false;
		boolean hasPartNewComment = false;
		// remove unnecessary default editor parts
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_PEOPLE)) {
				descriptors.remove(taskEditorPartDescriptor);
				break;
			}
		}
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_COMMENTS)) {
				descriptors.remove(taskEditorPartDescriptor);
				hasPartComments = true;
				break;
			}
		}
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_NEW_COMMENT)) {
				descriptors.remove(taskEditorPartDescriptor);
				hasPartNewComment = true;
				break;
			}
		}

		// Add Bugzilla Planning part
		try {
			TaskData data = TasksUi.getTaskDataManager().getTaskData(getTask());
			if (data != null) {
				// Add Bugzilla Flag part
				Map<String, TaskAttribute> attributes = data.getRoot().getAttributes();
				for (TaskAttribute attribute : attributes.values()) {
					if (BugzillaAttribute.KIND_FLAG.equals(attribute.getMetaData().getKind())) {
						descriptors.add(new TaskEditorPartDescriptor(ID_PART_BUGZILLA_FLAGS) {
							@Override
							public AbstractTaskEditorPart createPart() {
								return new BugzillaFlagPart();
							}
						}.setPath(ID_PART_ATTRIBUTES + "/" + PATH_FLAGS)); //$NON-NLS-1$
						break;
					}
				}

				TaskAttribute attrEstimatedTime = data.getRoot().getMappedAttribute(
						BugzillaAttribute.ESTIMATED_TIME.getKey());
				if (attrEstimatedTime != null) {
					descriptors.add(new TaskEditorPartDescriptor(ID_PART_BUGZILLA_PLANNING) {
						@Override
						public AbstractTaskEditorPart createPart() {
							return new BugzillaPlanningEditorPart();
						}
					}.setPath(ID_PART_ATTRIBUTES + "/" + PATH_PLANNING)); //$NON-NLS-1$
				}
			}
			if (hasPartComments) {
				descriptors.add(new TaskEditorPartDescriptor(ID_PART_COMMENTS) {
					@Override
					public AbstractTaskEditorPart createPart() {
						return new BugzillaTaskEditorCommentPart();
					}
				}.setPath(PATH_COMMENTS));

			}
		} catch (CoreException e) {
			// ignore
		}

		if (hasPartNewComment) {
			// Add the updated Bugzilla new comment part
			descriptors.add(new TaskEditorPartDescriptor(ID_PART_NEW_COMMENT) {
				@Override
				public AbstractTaskEditorPart createPart() {
					return new BugzillaTaskEditorNewCommentPart();
				}
			}.setPath(PATH_COMMENTS));
		}
		// Add the updated Bugzilla people part
		descriptors.add(new TaskEditorPartDescriptor(ID_PART_PEOPLE) {
			@Override
			public AbstractTaskEditorPart createPart() {
				return new BugzillaPeoplePart();
			}
		}.setPath(PATH_PEOPLE));

		return descriptors;
	}

	@Override
	protected AttributeEditorFactory createAttributeEditorFactory() {
		AttributeEditorFactory factory = new AttributeEditorFactory(getModel(), getTaskRepository(), getEditorSite()) {
			@Override
			public AbstractAttributeEditor createEditor(String type, final TaskAttribute taskAttribute) {
				AbstractAttributeEditor editor;
				if (IBugzillaConstants.EDITOR_TYPE_KEYWORDS.equals(type)) {
					editor = new BugzillaKeywordAttributeEditor(getModel(), taskAttribute);
				} else if (IBugzillaConstants.EDITOR_TYPE_REMOVECC.equals(type)) {
					editor = new BugzillaCcAttributeEditor(getModel(), taskAttribute);
				} else if (IBugzillaConstants.EDITOR_TYPE_VOTES.equals(type)) {
					editor = new BugzillaVotesEditor(getModel(), taskAttribute);
				} else if (IBugzillaConstants.EDITOR_TYPE_FLAG.equals(type)) {
					editor = new FlagAttributeEditor(getModel(), taskAttribute);
				} else {
					editor = super.createEditor(type, taskAttribute);
					if (TaskAttribute.TYPE_BOOLEAN.equals(type)) {
						editor.setDecorationEnabled(false);
					}
				}

				if (editor != null && taskAttribute.getId().startsWith(BugzillaCustomField.CUSTOM_FIELD_PREFIX)) {
					editor.setLayoutHint(new LayoutHint(editor.getLayoutHint()) {

						@Override
						public int getPriority() {
							return super.getPriority() * 10;
						}
					});
				}

				TaskAttributeMetaData properties = taskAttribute.getMetaData();
				if (editor != null && IBugzillaConstants.EDITOR_TYPE_FLAG.equals(properties.getType())) {
					editor.setLayoutHint(new LayoutHint(editor.getLayoutHint()) {

						@Override
						public int getPriority() {
							return super.getPriority() * 5;
						}
					});
				}
				BugzillaTaskEditorPage.this.addToAttributeEditorMap(taskAttribute, editor);
				return editor;
			}
		};
		return factory;
	}

	@Override
	public void doSubmit() {
		TaskAttribute summaryAttribute = getModel().getTaskData().getRoot().getMappedAttribute(TaskAttribute.SUMMARY);
		if (summaryAttribute != null && summaryAttribute.getValue().length() == 0) {
			getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Please_enter_a_short_summary_before_submitting,
					IMessageProvider.ERROR);
			AbstractTaskEditorPart part = getPart(ID_PART_SUMMARY);
			if (part != null) {
				part.setFocus();
			}
			return;
		}

		TaskAttribute componentAttribute = getModel().getTaskData()
				.getRoot()
				.getMappedAttribute(BugzillaAttribute.COMPONENT.getKey());
		if (componentAttribute != null && componentAttribute.getValue().length() == 0) {
			getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Please_select_a_component_before_submitting,
					IMessageProvider.ERROR);
			AbstractTaskEditorPart part = getPart(ID_PART_ATTRIBUTES);
			if (part != null) {
				part.setFocus();
			}
			return;
		}

		TaskAttribute descriptionAttribute = getModel().getTaskData()
				.getRoot()
				.getMappedAttribute(TaskAttribute.DESCRIPTION);
		if (descriptionAttribute != null && descriptionAttribute.getValue().length() == 0
				&& getModel().getTaskData().isNew()) {
			getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Please_enter_a_description_before_submitting,
					IMessageProvider.ERROR);
			AbstractTaskEditorPart descriptionPart = getPart(ID_PART_DESCRIPTION);
			if (descriptionPart != null) {
				descriptionPart.setFocus();
			}
			return;
		}

		TaskAttribute attributeOperation = getModel().getTaskData()
				.getRoot()
				.getMappedAttribute(TaskAttribute.OPERATION);
		if (attributeOperation != null) {
			if ("duplicate".equals(attributeOperation.getValue())) { //$NON-NLS-1$
				TaskAttribute originalOperation = getModel().getTaskData()
						.getRoot()
						.getAttribute(TaskAttribute.PREFIX_OPERATION + attributeOperation.getValue());
				String inputAttributeId = originalOperation.getMetaData().getValue(
						TaskAttribute.META_ASSOCIATED_ATTRIBUTE_ID);
				if (inputAttributeId != null && !inputAttributeId.equals("")) { //$NON-NLS-1$
					TaskAttribute inputAttribute = attributeOperation.getTaskData()
							.getRoot()
							.getAttribute(inputAttributeId);
					if (inputAttribute != null) {
						String dupValue = inputAttribute.getValue();
						if (dupValue == null || dupValue.equals("")) { //$NON-NLS-1$
							getTaskEditor().setMessage(
									Messages.BugzillaTaskEditorPage_Please_enter_a_bugid_for_duplicate_of_before_submitting,
									IMessageProvider.ERROR);
							AbstractTaskEditorPart part = getPart(ID_PART_ACTIONS);
							if (part != null) {
								part.setFocus();
							}
							return;
						}
					}
				}
			}
		}

		if (getModel().getTaskData().isNew()) {
			TaskAttribute productAttribute = getModel().getTaskData()
					.getRoot()
					.getMappedAttribute(TaskAttribute.PRODUCT);
			if (productAttribute != null && productAttribute.getValue().length() > 0) {
				getModel().getTaskRepository().setProperty(IBugzillaConstants.LAST_PRODUCT_SELECTION,
						productAttribute.getValue());
			}
			TaskAttribute componentSelectedAttribute = getModel().getTaskData()
					.getRoot()
					.getMappedAttribute(TaskAttribute.COMPONENT);
			if (componentSelectedAttribute != null && componentSelectedAttribute.getValue().length() > 0) {
				getModel().getTaskRepository().setProperty(IBugzillaConstants.LAST_COMPONENT_SELECTION,
						componentSelectedAttribute.getValue());
			}
		}

		// Force the most recent known good token onto the outgoing task data to ensure submit
		// bug#263318
		TaskAttribute attrToken = getModel().getTaskData().getRoot().getAttribute(BugzillaAttribute.TOKEN.getKey());
		if (attrToken != null) {
			String tokenString = getModel().getTask().getAttribute(BugzillaAttribute.TOKEN.getKey());
			if (tokenString != null) {
				attrToken.setValue(tokenString);
			}
		}
		//		for (ControlDecoration decoration : errorDecorations) {
		//			decoration.hide();
		//			decoration.dispose();
		//		}
		//		errorDecorations.clear();
		//		for (PersonAttributeEditor editor : editorsWithError) {
		//			editor.getContentAssistCommandAdapter().setEnabled(true);
		//		}
		if (!checkCanSubmit(IMessageProvider.ERROR)) {
			return;
		}
		getTaskEditor().setMessage("", IMessageProvider.NONE); //$NON-NLS-1$
		super.doSubmit();
	}

	@Override
	protected void createParts() {
		attributeEditorMap.clear();
		super.createParts();
		checkCanSubmit(IMessageProvider.INFORMATION);
	}

	@Override
	protected TaskDataModel createModel(TaskEditorInput input) throws CoreException {
		TaskDataModel model = super.createModel(input);
		productListener = new ProductSelectionListener();
		model.addModelListener(productListener);
		return model;
	}

	/**
	 * @since 3.1
	 */
	private void addToAttributeEditorMap(TaskAttribute attribute, AbstractAttributeEditor editor) {
		if (attributeEditorMap.containsKey(attribute)) {
			attributeEditorMap.remove(attribute);
		}
		attributeEditorMap.put(attribute, editor);
	}

	/**
	 * @since 3.1
	 */
	private AbstractAttributeEditor getEditorForAttribute(TaskAttribute attribute) {
		return attributeEditorMap.get(attribute);
	}

	private void refresh(TaskAttribute attributeComponent) {
		AbstractAttributeEditor editor = getEditorForAttribute(attributeComponent);
		if (editor != null) {
			try {
				editor.refresh();
			} catch (UnsupportedOperationException e) {
				// ignore
			}
		}
	}

	private class ProductSelectionListener extends TaskDataModelListener {
		@Override
		public void attributeChanged(TaskDataModelEvent event) {
			TaskAttribute taskAttribute = event.getTaskAttribute();
			if (taskAttribute != null) {
				if (taskAttribute.getId().equals(BugzillaAttribute.PRODUCT.getKey())) {
					RepositoryConfiguration repositoryConfiguration = null;
					try {
						BugzillaRepositoryConnector connector = (BugzillaRepositoryConnector) TasksUi.getRepositoryConnector(getModel().getTaskRepository()
								.getConnectorKind());
						repositoryConfiguration = connector.getRepositoryConfiguration(getModel().getTaskRepository(),
								false, new NullProgressMonitor());
					} catch (CoreException e) {
						StatusHandler.log(new RepositoryStatus(getTaskRepository(), IStatus.ERROR,
								BugzillaUiPlugin.ID_PLUGIN, 0, "Failed to obtain repository configuration", e)); //$NON-NLS-1$
						getTaskEditor().setMessage("Problem occured when updating attributes", IMessageProvider.ERROR); //$NON-NLS-1$
						return;
					}

					TaskAttribute attributeComponent = taskAttribute.getTaskData()
							.getRoot()
							.getMappedAttribute(BugzillaAttribute.COMPONENT.getKey());
					if (attributeComponent != null) {
						List<String> optionValues = repositoryConfiguration.getComponents(taskAttribute.getValue());
						Collections.sort(optionValues);
						attributeComponent.clearOptions();
						for (String option : optionValues) {
							attributeComponent.putOption(option, option);
						}
						if (optionValues.size() > 0) {
							attributeComponent.setValue(optionValues.get(0));
						}
						refresh(attributeComponent);
					}

					TaskAttribute attributeTargetMilestone = taskAttribute.getTaskData()
							.getRoot()
							.getMappedAttribute(BugzillaAttribute.TARGET_MILESTONE.getKey());
					if (attributeTargetMilestone != null) {
						List<String> optionValues = repositoryConfiguration.getTargetMilestones(taskAttribute.getValue());
						Collections.sort(optionValues);
						attributeTargetMilestone.clearOptions();
						for (String option : optionValues) {
							attributeTargetMilestone.putOption(option, option);
						}
						if (optionValues.size() == 1) {
							attributeTargetMilestone.setValue(optionValues.get(0));
						} else {
							attributeTargetMilestone.setValue("---"); //$NON-NLS-1$
						}
						refresh(attributeTargetMilestone);
					}

					TaskAttribute attributeVersion = taskAttribute.getTaskData()
							.getRoot()
							.getMappedAttribute(BugzillaAttribute.VERSION.getKey());
					if (attributeVersion != null) {
						List<String> optionValues = repositoryConfiguration.getVersions(taskAttribute.getValue());
						Collections.sort(optionValues);
						attributeVersion.clearOptions();
						for (String option : optionValues) {
							attributeVersion.putOption(option, option);
						}
						if (optionValues.size() == 1) {
							attributeVersion.setValue(optionValues.get(0));
						} else {
							attributeVersion.setValue("unspecified"); //$NON-NLS-1$
						}
						refresh(attributeVersion);
					}

					TaskAttribute attributeDefaultAssignee = taskAttribute.getTaskData()
							.getRoot()
							.getMappedAttribute(BugzillaAttribute.SET_DEFAULT_ASSIGNEE.getKey());
					if (attributeDefaultAssignee != null) {
						attributeDefaultAssignee.setValue("1"); //$NON-NLS-1$
						refresh(attributeDefaultAssignee);
					}
					if (taskAttribute.getTaskData().isNew()) {
						BugzillaVersion bugzillaVersion = repositoryConfiguration.getInstallVersion();
						if (bugzillaVersion == null) {
							bugzillaVersion = BugzillaVersion.MIN_VERSION;
						}
						if (bugzillaVersion.compareMajorMinorOnly(BugzillaVersion.BUGZILLA_4_0) >= 0) {
							AbstractTaskEditorPart part = getPart(ID_PART_ACTIONS);
							Boolean unconfirmedAllowed = repositoryConfiguration.getUnconfirmedAllowed(taskAttribute.getValue());
							TaskAttribute unconfirmedAttribute = taskAttribute.getTaskData()
									.getRoot()
									.getAttribute(
											TaskAttribute.PREFIX_OPERATION + BugzillaOperation.unconfirmed.toString());
							if (unconfirmedAttribute != null && unconfirmedAllowed != null) {
								unconfirmedAttribute.getMetaData().setReadOnly(!unconfirmedAllowed.booleanValue());
							}
							if (part != null) {
								TaskEditorActionPart actionPart = (TaskEditorActionPart) part;
								actionPart.refreshOperations();
							}
						}
					}
/*
 * 					add confirm_product_change to avoid verification page on submit
 */
					TaskAttribute attributeConfirmeProductChange = taskAttribute.getTaskData()
							.getRoot()
							.getMappedAttribute(BugzillaAttribute.CONFIRM_PRODUCT_CHANGE.getKey());
					if (attributeConfirmeProductChange == null) {
						attributeConfirmeProductChange = BugzillaTaskDataHandler.createAttribute(
								taskAttribute.getTaskData().getRoot(), BugzillaAttribute.CONFIRM_PRODUCT_CHANGE);
					}
					if (attributeConfirmeProductChange != null) {
						attributeConfirmeProductChange.setValue("1"); //$NON-NLS-1$
					}
				}
			}
		}
	}

	@Override
	protected void handleTaskSubmitted(SubmitJobEvent event) {
		if (event.getJob().getStatus() != null) {
			switch (event.getJob().getStatus().getCode()) {
			case BugzillaStatus.ERROR_CONFIRM_MATCH:
			case BugzillaStatus.ERROR_MATCH_FAILED:
				presentErrorToUI((BugzillaStatus) event.getJob().getStatus());
				break;
			}
		} else if (event.getJob().getResponse() != null
				&& event.getJob().getResponse() instanceof BugzillaRepositoryResponse) {
			final RepositoryResponse response = event.getJob().getResponse();
			if (response instanceof BugzillaRepositoryResponse) {
				final BugzillaRepositoryResponse bugzillaResponse = (BugzillaRepositoryResponse) response;
				if (bugzillaResponse.getResponseData().size() > 0) {
					getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Changes_Submitted_Message,
							IMessageProvider.INFORMATION, new HyperlinkAdapter() {
								@Override
								public void linkActivated(HyperlinkEvent event) {

									String message = ""; //$NON-NLS-1$
									for (String iterable_map : bugzillaResponse.getResponseData().keySet()) {
										if (message.length() > 0) {
											message += "\n"; //$NON-NLS-1$
										}
										message += NLS.bind(Messages.BugzillaTaskEditorPage_Bug_Line, iterable_map);
										Map<String, List<String>> responseMap = bugzillaResponse.getResponseData().get(
												iterable_map);
										for (String iterable_list : responseMap.keySet()) {
											message += NLS.bind(Messages.BugzillaTaskEditorPage_Action_Line,
													iterable_list);
											List<String> responseList = responseMap.get(iterable_list);
											for (String string : responseList) {
												message += NLS.bind(Messages.BugzillaTaskEditorPage_Email_Line, string);
											}
										}
									}
									BugzillaResponseDetailDialog dialog = new BugzillaResponseDetailDialog(
											WorkbenchUtil.getShell(), Messages.BugzillaTaskEditorPage_Response_Titel,
											message);
									dialog.open();
								}
							});
				} else {
					getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Changes_Submitted_Message,
							IMessageProvider.INFORMATION);
				}
			}
		} else {
			super.handleTaskSubmitted(event);
		}
	}

	@Override
	public void refresh() {
		super.refresh();
		checkCanSubmit(IMessageProvider.INFORMATION);
	}

	private boolean checkCanSubmit(final int type) {
		final TaskRepository taskRepository = getModel().getTaskRepository();
		AuthenticationCredentials cred = taskRepository.getCredentials(AuthenticationType.REPOSITORY);
		if (cred == null || cred.getUserName() == null || cred.getUserName().equals("")) { //$NON-NLS-1$
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				public void run() {
					getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_Anonymous_can_not_submit_Tasks, type,
							new HyperlinkAdapter() {
								@Override
								public void linkActivated(HyperlinkEvent e) {
									TasksUiUtil.openEditRepositoryWizard(taskRepository);
									refresh();
								}
							});
				}
			});
			return false;
		}
		if (!getModel().getTaskData().isNew()) {
			TaskAttribute exporter = getModel().getTaskData()
					.getRoot()
					.getAttribute(BugzillaAttribute.EXPORTER_NAME.getKey());
			if (exporter == null || exporter.getValue().equals("")) { //$NON-NLS-1$
				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
					public void run() {
						getTaskEditor().setMessage(Messages.BugzillaTaskEditorPage_submit_disabled_please_refresh,
								type, new HyperlinkAdapter() {
									@Override
									public void linkActivated(HyperlinkEvent e) {
										TasksUiUtil.openEditRepositoryWizard(taskRepository);
										refresh();
									}
								});
					}
				});
				return false;
			}
		}
		return true;
	}

	private void presentErrorToUI(BugzillaStatus bugzillaStatus) {
		String resultString;
		String resultDetail = ""; //$NON-NLS-1$
		int count = 0;
		String fieldString = ""; //$NON-NLS-1$
		Map<String, List<String>> response = bugzillaStatus.getResponseData();
		FieldDecorationRegistry registry = FieldDecorationRegistry.getDefault();
		FieldDecoration fieldDecoration = registry.getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);

		for (String string : response.keySet()) {
			TaskAttribute attribute = getModel().getTaskData().getRoot().getAttribute(string);
			if (count++ > 0) {
				fieldString += MessageFormat.format(Messages.BugzillaTaskEditorPage_Error_Label_N,
						attribute.getMetaData().getLabel());
			} else {
				fieldString += MessageFormat.format(Messages.BugzillaTaskEditorPage_Error_Label_1,
						attribute.getMetaData().getLabel());
			}
			resultDetail += attribute.getMetaData().getLabel() + "\n"; //$NON-NLS-1$

			AbstractAttributeEditor editor = getEditorForAttribute(attribute);
			if (editor != null) {
				Map<String, String> newPersonProposalMap = new HashMap<String, String>();
				String decorationText = ""; //$NON-NLS-1$
				for (String responseValue : response.get(string)) {
					if (responseValue.startsWith("#msg#")) { //$NON-NLS-1$
						decorationText += responseValue.substring(5);
					} else {
						newPersonProposalMap.put(responseValue, responseValue);
					}
					resultDetail += "\t\t" //$NON-NLS-1$
							+ (responseValue.startsWith("#msg#") ? responseValue.substring(5) : responseValue) + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
				}

				final Control control = editor.getControl();

				ControlDecoration decoration = new ControlDecoration(control, SWT.LEFT | SWT.TOP);
				decoration.setImage(fieldDecoration.getImage());
				IBindingService bindingService = (IBindingService) PlatformUI.getWorkbench().getService(
						IBindingService.class);
				if (!decorationText.equals("")) { //$NON-NLS-1$
					decoration.setDescriptionText(decorationText);
					errorDecorations.add(decoration);
				} else {
					decoration.setDescriptionText(NLS.bind(
							Messages.BugzillaTaskEditorPage_Content_Assist_for_Error_Available,
							bindingService.getBestActiveBindingFormattedFor(ContentAssistCommandAdapter.CONTENT_PROPOSAL_COMMAND)));
					errorDecorations.add(decoration);

					PersonAttributeEditor personEditor = ((PersonAttributeEditor) editor);
					final PersonProposalProvider personProposalProvider = (PersonProposalProvider) personEditor.getContentAssistCommandAdapter()
							.getContentProposalProvider();
					personProposalProvider.setErrorProposals(newPersonProposalMap);
					editorsWithError.add(personEditor);
					personEditor.getText().addKeyListener(new KeyListener() {

						public void keyReleased(KeyEvent e) {
							// ignore
						}

						public void keyPressed(KeyEvent e) {
							if (e.keyCode != SWT.ARROW_LEFT && e.keyCode != SWT.ARROW_RIGHT && e.keyCode != SWT.CTRL
									&& !((e.keyCode == 32) && ((e.stateMask & SWT.CTRL) == SWT.CTRL))) {
								for (ControlDecoration error : errorDecorations) {
									if (error.getControl() == control) {
										error.hide();
										errorDecorations.remove(error);
										error.dispose();
										break;
									}
								}
								personProposalProvider.setErrorProposals(null);
							}
						}
					});
				}

			}
		}
		final String codeString;
		switch (bugzillaStatus.getCode()) {
		case BugzillaStatus.ERROR_CONFIRM_MATCH:
			codeString = Messages.BugzillaTaskEditorPage_Confirm;
			break;
		case BugzillaStatus.ERROR_MATCH_FAILED:
			codeString = Messages.BugzillaTaskEditorPage_Error;
			break;
		default:
			codeString = ""; //$NON-NLS-1$
			break;
		}
		if (count > 1) {
			resultString = MessageFormat.format(Messages.BugzillaTaskEditorPage_Message_more, codeString, fieldString);
		} else {
			resultString = MessageFormat.format(Messages.BugzillaTaskEditorPage_Message_one, codeString, fieldString);
		}

		final String resultDetailString = resultDetail;
		getTaskEditor().setMessage(resultString, IMessageProvider.ERROR, new HyperlinkAdapter() {
			@Override
			public void linkActivated(HyperlinkEvent event) {
				BugzillaResponseDetailDialog dialog = new BugzillaResponseDetailDialog(WorkbenchUtil.getShell(),
						codeString + Messages.BugzillaTaskEditorPage_match_Detail, resultDetailString);
				dialog.open();
			}
		});
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;

/**
  */
public abstract class OptionsConfigurationBlock {

	protected static class ControlData {
		private String fKey;
		private String[] fValues;
		
		public ControlData(String key, String[] values) {
			fKey= key;
			fValues= values;
		}
		
		public String getKey() {
			return fKey;
		}
		
		public String getValue(boolean selection) {
			int index= selection ? 0 : 1;
			return fValues[index];
		}
		
		public String getValue(int index) {
			return fValues[index];
		}		
		
		public int getSelection(String value) {
			for (int i= 0; i < fValues.length; i++) {
				if (value.equals(fValues[i])) {
					return i;
				}
			}
			throw new IllegalArgumentException();
		}
	}
	
	
	protected Map fWorkingValues;

	protected ArrayList fCheckBoxes;
	protected ArrayList fComboBoxes;
	protected ArrayList fTextBoxes;
	
	private SelectionListener fSelectionListener;
	private ModifyListener fTextModifyListener;

	protected IStatusChangeListener fContext;
	protected IJavaProject fProject; // project or null
	
	private Shell fShell;

	public OptionsConfigurationBlock(IStatusChangeListener context, IJavaProject project) {
		fContext= context;
		fProject= project;
		
		fWorkingValues= getOptions(true);
		
		fCheckBoxes= new ArrayList();
		fComboBoxes= new ArrayList();
		fTextBoxes= new ArrayList(2); 
	}
	
	protected abstract String[] getAllKeys();
	
	protected Map getOptions(boolean inheritJavaCoreOptions) {
		if (fProject != null) {
			return fProject.getOptions(inheritJavaCoreOptions);
		} else {
			return JavaCore.getOptions();
		}	
	}
	
	protected Map getDefaultOptions() {
		return JavaCore.getDefaultOptions();
	}	
	
	public final boolean hasProjectSpecificOptions() {
		if (fProject != null) {
			Map settings= fProject.getOptions(false);
			String[] allKeys= getAllKeys();
			for (int i= 0; i < allKeys.length; i++) {
				if (settings.get(allKeys[i]) != null) {
					return true;
				}
			}
		}
		return false;
	}	
		
	protected final void setOptions(Map map) {
		if (fProject != null) {
			fProject.setOptions(map);
		} else {
			JavaCore.setOptions((Hashtable) map);
		}	
	} 
	
	protected Shell getShell() {
		return fShell;
	}
	
	protected void setShell(Shell shell) {
		fShell= shell;
	}	
	
	protected abstract Control createContents(Composite parent);
	
	protected Button addCheckBox(Composite parent, String label, String key, String[] values, int indent) {
		ControlData data= new ControlData(key, values);
		
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 3;
		gd.horizontalIndent= indent;
		
		Button checkBox= new Button(parent, SWT.CHECK);
		checkBox.setText(label);
		checkBox.setData(data);
		checkBox.setLayoutData(gd);
		checkBox.addSelectionListener(getSelectionListener());
		
		String currValue= (String)fWorkingValues.get(key);	
		checkBox.setSelection(data.getSelection(currValue) == 0);
		
		fCheckBoxes.add(checkBox);
		
		return checkBox;
	}
	
	protected void addComboBox(Composite parent, String label, String key, String[] values, String[] valueLabels, int indent) {
		ControlData data= new ControlData(key, values);
		
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= indent;
				
		Label labelControl= new Label(parent, SWT.LEFT | SWT.WRAP);
		labelControl.setText(label);
		labelControl.setLayoutData(gd);
		
		Combo comboBox= new Combo(parent, SWT.READ_ONLY);
		comboBox.setItems(valueLabels);
		comboBox.setData(data);
		comboBox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		comboBox.addSelectionListener(getSelectionListener());
		
		Label placeHolder= new Label(parent, SWT.NONE);
		placeHolder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		String currValue= (String)fWorkingValues.get(key);	
		comboBox.select(data.getSelection(currValue));
		
		fComboBoxes.add(comboBox);
	}
	
	protected Text addTextField(Composite parent, String label, String key, int indent, int widthHint) {	
		Label labelControl= new Label(parent, SWT.NONE);
		labelControl.setText(label);
		labelControl.setLayoutData(new GridData());
				
		Text textBox= new Text(parent, SWT.BORDER | SWT.SINGLE);
		textBox.setData(key);
		textBox.setLayoutData(new GridData());
		
		String currValue= (String) fWorkingValues.get(key);	
		textBox.setText(currValue);
		textBox.addModifyListener(getTextModifyListener());

		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		if (widthHint != 0) {
			data.widthHint= widthHint;
		}
		data.horizontalIndent= indent;
		data.horizontalSpan= 2;
		textBox.setLayoutData(data);

		fTextBoxes.add(textBox);
		return textBox;
	}	

	protected SelectionListener getSelectionListener() {
		if (fSelectionListener == null) {
			fSelectionListener= new SelectionListener() {
				public void widgetDefaultSelected(SelectionEvent e) {}
	
				public void widgetSelected(SelectionEvent e) {
					controlChanged(e.widget);
				}
			};
		}
		return fSelectionListener;
	}
	
	protected ModifyListener getTextModifyListener() {
		if (fTextModifyListener == null) {
			fTextModifyListener= new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					textChanged((Text) e.widget);
				}
			};
		}
		return fTextModifyListener;
	}		
	
	protected void controlChanged(Widget widget) {
		ControlData data= (ControlData) widget.getData();
		String newValue= null;
		if (widget instanceof Button) {
			newValue= data.getValue(((Button)widget).getSelection());			
		} else if (widget instanceof Combo) {
			newValue= data.getValue(((Combo)widget).getSelectionIndex());
		} else {
			return;
		}
		fWorkingValues.put(data.getKey(), newValue);
		
		validateSettings(data.getKey(), newValue);
	}
	
	protected void textChanged(Text textControl) {
		String key= (String) textControl.getData();
		String number= textControl.getText();
		fWorkingValues.put(key, number);
		validateSettings(key, number);
	}	

	protected boolean checkValue(String key, String value) {
		return value.equals(fWorkingValues.get(key));
	}
	
	/* (non-javadoc)
	 * Update fields and validate.
	 * @param changedKey Key that changed, or null, if all changed.
	 */	
	protected abstract void validateSettings(String changedKey, String newValue);
	
	
	protected String[] getTokens(String text, String separator) {
		StringTokenizer tok= new StringTokenizer(text, separator); //$NON-NLS-1$
		int nTokens= tok.countTokens();
		String[] res= new String[nTokens];
		for (int i= 0; i < res.length; i++) {
			res[i]= tok.nextToken().trim();
		}
		return res;
	}	

	
	public boolean performOk(boolean enabled) {
		String[] allKeys= getAllKeys();
		Map actualOptions= getOptions(false);
		
		// preserve other options
		boolean hasChanges= false;
		for (int i= 0; i < allKeys.length; i++) {
			String key= allKeys[i];
			String oldVal= (String) actualOptions.get(key);
			String val= null;
			if (enabled) {
				val= (String) fWorkingValues.get(key);
				if (!val.equals(oldVal)) {
					hasChanges= true;
					actualOptions.put(key, val);
				}
			} else {
				if (oldVal != null) {
					actualOptions.remove(key);
					hasChanges= true;
				}
			}
		}
		
		
		if (hasChanges) {
			boolean doBuild= false;
			String[] strings= getFullBuildDialogStrings(fProject == null);
			if (strings != null) {
				MessageDialog dialog= new MessageDialog(getShell(), strings[0], null, strings[1], MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL }, 2);
				int res= dialog.open();
				if (res == 0) {
					doBuild= true;
				} else if (res != 1) {
					return false; // cancel pressed
				}
			}
			setOptions(actualOptions);
			if (doBuild) {
				doFullBuild();
			}
		}
		return true;
	}
	
	protected abstract String[] getFullBuildDialogStrings(boolean workspaceSettings);
		
	protected void doFullBuild() {
		ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
		try {
			dialog.run(true, true, new IRunnableWithProgress() { 
				public void run(IProgressMonitor monitor) throws InvocationTargetException {
					monitor.beginTask("", 1); //$NON-NLS-1$
					try {
						if (fProject != null) {
							monitor.setTaskName(PreferencesMessages.getFormattedString("OptionsConfigurationBlock.buildproject.taskname", fProject.getElementName())); //$NON-NLS-1$
							fProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new SubProgressMonitor(monitor,1));
						} else {
							monitor.setTaskName(PreferencesMessages.getString("OptionsConfigurationBlock.buildall.taskname")); //$NON-NLS-1$
							JavaPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new SubProgressMonitor(monitor,1));
						}
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			});
		} catch (InterruptedException e) {
			// cancelled by user
		} catch (InvocationTargetException e) {
			String title= PreferencesMessages.getString("OptionsConfigurationBlock.builderror.title"); //$NON-NLS-1$
			String message= PreferencesMessages.getString("OptionsConfigurationBlock.builderror.message"); //$NON-NLS-1$
			ExceptionHandler.handle(e, getShell(), title, message);
		}
	}		
	
	public void performDefaults() {
		fWorkingValues= getDefaultOptions();
		updateControls();
		validateSettings(null, null);
	}
	
	protected void updateControls() {
		// update the UI
		for (int i= fCheckBoxes.size() - 1; i >= 0; i--) {
			Button curr= (Button) fCheckBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
					
			String currValue= (String) fWorkingValues.get(data.getKey());	
			curr.setSelection(data.getSelection(currValue) == 0);			
		}
		for (int i= fComboBoxes.size() - 1; i >= 0; i--) {
			Combo curr= (Combo) fComboBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
					
			String currValue= (String) fWorkingValues.get(data.getKey());	
			curr.select(data.getSelection(currValue));			
		}
		for (int i= fTextBoxes.size() - 1; i >= 0; i--) {
			Text curr= (Text) fTextBoxes.get(i);
			String key= (String) curr.getData();
			
			String currValue= (String) fWorkingValues.get(key);
			curr.setText(currValue);
		}
	}
	
	protected Button getCheckBox(String key) {
		for (int i= fCheckBoxes.size() - 1; i >= 0; i--) {
			Button curr= (Button) fCheckBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
			if (key.equals(data.getKey())) {
				return curr;
			}
		}
		return null;		
	}
	
	
	
}

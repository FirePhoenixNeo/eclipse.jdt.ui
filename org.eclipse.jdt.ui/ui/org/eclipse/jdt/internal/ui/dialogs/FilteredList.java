/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.dialogs;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import org.eclipse.jface.viewers.ILabelProvider;

import org.eclipse.jdt.internal.core.Assert;
import org.eclipse.jdt.internal.ui.util.StringMatcher;

/**
 * A composite widget which holds a list of elements for user selection.
 * The elements are sorted alphabetically.
 * Optionally, the elements can be filtered and duplicate entries can
 * be hidden (folding).
 */
public class FilteredList extends Composite {
	public interface FilterMatcher {
		/**
		 * Sets the filter.
		 * 
		 * @param pattern         the filter pattern.
		 * @param ignoreCase      a flag indicating whether pattern matching is case insensitive or not.
		 * @param ignoreWildCards a flag indicating whether wildcard characters are interpreted or not.
		 */
		void setFilter(String pattern, boolean ignoreCase, boolean ignoreWildCards);

		/**
		 * Returns <code>true</code> if the object matches the pattern, <code>false</code> otherwise.
		 * <code>setFilter()</code> must have been called at least once prior to a call to this method.
		 */
		boolean match(Object element);	
	}
	
	private class DefaultFilterMatcher implements FilterMatcher {
		private StringMatcher fMatcher;
		
		public void setFilter(String pattern, boolean ignoreCase, boolean ignoreWildCards) {
			fMatcher= new StringMatcher(pattern + '*', ignoreCase, ignoreWildCards);
		}
		
		public boolean match(Object element) {
			return fMatcher.match(fRenderer.getText(element));
		}	
	}

	private Table fList;
	private ILabelProvider fRenderer;
	private boolean fMatchEmtpyString= true;
	private boolean fIgnoreCase;
	private boolean fAllowDuplicates;
	private String fFilter= ""; //$NON-NLS-1$
	private TwoArrayQuickSorter fSorter;
	
	private Object[] fElements= new Object[0];
	private Label[] fLabels;
	private Vector fImages= new Vector();

	private int[] fFoldedIndices;
	private int fFoldedCount;
	
	private int[] fFilteredIndices;
	private int fFilteredCount;
	
	private FilterMatcher fFilterMatcher= new DefaultFilterMatcher();
	private Comparator fComparator;
	
	private UpdateThread fUpdateThread;

	private static class Label {
		public final String string;
		public final Image image;

		public Label(String string, Image image) {
			this.string= string;
			this.image= image;
		}
		
		public boolean equals(Label label) {
			if (label == null)
				return false;
				
			return			
				string.equals(label.string) &&
				image.equals(label.image);
		}
	}

	private final class LabelComparator implements Comparator {
		private boolean fIgnoreCase;
	
		LabelComparator(boolean ignoreCase) {
			fIgnoreCase= ignoreCase;
		}
	
		public int compare(Object left, Object right) {
			Label leftLabel= (Label) left;
			Label rightLabel= (Label) right;			

			int value;
			
			if (fComparator == null) {
				value= fIgnoreCase
					? leftLabel.string.compareToIgnoreCase(rightLabel.string)
					: leftLabel.string.compareTo(rightLabel.string);
			} else {
			    value= fComparator.compare(leftLabel.string, rightLabel.string);
			}

			if (value != 0)
				return value;

			// images are allowed to be null
			if (leftLabel.image == null) {
				return (rightLabel.image == null) ? 0 : -1;
			} else if (rightLabel.image == null) {
				return +1;				
			} else {
				return
					fImages.indexOf(leftLabel.image) -
					fImages.indexOf(rightLabel.image);
			}
		}
		
	}	
	
	/**
	 * Constructs a new instance of a filtered list.
	 * @param parent           the parent composite.
	 * @param style            the widget style.
	 * @param renderer         the label renderer.
	 * @param ignoreCase       specifies whether sorting and folding is case sensitive.
	 * @param allowDuplicates  specifies whether folding of duplicates is desired.
	 * @param matchEmptyString specifies whether empty filter strings should filter everything or nothing.
	 */
	public FilteredList(Composite parent, int style, ILabelProvider renderer,
		boolean ignoreCase, boolean allowDuplicates, boolean matchEmptyString)
	{
		super(parent, SWT.NONE);

		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		setLayout(layout);
		
		fList= new Table(this, style);
		fList.setLayoutData(new GridData(GridData.FILL_BOTH));
		fList.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				fRenderer.dispose();
				if (fUpdateThread != null)
					fUpdateThread.requestStop();
			}
		});
		
		fRenderer= renderer;
		fIgnoreCase= ignoreCase;		
		fSorter= new TwoArrayQuickSorter(new LabelComparator(ignoreCase));
		fAllowDuplicates= allowDuplicates;
		fMatchEmtpyString= matchEmptyString;
	}
	/**
	 * Sets the list of elements.
	 * @param elements the elements to be shown in the list.
	 */
	public void setElements(Object[] elements) {
		if (elements == null) {
			fElements= new Object[0];
		} else {
			// copy list for sorting
			fElements= new Object[elements.length];
			System.arraycopy(elements, 0, fElements, 0, elements.length);
		}
			
		int length= fElements.length;

		// fill labels			
		fLabels= new Label[length];
		Set imageSet= new HashSet();
		for (int i= 0; i != length; i++) {
			String text= fRenderer.getText(fElements[i]);
			Image image= fRenderer.getImage(fElements[i]);
			
			fLabels[i]= new Label(text, image);				
			imageSet.add(image);
		}
		fImages.clear();
		fImages.addAll(imageSet);

		fSorter.sort(fLabels, fElements);

		fFilteredIndices= new int[length];	
		fFoldedIndices= new int[length];

		updateList();
	}

	/**
	 * Tests if the list (before folding and filtering) is empty.
	 * @return returns <code>true</code> if the list is empty, <code>false</code> otherwise.
	 */
	public boolean isEmpty() {
		return (fElements == null) || (fElements.length == 0);
	}

	/**
	 * Sets the filter matcher.
	 */
	public void setFilterMatcher(FilterMatcher filterMatcher) {
		Assert.isNotNull(filterMatcher);
		fFilterMatcher= filterMatcher;
	}
	
	/**
	 * Sets a custom comparator for sorting the list.
	 */
	public void setComparator(Comparator comparator) {
	    Assert.isNotNull(comparator);
	    fComparator= comparator;
	}

    /**
     * Adds a selection listener to the list.
     * @param listener the selection listener to be added.
     */
	public void addSelectionListener(SelectionListener listener) {
		fList.addSelectionListener(listener);
	}

    /**
     * Removes a selection listener from the list.
     * @param listener the selection listener to be removed.
     */
	public void removeSelectionListener(SelectionListener listener) {
		fList.removeSelectionListener(listener);
	}	

    /**
     * Sets the selection of the list.
     * @param selection an array of indices specifying the selection.
     */
	public void setSelection(int[] selection) {
		fList.setSelection(selection);
	}
	
	/**
	 * Returns the selection of the list.
	 * @return returns an array of indices specifying the current selection.
	 */
	public int[] getSelectionIndices() {
		return fList.getSelectionIndices();
	}
	
	/**
	 * Returns the selection of the list.
	 * This is a convenience function for <code>getSelectionIndices()</code>.
	 * @return returns the index of the selection, -1 for no selection.
	 */
	public int getSelectionIndex() {
		return fList.getSelectionIndex();		
	}
	
	/**
	 * Sets the selection of the list.
	 * @param elements the array of elements to be selected.
	 */
	public void setSelection(Object[] elements) {
		if ((elements == null) || (fElements == null))
			return;			
		
		// fill indices
		int[] indices= new int[elements.length];
		for (int i= 0; i != elements.length; i++) {
			int j;			
			for (j= 0; j != fFoldedCount; j++) {
				int max= (j == fFoldedCount - 1)
					? fFilteredCount
					: fFoldedIndices[j + 1];

				int l;					
				for (l= fFoldedIndices[j]; l != max; l++) {
					// found matching element?
					if (fElements[fFilteredIndices[l]].equals(elements[i])) {
						indices[i]= j;
						break;	
					}
				}
				
				if (l != max)
					break;
			}
			
			// not found
			if (j == fFoldedCount)
				indices[i] = 0;
		}
		
		fList.setSelection(indices);
	}
	
	/**
	 * Returns an array of the selected elements. The type of the elements
	 * returned in the list are the same as the ones passed with
	 * <code>setElements</code>. The array does not contain the rendered strings.
	 * @return returns the array of selected elements.
	 */
	public Object[] getSelection() {
		if (fList.isDisposed() || (fList.getSelectionCount() == 0))
			return new Object[0];

		int[] indices= fList.getSelectionIndices();
		Object[] elements= new Object[indices.length];
		
		for (int i= 0; i != indices.length; i++)
			elements[i]= fElements[fFilteredIndices[fFoldedIndices[indices[i]]]];
		
		return elements;		
	}

	/**
	 * Sets the filter pattern. Current only prefix filter patterns are supported.
	 * @param filter the filter pattern.
	 */
	public void setFilter(String filter) {
		fFilter= (filter == null) ? "" : filter; //$NON-NLS-1$

		updateList();
	}

	private void updateList() {
		fFilteredCount= filter();
		fFoldedCount= fold();

		if (fUpdateThread != null)
			fUpdateThread.requestStop();
		fUpdateThread= new UpdateThread(new TableUpdater(fList, fFoldedCount));
		fUpdateThread.start();
	}
	
	/**
	 * Returns the filter pattern.
	 * @return returns the filter pattern.
	 */
	public String getFilter() {
		return fFilter;
	}

	/**
	 * Returns all elements which are folded together to one entry in the list.
	 * @param  index the index selecting the entry in the list.
	 * @return returns an array of elements folded together, <code>null</code> if index is out of range.
	 */
	public Object[] getFoldedElements(int index) {
		if ((index < 0) || (index >= fFoldedCount))
			return null;
		
		int start= fFoldedIndices[index];			
		int count= (index == fFoldedCount - 1)
			? fFilteredCount - start
			: fFoldedIndices[index + 1] - start;
			
		Object[] elements= new Object[count];
		for (int i= 0; i != count; i++)
			elements[i]= fElements[fFilteredIndices[start + i]];
				
		return elements;
	}

    /*
     * Folds duplicate entries. Two elements are considered as a pair of
     * duplicates if they coiincide in the rendered string and image.
     * @return returns the number of elements after folding.
     */
	private int fold() {
		if (fAllowDuplicates) {
			for (int i= 0; i != fFilteredCount; i++) 		
				fFoldedIndices[i]= i; // identity mapping

			return fFilteredCount;			
		
		} else {
			int k= 0;
			Label last= null;
			for (int i= 0; i != fFilteredCount; i++) {
				int j= fFilteredIndices[i];
				
				Label current= fLabels[j];
				if (! current.equals(last)) {
					fFoldedIndices[k]= i;
					k++;
					last= current;
				}
			}
			return k;
		}
	}

	/*
	 * Filters the list with the filter pattern.
     * @return returns the number of elements after filtering.
	 */
	private int filter() {
		if (((fFilter == null) || (fFilter.length() == 0)) && !fMatchEmtpyString)
			return 0;
		
		fFilterMatcher.setFilter(fFilter.trim(), fIgnoreCase, false);

		int k= 0;
		for (int i= 0; i != fElements.length; i++) {
			if (fFilterMatcher.match(fElements[i]))
				fFilteredIndices[k++]= i;
		}			
						
		return k;
	}	

	private interface IncrementalRunnable extends Runnable {
		public int getCount();		
		public void cancel();
	}

	private class TableUpdater implements IncrementalRunnable {
		private final Display fDisplay;
		private final Table fTable;
		private final int fCount;
		private int fIndex;
		
		public TableUpdater(Table table, int count) {
			fTable= table;
			fDisplay= table.getDisplay();
			fCount= count;
		}
		
		/*
		 * @see IncrementalRunnable#getCount()
		 */
		public int getCount() {
			return fCount + 1;	
		}
		
		/*
		 * @see IncrementalRunnable#cancel()
		 */
		public void cancel() {
			fIndex= 0;
		}
		
		/*
		 * @see Runnable#run()
		 */
		public void run() {
			final int index= fIndex++;

			fDisplay.syncExec(new Runnable() {
				public void run() {
					if (fTable.isDisposed())
						return;
					
			 		final int itemCount= fTable.getItemCount();
					
					if (index < fCount) {
						final TableItem item= (index < itemCount)
							? fTable.getItem(index)
							: new TableItem(fTable, SWT.NONE);
	
						final Label label= fLabels[fFilteredIndices[fFoldedIndices[index]]];
	
						item.setText(label.string);
						item.setImage(label.image);		

						// select first item
						if (index == 0) {
							fTable.setSelection(0);				 		
							fTable.notifyListeners(SWT.Selection, new Event());
						}

					// finish
					} else {
				 		if (fCount < itemCount) {
				 			fTable.setRedraw(false);
					 		fTable.remove(fCount, itemCount - 1);
				 			fTable.setRedraw(true);
				 		}

						// table empty -> no selection
						if (fCount == 0)
							fTable.notifyListeners(SWT.Selection, new Event());
					}
				}
			});
		}
	}

	private static class UpdateThread extends Thread {

		/** The incremental runnable */
		private final IncrementalRunnable fRunnable;
		/** A flag indicating a thread stop request */
		private boolean fStop;
		
		/**
		 * Creates an update thread.
		 */
		public UpdateThread(IncrementalRunnable runnable) {
			fRunnable= runnable;
		}
		
		/**
		 * Requests the thread to stop.
		 */
		public void requestStop() {
			fStop= true;
		}
		
		/**
		 * @see Runnable#run()
		 */
		public void run() {
			final int count= fRunnable.getCount();
			for (int i= 0; i != count; i++) {
				if (i % 50 == 0)
					try { Thread.sleep(10); } catch (InterruptedException e) {}

				if (fStop) {
					fRunnable.cancel();
					break;
				}

				fRunnable.run();
			}		
		}
	}

}
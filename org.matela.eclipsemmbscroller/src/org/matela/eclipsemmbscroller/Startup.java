/*******************************************************************************
 * Copyright (c) 2009 Mateusz Matela.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mateusz Matela - initial API and implementation
 *******************************************************************************/
package org.matela.eclipsemmbscroller;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.eclipse.jface.text.TextViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;

/**
 * Implementation of IStartup required by org.eclipse.ui.startup extension
 * point. It adds scrolling feature to all found text editors and adds a
 * listener to react when new editors come up.
 * 
 * @author Mateusz Matela
 */
public class Startup implements IStartup {

	/**
	 * Listener for adding a scroller to newly opened editors
	 */
	private class PartListener implements IPartListener2 {

		public void partVisible(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partOpened(IWorkbenchPartReference partRef) {
			tryToAttachScroller(partRef.getPart(false));
		}

		public void partInputChanged(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partHidden(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partDeactivated(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partClosed(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partBroughtToTop(IWorkbenchPartReference partRef) {
			// ignore
		}

		public void partActivated(IWorkbenchPartReference partRef) {
			// ignore
		}
	}

	private final PartListener listener = new PartListener();

	public void earlyStartup() {
		register();
	}

	/**
	 * Searches through the workbench and adds scroller wherever possible.
	 */
	private void register() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow[] workbenchWindows = workbench.getWorkbenchWindows();
		for (int i = 0; i < workbenchWindows.length; i++) {
			IWorkbenchPage[] pages = workbenchWindows[i].getPages();
			for (int j = 0; j < pages.length; j++) {
				IEditorReference[] editorReferences = pages[j]
						.getEditorReferences();
				for (int k = 0; k < editorReferences.length; k++) {
					tryToAttachScroller(editorReferences[k].getPart(false));
				}
			}

			IPartService partService = workbenchWindows[i].getPartService();
			partService.addPartListener(listener);
		}
	}

	/**
	 * Obtains given editor's text viewer using reflection mechanism
	 * 
	 * @param editor
	 *            the editor to get the text viewer from
	 * @return obtained text viewer or null if it couldn't be obtained
	 */
	private TextViewer getTextViewer(AbstractTextEditor editor) {
		try {
			Method method = AbstractTextEditor.class.getDeclaredMethod(
					"getSourceViewer", new Class[] {});
			method.setAccessible(true);
			return (TextViewer) method.invoke(editor, new Object[] {});
		} catch (SecurityException e) {
			return null;
		} catch (NoSuchMethodException e) {
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		} catch (InvocationTargetException e) {
			return null;
		} catch (ClassCastException e) {
			return null;
		}
	}

	private Object getEditor(final MultiPageEditorPart multiEditor,
			final int index) {
		final Object[] editor = new Object[1];
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().syncExec(new Runnable() {
			public void run() {
				try {
					final Method getEditor = MultiPageEditorPart.class
							.getDeclaredMethod("getEditor",
									new Class[] { Integer.TYPE });
					getEditor.setAccessible(true);
					editor[0] = getEditor.invoke(multiEditor,
							new Object[] { (Object) new Integer(index) });
				} catch (SecurityException e) {
					// continue
				} catch (NoSuchMethodException e) {
					// continue
				} catch (IllegalArgumentException e) {
					// continue
				} catch (IllegalAccessException e) {
					// continue
				} catch (InvocationTargetException e) {
					// continue
				}
			}
		});
		return editor[0];
	}

	/**
	 * Searches given editor's pages for text editors. It uses reflection
	 * mechanism.
	 * 
	 * @param multiEditor
	 *            the multi-part editor to search
	 * @return an array of text viewers. If no viewers could be found or an
	 *         error occurs, the array is empty.
	 */
	private TextViewer[] getTextViewers(final MultiPageEditorPart multiEditor) {
		ArrayList textViewers = new ArrayList();
		try {
			final Method getPageCount = MultiPageEditorPart.class
					.getDeclaredMethod("getPageCount", new Class[0]);
			getPageCount.setAccessible(true);
			int pageCount = ((Integer) getPageCount.invoke(multiEditor,
					new Object[0])).intValue();
			for (int i = 0; i < pageCount; i++) {
				final Object editor = getEditor(multiEditor, i);
				if (editor instanceof AbstractTextEditor) {
					TextViewer viewer = getTextViewer((AbstractTextEditor) editor);
					if (viewer != null)
						textViewers.add(viewer);
				}
			}
		} catch (SecurityException e) {
			// continue
		} catch (NoSuchMethodException e) {
			// continue
		} catch (IllegalArgumentException e) {
			// continue
		} catch (IllegalAccessException e) {
			// continue
		} catch (InvocationTargetException e) {
			// continue
		} catch (ClassCastException e) {
			// continue
		}
		return (TextViewer[]) textViewers.toArray(new TextViewer[0]);
	}

	/**
	 * Tries to attach a scroller to given part. If the part is a text editor,
	 * the scroller is attached to its text viewer. If the part is a multi-part
	 * editor, the scroller is attached to every sub-part that is a text editor.
	 * 
	 * @param part
	 *            the part to add scroller to. Should be an instance of
	 *            {@link AbstractTextEditor} or {@link MultiPageEditorPart}
	 *            (otherwise nothing happens).
	 */
	private void tryToAttachScroller(IWorkbenchPart part) {
		if (part instanceof AbstractTextEditor) {
			TextViewer viewer = getTextViewer((AbstractTextEditor) part);
			if (viewer != null) {
				final StyledText widget = viewer.getTextWidget();
				viewer.getControl().getDisplay().asyncExec(new Runnable() {
					public void run() {
						StyledTextScroller.addStyledText(widget);
					}
				});
			}
		}
		if (part instanceof MultiPageEditorPart) {
			TextViewer[] textViewers = getTextViewers((MultiPageEditorPart) part);
			for (int i = 0; i < textViewers.length; i++) {
				final StyledText widget = textViewers[i].getTextWidget();
				textViewers[i].getControl().getDisplay().asyncExec(
						new Runnable() {
							public void run() {
								StyledTextScroller.addStyledText(widget);
							}
						});
			}
		}
	}

}

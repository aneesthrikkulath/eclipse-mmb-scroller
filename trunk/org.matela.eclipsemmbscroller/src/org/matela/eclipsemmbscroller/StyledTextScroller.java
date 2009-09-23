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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * This class can add scrolling functionality to {@link StyledText} widgets.
 * Scrolling starts when middle mouse button is pressed in a widget. The
 * direction and speed of scrolling is controlled with mouse cursor position on
 * the screen.
 * 
 * It is ensured that only one instance of this class serves all widgets within
 * the same Display.
 * 
 * @author Mateusz Matela
 */
public class StyledTextScroller {

	/**
	 * A thread responsible for periodic checking of current position of the
	 * mouse cursor, calculating scrolling speed and moving visible area of
	 * currently scrolled widget.
	 */
	private class ScrollingThread extends Thread {
		private final double SCROLL_SPEED = 0.005;

		private final double SCROLL_FACTOR_LINEAR = 0.6;

		private final double SCROLL_FACTOR_SQUARE = 0.012;

		private final int MIN_SCROLL_INTERVAL = 30;

		private StyledText currentWidget;

		private int horizontalScrollDistance = 1;

		private int verticalScrollDistance = 0;

		private int sleepTime = 0;

		public Point initialLocation;

		public Point currentLocation;

		public boolean scrollStarted;

		private Runnable runnable = new Runnable() {
			public void run() {
				StyledText widget;
				int vertical, horizontal;
				synchronized (StyledTextScroller.this) {
					currentLocation = fDisplay.getCursorLocation();
					widget = currentWidget;
					vertical = verticalScrollDistance;
					horizontal = horizontalScrollDistance;
				}
				if (widget != null && !widget.isDisposed()) {
					if (vertical != 0) {
						widget.setTopPixel(widget.getTopPixel() + vertical);
					}
					if (horizontal != 0) {
						widget.setHorizontalPixel(widget.getHorizontalPixel()
								+ horizontal);
					}
				}
			}
		};

		public void run() {
			Point tempLocation = null;
			while (true) {

				if (isInterrupted())
					return;
				try {
					if (isActive()) {
						Thread.sleep(sleepTime);
					} else
						synchronized (this) {
							while (!isActive())
								wait();
						}
				} catch (InterruptedException e) {
					return;
				}

				synchronized (this) {
					if (!currentLocation.equals(tempLocation)) {
						tempLocation = currentLocation;
						double verticalSpeed = absDec(tempLocation.y
								- initialLocation.y, SCROLL_TOOL_RADIUS);
						double horizontalSpeed = absDec(tempLocation.x
								- initialLocation.x, SCROLL_TOOL_RADIUS);
						if (verticalSpeed != 0 || horizontalSpeed != 0) {
							recalculateScrollingSpeed(verticalSpeed,
									horizontalSpeed);
						} else {
							resetScrollingSpeed();
						}
					}
				}

				fDisplay.asyncExec(runnable);
			}
		}

		private void recalculateScrollingSpeed(double verticalSpeed,
				double horizontalSpeed) {
			verticalSpeed = SCROLL_SPEED
					* (SCROLL_FACTOR_LINEAR * verticalSpeed + SCROLL_FACTOR_SQUARE
							* absSqr(verticalSpeed));
			horizontalSpeed = SCROLL_SPEED
					* (SCROLL_FACTOR_LINEAR * horizontalSpeed + SCROLL_FACTOR_SQUARE
							* absSqr(horizontalSpeed));

			if (verticalSpeed != 0) {
				sleepTime = Math.abs((int) Math.round(1.0 / verticalSpeed));
				if (horizontalSpeed != 0)
					sleepTime = Math.min(sleepTime, Math.abs((int) Math
							.round(1.0 / horizontalSpeed)));
			} else {
				sleepTime = Math.abs((int) Math.round(1.0 / horizontalSpeed));
			}
			sleepTime = Math.max(sleepTime, MIN_SCROLL_INTERVAL);

			verticalScrollDistance = (int) Math
					.round(sleepTime * verticalSpeed);
			horizontalScrollDistance = (int) Math.round(sleepTime
					* horizontalSpeed);

			scrollStarted = true;
		}

		private void resetScrollingSpeed() {
			horizontalScrollDistance = verticalScrollDistance = 0;
			sleepTime = 100;
		}

		private int absDec(int value, int decrement) {
			if (value > 0) {
				value -= decrement;
				if (value < 0)
					return 0;
			} else {
				value += decrement;
				if (value > 0)
					return 0;
			}
			return value;
		}

		private double absSqr(double value) {
			return value < 0 ? -value * value : value * value;
		}

		/**
		 * 
		 * @return true if one of the widgets is being scrolled
		 */
		public synchronized boolean isActive() {
			return currentWidget != null;
		}

		/**
		 * Starts scrolling given widget
		 * 
		 * @param widget
		 *            the widget to scroll
		 * @param cursorLocation
		 *            the location of the cursor at the beginning of the
		 *            scrolling
		 */
		public synchronized void activate(StyledText widget,
				Point cursorLocation) {
			initialLocation = currentLocation = cursorLocation;
			currentWidget = widget;
			scrollStarted = false;
			resetScrollingSpeed();

			if (isAlive()) {
				notify();
			} else {
				start();
			}
		}

		/**
		 * Stops scrolling
		 */
		public synchronized void deactivate() {
			currentWidget = null;
		}

		/**
		 * 
		 * @return true if visible area has been moved since the last activation
		 *         of the scrolling.
		 */
		public synchronized boolean isScrollStarted() {
			return scrollStarted;
		}
	}

	/**
	 * This class is responsible for listening for GUI events and
	 * activating/deactivating scrolling.
	 */
	private class ScrollLisener implements FocusListener, ControlListener,
			Listener, DisposeListener {
		public void focusGained(FocusEvent e) {
			// ignore
		}

		public void focusLost(FocusEvent e) {
			/*
			 * the widget has lost the focus, which means user has clicked
			 * elsewhere - better stop scrolling
			 */
			deactivate();
		}

		public void controlMoved(ControlEvent e) {
			/* the window is being moved - better stop scrolling */
			deactivate();
		}

		public void controlResized(ControlEvent e) {
			/* the window is being resized - better stop scrolling */
			deactivate();
		}

		public void handleEvent(Event event) {
			switch (event.type) {
			case SWT.MouseDown:
				if (fScrolledWidgets.contains(event.widget)
						&& event.button == 2 && !isActive()) {
					activate((StyledText) event.widget);
				} else {
					deactivate();
				}
				break;
			case SWT.MouseUp:
				if (isActive() && fScrollingThread.isScrollStarted()) {
					deactivate();
				}
				break;
			}
		}

		public void widgetDisposed(DisposeEvent e) {
			if (fScrolledWidgets.contains(e.widget))
				unscrollStyledText((StyledText) e.widget);
			else {
				fScrolledWidgetsShells.remove(e.widget);
				((Shell) e.widget).removeControlListener(this);
				((Shell) e.widget).removeDisposeListener(this);
			}
		}
	}

	private final ScrollLisener listener = new ScrollLisener();

	private final int SCROLL_TOOL_RADIUS = 12;

	private static HashMap scrolledDisplays = new HashMap();

	private Image fScrollerImage;

	private HashSet fScrolledWidgets = new HashSet();

	private HashSet fScrolledWidgetsShells = new HashSet();

	private Shell fScrollTool;

	private Display fDisplay;

	private ScrollingThread fScrollingThread;

	private StyledTextScroller(Display display) {
		fDisplay = display;
		fDisplay.addFilter(SWT.MouseDown, listener);
		fDisplay.addFilter(SWT.MouseUp, listener);
	}

	/**
	 * Adds scrolling functionality to given {@link StyledText} widget. This
	 * method should be called from GUI thread of the widget's display.
	 * 
	 * @param widget
	 *            the widget to scroll
	 */
	public synchronized static void addStyledText(StyledText widget) {
		Display display = widget.getDisplay();
		StyledTextScroller scroller = (StyledTextScroller) scrolledDisplays
				.get(display);
		if (scroller == null) {
			scroller = new StyledTextScroller(display);
			scrolledDisplays.put(display, scroller);
		}
		scroller.scrollStyledText(widget);
	}

	private void scrollStyledText(StyledText widget) {
		if (fScrolledWidgets.contains(widget))
			return;
		fScrolledWidgets.add(widget);
		widget.addFocusListener(listener);
		widget.addDisposeListener(listener);

		Shell widgetShell = widget.getShell();
		if (fScrolledWidgetsShells.contains(widgetShell))
			return;
		fScrolledWidgetsShells.add(widgetShell);
		widgetShell.addControlListener(listener);
		widgetShell.addDisposeListener(listener);
	}

	/**
	 * Removes scrolling functionality from given {@link StyledText} widget.
	 * This method should be called from GUI thread of the widget's display.
	 * 
	 * @param widget
	 *            the widget to stop scrolling
	 */
	public synchronized static void removeStyledText(StyledText widget) {
		StyledTextScroller scroller = (StyledTextScroller) scrolledDisplays
				.get(widget.getDisplay());
		if (scroller != null)
			scroller.unscrollStyledText(widget);
	}

	/**
	 * Removes scrolling from all widgets.
	 */
	public synchronized static void disposeAll() {
		Set keySet = new HashSet(scrolledDisplays.keySet());
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			Display display = (Display) iterator.next();
			final StyledTextScroller scroller = (StyledTextScroller) scrolledDisplays
					.get(display);
			display.asyncExec(new Runnable() {
				public void run() {
					scroller.dispose();
				}
			});
		}
	}

	private void unscrollStyledText(StyledText widget) {
		if (!fScrolledWidgets.contains(widget))
			return;
		fScrolledWidgets.remove(widget);
		widget.removeFocusListener(listener);
		widget.removeDisposeListener(listener);
		if (fScrolledWidgets.size() == 0)
			dispose();
	}

	private void dispose() {
		if (fScrollingThread != null)
			fScrollingThread.interrupt();
		if (fScrollerImage != null)
			fScrollerImage.dispose();
		if (fScrollTool != null)
			fScrollTool.dispose();
		for (Iterator iterator = fScrolledWidgets.iterator(); iterator
				.hasNext();) {
			StyledText widget = (StyledText) iterator.next();
			widget.removeFocusListener(listener);
			widget.removeDisposeListener(listener);
		}
		fScrolledWidgets.clear();

		scrolledDisplays.remove(fDisplay);
		fDisplay.removeFilter(SWT.MouseDown, listener);
		fDisplay.removeFilter(SWT.MouseUp, listener);
	}

	private boolean isActive() {
		return fScrollingThread != null && fScrollingThread.isActive();
	}

	private void activate(StyledText widget) {
		if (isActive())
			return;

		if (fScrollTool == null) {
			createScrollTool();
		}
		Point cursorLocation = fDisplay.getCursorLocation();
		fScrollTool.setLocation(cursorLocation.x - SCROLL_TOOL_RADIUS,
				cursorLocation.y - SCROLL_TOOL_RADIUS);
		fScrollTool.setVisible(true);

		if (fScrollingThread == null)
			fScrollingThread = new ScrollingThread();

		fScrollingThread.activate(widget, cursorLocation);
	}

	private void draw8Points(GC gc, int x, int y) {
		gc.drawPoint(SCROLL_TOOL_RADIUS + x - 1, SCROLL_TOOL_RADIUS + y - 1);
		gc.drawPoint(SCROLL_TOOL_RADIUS + x - 1, SCROLL_TOOL_RADIUS - y);
		gc.drawPoint(SCROLL_TOOL_RADIUS - x, SCROLL_TOOL_RADIUS + y - 1);
		gc.drawPoint(SCROLL_TOOL_RADIUS - x, SCROLL_TOOL_RADIUS - y);
		gc.drawPoint(SCROLL_TOOL_RADIUS + y - 1, SCROLL_TOOL_RADIUS + x - 1);
		gc.drawPoint(SCROLL_TOOL_RADIUS + y - 1, SCROLL_TOOL_RADIUS - x);
		gc.drawPoint(SCROLL_TOOL_RADIUS - y, SCROLL_TOOL_RADIUS + x - 1);
		gc.drawPoint(SCROLL_TOOL_RADIUS - y, SCROLL_TOOL_RADIUS - x);
	}

	private Image getScrollerImage() {
		if (fScrollerImage == null) {
			fScrollerImage = new Image(fDisplay, 2 * SCROLL_TOOL_RADIUS,
					2 * SCROLL_TOOL_RADIUS);
			GC gc = new GC(fScrollerImage);

			for (int x = 0; x < SCROLL_TOOL_RADIUS; x++)
				for (int y = x; y < SCROLL_TOOL_RADIUS; y++) {
					int angleFactor = SCROLL_TOOL_RADIUS
							- Math.max(2, Math.max(x - y, y - x));
					int brightness = 255 - 255 * (x * x + y * y)
							/ SCROLL_TOOL_RADIUS / SCROLL_TOOL_RADIUS
							/ angleFactor;
					Color color = new Color(fDisplay, brightness, brightness,
							brightness);
					gc.setForeground(color);
					draw8Points(gc, x, y);
					color.dispose();
				}
			int arrowBrightness = 55;
			Color color = new Color(fDisplay, arrowBrightness, arrowBrightness,
					arrowBrightness);
			gc.setForeground(color);
			for (int x = 1; x <= SCROLL_TOOL_RADIUS / 3; x++)
				for (int y = x; y <= SCROLL_TOOL_RADIUS / 3; y++)
					draw8Points(gc, x, SCROLL_TOOL_RADIUS - y);
			draw8Points(gc, 1, 1);
			color.dispose();
			arrowBrightness *= 2;
			color = new Color(fDisplay, arrowBrightness, arrowBrightness,
					arrowBrightness);
			gc.setForeground(color);
			draw8Points(gc, SCROLL_TOOL_RADIUS / 3, SCROLL_TOOL_RADIUS
					- SCROLL_TOOL_RADIUS / 3 - 1);
			draw8Points(gc, 1, 2);
			color.dispose();
			gc.dispose();
		}
		return fScrollerImage;
	}

	private void deactivate() {
		if (!isActive())
			return;

		fScrollTool.dispose();
		fScrollTool = null;
		fScrollingThread.deactivate();
	}

	private void createScrollTool() {
		fScrollTool = new Shell(fDisplay, SWT.NO_TRIM | SWT.TOOL | SWT.ON_TOP);
		fScrollTool.setSize(SCROLL_TOOL_RADIUS * 2, SCROLL_TOOL_RADIUS * 2);
		Region region = new Region();
		region.add(createCirclePolygon(SCROLL_TOOL_RADIUS));
		fScrollTool.setRegion(region);
		region.dispose();
		final Image image = getScrollerImage();
		fScrollTool.setBackgroundImage(image);
	}

	private int[] createCirclePolygon(int r) {
		int[] result = new int[8 * r - 4];
		for (int y = 1; y <= r; y++) {
			int x = (int) Math.sqrt(r * r - y * y);
			result[2 * y - 2] = r + x;
			result[2 * y + 1 - 2] = r + y;
			result[2 * (2 * r - y) - 2] = r - x;
			result[2 * (2 * r - y) + 1 - 2] = r + y;
			result[2 * (2 * r + y) - 4] = r - x;
			result[2 * (2 * r + y) + 1 - 4] = r - y;
			result[2 * (4 * r - y) - 4] = r + x;
			result[2 * (4 * r - y) + 1 - 4] = r - y;
		}
		return result;
	}
}

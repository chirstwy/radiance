/*
 * Copyright (c) 2005-2018 Flamingo Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *     
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *     
 *  o Neither the name of Flamingo Kirill Grouchnikov nor the names of 
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *     
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package org.pushingpixels.flamingo.internal.ui.ribbon;

import org.pushingpixels.flamingo.api.common.*;
import org.pushingpixels.flamingo.api.common.popup.JPopupPanel;
import org.pushingpixels.flamingo.api.common.popup.PopupPanelManager;
import org.pushingpixels.flamingo.api.common.popup.PopupPanelManager.PopupEvent;
import org.pushingpixels.flamingo.api.ribbon.*;
import org.pushingpixels.flamingo.api.ribbon.resize.RibbonBandResizePolicy;
import org.pushingpixels.flamingo.api.ribbon.resize.RibbonBandResizeSequencingPolicy;
import org.pushingpixels.flamingo.internal.ui.common.BasicCommandButtonUI;
import org.pushingpixels.flamingo.internal.ui.ribbon.appmenu.JRibbonApplicationMenuButton;
import org.pushingpixels.flamingo.internal.utils.FlamingoUtilities;
import org.pushingpixels.flamingo.internal.utils.KeyTipManager;
import org.pushingpixels.neon.NeonCortex;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * Basic UI for ribbon {@link JRibbon}.
 * 
 * @author Kirill Grouchnikov
 */
public abstract class BasicRibbonUI extends RibbonUI {
    private static final String JUST_MINIMIZED = "ribbon.internal.justMinimized";

    /**
     * The associated ribbon.
     */
    protected JRibbon ribbon;

    protected JScrollablePanel<BandHostPanel> bandScrollablePanel;

    protected JScrollablePanel<TaskToggleButtonsHostPanel> taskToggleButtonsScrollablePanel;

    protected JRibbonApplicationMenuButton applicationMenuButton;

    protected Container anchoredButtons;

    /**
     * Map of toggle buttons of all tasks.
     */
    protected Map<RibbonTask, JRibbonTaskToggleButton> taskToggleButtons;

    /**
     * Button group for task toggle buttons.
     */
    protected CommandToggleButtonGroup taskToggleButtonGroup;

    /**
     * Change listener.
     */
    protected ChangeListener ribbonChangeListener;

    /**
     * Property change listener.
     */
    protected PropertyChangeListener propertyChangeListener;

    protected ComponentListener ribbonComponentListener;

    /**
     * Creates a new basic ribbon UI delegate.
     */
    public BasicRibbonUI() {
        this.taskToggleButtons = new HashMap<RibbonTask, JRibbonTaskToggleButton>();
        this.taskToggleButtonGroup = new CommandToggleButtonGroup();
        this.taskToggleButtonGroup.setAllowsClearingSelection(false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.plaf.ComponentUI#installUI(javax.swing.JComponent)
     */
    @Override
    public void installUI(JComponent c) {
        this.ribbon = (JRibbon) c;
        installDefaults();
        installComponents();
        installListeners();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.plaf.ComponentUI#uninstallUI(javax.swing.JComponent)
     */
    @Override
    public void uninstallUI(JComponent c) {
        uninstallListeners();
        uninstallComponents();
        uninstallDefaults();

        this.ribbon = null;
    }

    /**
     * Installs listeners on the associated ribbon.
     */
    protected void installListeners() {
        this.ribbonChangeListener = (ChangeEvent e) -> syncRibbonState();
        this.ribbon.addChangeListener(this.ribbonChangeListener);

        this.propertyChangeListener = (PropertyChangeEvent evt) -> {
            if ("selectedTask".equals(evt.getPropertyName())) {
                RibbonTask old = (RibbonTask) evt.getOldValue();
                final RibbonTask curr = (RibbonTask) evt.getNewValue();
                if ((old != null) && (taskToggleButtons.get(old) != null)) {
                    taskToggleButtons.get(old).getActionModel().setSelected(false);
                }
                if ((curr != null) && (taskToggleButtons.get(curr) != null)) {
                    taskToggleButtons.get(curr).getActionModel().setSelected(true);
                }

                if (isShowingScrollsForTaskToggleButtons() && (curr != null)) {
                    // scroll selected task as necessary so that it's
                    // visible
                    JRibbonTaskToggleButton toggleButton = taskToggleButtons.get(curr);
                    if (toggleButton != null) {
                        scrollAndRevealTaskToggleButton(toggleButton);
                    }
                }

                // Special case for showing key tips of ribbon tasks.
                // When a ribbon task is selected with a key tip, its
                // showing and layout is deferred as a separate Runnable
                // on EDT. When the key chain for that task is created,
                // the command buttons are not at their final size yet
                // and no key tips are shown.
                // Here we schedule yet another Runnable
                // to recompute all keytips if the
                // originator is a task toggle button.
                SwingUtilities.invokeLater(() -> {
                    KeyTipManager ktm = KeyTipManager.defaultManager();
                    if (ktm.isShowingKeyTips()) {
                        KeyTipManager.KeyTipChain chain = ktm.getCurrentlyShownKeyTipChain();
                        if (chain.chainParentComponent == taskToggleButtons.get(curr)) {
                            ktm.refreshCurrentChain();
                        }
                    }
                });
            }
            if ("applicationMenuRichTooltip".equals(evt.getPropertyName())) {
                syncApplicationMenuTips();
            }
            if ("applicationMenuKeyTip".equals(evt.getPropertyName())) {
                syncApplicationMenuTips();
            }
            if ("applicationMenu".equals(evt.getPropertyName())) {
                ribbon.revalidate();
                ribbon.doLayout();
                ribbon.repaint();
                Window windowAncestor = SwingUtilities.getWindowAncestor(ribbon);
                if (windowAncestor instanceof JRibbonFrame) {
                    FlamingoUtilities.updateRibbonFrameIconImages((JRibbonFrame) windowAncestor);
                }
            }
            if ("minimized".equals(evt.getPropertyName())) {
                PopupPanelManager.defaultManager().hidePopups(null);
                ribbon.revalidate();
                ribbon.doLayout();
                ribbon.repaint();
            }
        };
        this.ribbon.addPropertyChangeListener(this.propertyChangeListener);

        this.ribbonComponentListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                KeyTipManager.defaultManager().hideAllKeyTips();
            }
        };
        this.ribbon.addComponentListener(this.ribbonComponentListener);
    }

    /**
     * Uninstalls listeners from the associated ribbon.
     */
    protected void uninstallListeners() {
        // this.taskToggleButtonsScrollablePanel.getView()
        // .removeMouseWheelListener(this.mouseWheelListener);
        // this.mouseWheelListener = null;
        //
        this.ribbon.removeChangeListener(this.ribbonChangeListener);
        this.ribbonChangeListener = null;

        this.ribbon.removePropertyChangeListener(this.propertyChangeListener);
        this.propertyChangeListener = null;

        this.ribbon.removeComponentListener(this.ribbonComponentListener);
        this.ribbonComponentListener = null;
    }

    /**
     * Installs defaults on the associated ribbon.
     */
    protected void installDefaults() {
        Border b = this.ribbon.getBorder();
        if (b == null || b instanceof UIResource) {
            this.ribbon.setBorder(new BorderUIResource.EmptyBorderUIResource(1, 1, 1, 1));
        }
    }

    /**
     * Uninstalls defaults from the associated ribbon.
     */
    protected void uninstallDefaults() {
    }

    /**
     * Installs subcomponents on the associated ribbon.
     */
    protected void installComponents() {
        // band scrollable panel
        BandHostPanel bandHostPanel = createBandHostPanel();
        bandHostPanel.setLayout(createBandHostPanelLayoutManager());
        this.bandScrollablePanel = new JScrollablePanel<BandHostPanel>(bandHostPanel,
                JScrollablePanel.ScrollType.HORIZONTALLY);
        this.bandScrollablePanel.setScrollOnRollover(false);
        this.ribbon.add(this.bandScrollablePanel);

        // task toggle buttons scrollable panel
        TaskToggleButtonsHostPanel taskToggleButtonsHostPanel = createTaskToggleButtonsHostPanel();
        taskToggleButtonsHostPanel.setLayout(createTaskToggleButtonsHostPanelLayoutManager());
        this.taskToggleButtonsScrollablePanel = new JScrollablePanel<TaskToggleButtonsHostPanel>(
                taskToggleButtonsHostPanel, JScrollablePanel.ScrollType.HORIZONTALLY);
        this.taskToggleButtonsScrollablePanel.setScrollOnRollover(false);
        // need to repaint the entire ribbon on change since scrolling
        // the task toggle buttons affects the contour outline
        // of the ribbon
        this.taskToggleButtonsScrollablePanel
                .addChangeListener((ChangeEvent e) -> ribbon.repaint());
        this.ribbon.add(this.taskToggleButtonsScrollablePanel);

        this.ribbon.setLayout(createLayoutManager());

        this.syncRibbonState();

        this.applicationMenuButton = new JRibbonApplicationMenuButton(this.ribbon);
        this.syncApplicationMenuTips();
        this.ribbon.add(applicationMenuButton);
        Window windowAncestor = SwingUtilities.getWindowAncestor(this.ribbon);
        if (windowAncestor instanceof JRibbonFrame) {
            FlamingoUtilities.updateRibbonFrameIconImages((JRibbonFrame) windowAncestor);
        }
    }

    protected LayoutManager createTaskToggleButtonsHostPanelLayoutManager() {
        return new TaskToggleButtonsHostPanelLayout();
    }

    protected abstract TaskToggleButtonsHostPanel createTaskToggleButtonsHostPanel();

    protected abstract BandHostPanel createBandHostPanel();

    protected LayoutManager createBandHostPanelLayoutManager() {
        return new BandHostPanelLayout();
    }

    /**
     * Uninstalls subcomponents from the associated ribbon.
     */
    protected void uninstallComponents() {
        BandHostPanel bandHostPanel = this.bandScrollablePanel.getView();
        bandHostPanel.removeAll();
        bandHostPanel.setLayout(null);
        this.ribbon.remove(this.bandScrollablePanel);

        TaskToggleButtonsHostPanel taskToggleButtonsHostPanel = this.taskToggleButtonsScrollablePanel
                .getView();
        taskToggleButtonsHostPanel.removeAll();
        taskToggleButtonsHostPanel.setLayout(null);
        this.ribbon.remove(this.taskToggleButtonsScrollablePanel);

        this.ribbon.remove(this.applicationMenuButton);
        if (this.anchoredButtons != null) {
            this.ribbon.remove(this.anchoredButtons);
        }

        this.ribbon.setLayout(null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.plaf.ComponentUI#update(java.awt.Graphics, javax.swing.JComponent)
     */
    @Override
    public void update(Graphics g, JComponent c) {
        Graphics2D g2d = (Graphics2D) g.create();
        NeonCortex.installDesktopHints(g2d, c);
        super.update(g2d, c);
        g2d.dispose();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.plaf.ComponentUI#paint(java.awt.Graphics, javax.swing.JComponent)
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        this.paintBackground(g);

        if (!ribbon.isMinimized()) {
            Insets ins = c.getInsets();
            int extraHeight = getTaskToggleButtonHeight();
            this.paintTaskArea(g, 0, ins.top + extraHeight, c.getWidth(),
                    c.getHeight() - extraHeight - ins.top - ins.bottom);
        } else {
            this.paintMinimizedRibbonSeparator(g);
        }
    }

    protected abstract void paintMinimizedRibbonSeparator(Graphics g);

    /**
     * Paints the ribbon background.
     * 
     * @param g
     *            Graphics context.
     */
    protected abstract void paintBackground(Graphics g);

    /**
     * Paints the task border.
     * 
     * @param g
     *            Graphics context.
     * @param x
     *            Left X of the tasks band bounds.
     * @param y
     *            Top Y of the tasks band bounds.
     * @param width
     *            Width of the tasks band bounds.
     * @param height
     *            Height of the tasks band bounds.
     */
    protected abstract void paintTaskArea(Graphics g, int x, int y, int width, int height);

    @Override
    public Rectangle getContextualTaskGroupBounds(RibbonContextualTaskGroup group) {
        Rectangle rect = null;
        for (int j = 0; j < group.getTaskCount(); j++) {
            JRibbonTaskToggleButton button = taskToggleButtons.get(group.getTask(j));
            if (rect == null)
                rect = button.getBounds();
            else
                rect = rect.union(button.getBounds());
        }
        int buttonGap = getTabButtonGap();
        Point location = SwingUtilities.convertPoint(taskToggleButtonsScrollablePanel.getView(),
                rect.getLocation(), ribbon);
        return new Rectangle(location.x - buttonGap / 3, location.y - 1,
                rect.width + buttonGap * 2 / 3 - 1, rect.height + 1);
    }

    /**
     * Returns the layout gap for the bands in the associated ribbon.
     * 
     * @return The layout gap for the bands in the associated ribbon.
     */
    protected int getBandGap() {
        return FlamingoUtilities.getScaledSize(2, this.ribbon.getFont().getSize(), 0.2, 1);
    }

    /**
     * Returns the layout gap for the tab buttons in the associated ribbon.
     * 
     * @return The layout gap for the tab buttons in the associated ribbon.
     */
    protected int getTabButtonGap() {
        return FlamingoUtilities.getScaledSize(6, this.ribbon.getFont().getSize(), 0.25, 1);
    }

    /**
     * Invoked by <code>installUI</code> to create a layout manager object to manage the
     * {@link JRibbon}.
     * 
     * @return a layout manager object
     */
    protected LayoutManager createLayoutManager() {
        return new RibbonLayout();
    }

    /**
     * Returns the height of the taskbar area.
     * 
     * @return The height of the taskbar area.
     */
    public abstract int getTaskbarHeight();

    /**
     * Returns the height of the task toggle button area.
     * 
     * @return The height of the task toggle button area.
     */
    public abstract int getTaskToggleButtonHeight();

    /**
     * Layout for the ribbon.
     * 
     * @author Kirill Grouchnikov
     */
    private class RibbonLayout implements LayoutManager {
        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#addLayoutComponent(java.lang.String, java.awt.Component)
         */
        public void addLayoutComponent(String name, Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#removeLayoutComponent(java.awt.Component)
         */
        public void removeLayoutComponent(Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#preferredLayoutSize(java.awt.Container)
         */
        public Dimension preferredLayoutSize(Container c) {
            Insets ins = c.getInsets();
            int maxPrefBandHeight = 0;
            boolean isRibbonMinimized = ribbon.isMinimized();
            if (!isRibbonMinimized) {
                if (ribbon.getTaskCount() > 0) {
                    RibbonTask selectedTask = ribbon.getSelectedTask();
                    for (AbstractRibbonBand<?> ribbonBand : selectedTask.getBands()) {
                        int bandPrefHeight = ribbonBand.getPreferredSize().height;
                        Insets bandInsets = ribbonBand.getInsets();
                        maxPrefBandHeight = Math.max(maxPrefBandHeight,
                                bandPrefHeight + bandInsets.top + bandInsets.bottom);
                    }
                }
            }

            int extraHeight = getTaskToggleButtonHeight();
            int prefHeight = maxPrefBandHeight + extraHeight + ins.top + ins.bottom;
            // System.out.println("Ribbon pref = " + prefHeight);
            return new Dimension(c.getWidth(), prefHeight);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#minimumLayoutSize(java.awt.Container)
         */
        public Dimension minimumLayoutSize(Container c) {
            // go over all ribbon bands and sum the width
            // of ribbon buttons (of collapsed state)
            Insets ins = c.getInsets();
            int width = 0;
            int maxMinBandHeight = 0;
            int gap = getBandGap();

            int extraHeight = getTaskToggleButtonHeight();

            if (ribbon.getTaskCount() > 0) {
                boolean isRibbonMinimized = ribbon.isMinimized();
                // minimum is when all the tasks are collapsed
                RibbonTask selectedTask = ribbon.getSelectedTask();
                for (AbstractRibbonBand ribbonBand : selectedTask.getBands()) {
                    int bandPrefHeight = ribbonBand.getMinimumSize().height;
                    Insets bandInsets = ribbonBand.getInsets();
                    RibbonBandUI bandUI = ribbonBand.getUI();
                    width += bandUI.getPreferredCollapsedWidth();
                    if (!isRibbonMinimized) {
                        maxMinBandHeight = Math.max(maxMinBandHeight,
                                bandPrefHeight + bandInsets.top + bandInsets.bottom);
                    }
                }
                // add inter-band gaps
                width += gap * (selectedTask.getBandCount() - 1);
            } else {
                // fix for issue 44 (empty ribbon)
                width = 50;
            }
            return new Dimension(width, maxMinBandHeight + extraHeight + ins.top + ins.bottom);
        }

        private int getAnchoredButtonsWidth(CommandButtonDisplayState state) {
            int result = 0;

            for (Component comp : anchoredButtons.getComponents()) {
                AbstractCommandButton anchoredButton = (AbstractCommandButton) comp;
                result += state.createLayoutManager(anchoredButton)
                        .getPreferredSize(anchoredButton).width;
            }

            return result;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#layoutContainer(java.awt.Container)
         */
        public void layoutContainer(Container c) {
            // System.out.println("Ribbon real = " + c.getHeight());

            Insets ins = c.getInsets();
            int tabButtonGap = getTabButtonGap();

            boolean ltr = ribbon.getComponentOrientation().isLeftToRight();

            // the top row - task bar components
            int width = c.getWidth();
            int taskbarHeight = getTaskbarHeight();
            int y = ins.top;

            int taskToggleButtonHeight = getTaskToggleButtonHeight();

            int x = ltr ? ins.left : width - ins.right;
            // the application menu button
            int appMenuButtonSize = taskbarHeight + taskToggleButtonHeight;
//            if (!isUsingTitlePane) {
//                applicationMenuButton.setVisible(ribbon.getApplicationMenu() != null);
//                if (ribbon.getApplicationMenu() != null) {
//                    if (ltr) {
//                        applicationMenuButton.setBounds(x, ins.top, appMenuButtonSize,
//                                appMenuButtonSize);
//                    } else {
//                        applicationMenuButton.setBounds(x - appMenuButtonSize, ins.top,
//                                appMenuButtonSize, appMenuButtonSize);
//                    }
//                }
//            } else {
//                applicationMenuButton.setVisible(false);
//            }
            x = ltr ? x + 2 : x - 2;
            boolean isShowingAppMenuButton = (FlamingoUtilities
                    .getApplicationMenuButton(SwingUtilities.getWindowAncestor(ribbon)) != null);
            if (isShowingAppMenuButton) {
                x = ltr ? x + appMenuButtonSize : x - appMenuButtonSize;
            }

            // how much horizontal space do anchored buttons need in expanded (text + icon) and
            // collapsed (icon only) modes?
            int anchoredButtonsCollapsedWidth = getAnchoredButtonsWidth(
                    CommandButtonDisplayState.SMALL);
            int anchoredButtonsExpandedWidth = getAnchoredButtonsWidth(
                    CommandButtonDisplayState.MEDIUM);

            // if anchored buttons are expanded, do we have enough horizontal space to display
            // the task toggle buttons in their preferred size (without cutting off on the sides
            // or kicking in the scrolling)?
            TaskToggleButtonsHostPanel taskToggleButtonsStrip = taskToggleButtonsScrollablePanel
                    .getView();
            taskToggleButtonsStrip.setPreferredSize(null);

            int fullPreferredContentWidth = ins.left + ins.right + 2
                    + (isShowingAppMenuButton ? appMenuButtonSize : 0)
                    + ((anchoredButtons.getComponentCount() > 0)
                            ? (anchoredButtonsExpandedWidth + tabButtonGap)
                            : 0)
                    + taskToggleButtonsStrip.getPreferredSize().width;

            int anchoredButtonPanelWidth = 0;
            if (fullPreferredContentWidth <= c.getWidth()) {
                // can fit everything with no cuts
                for (Component comp : anchoredButtons.getComponents()) {
                    AbstractCommandButton anchoredButton = (AbstractCommandButton) comp;
                    anchoredButton.setDisplayState(CommandButtonDisplayState.MEDIUM);
                }
                anchoredButtonPanelWidth = anchoredButtonsExpandedWidth;
            } else {
                // switch anchored buttons to icon-only mode
                for (Component comp : anchoredButtons.getComponents()) {
                    AbstractCommandButton anchoredButton = (AbstractCommandButton) comp;
                    anchoredButton.setDisplayState(CommandButtonDisplayState.SMALL);
                }
                anchoredButtonPanelWidth = anchoredButtonsCollapsedWidth;
            }

            if (anchoredButtons.getComponentCount() > 0) {
                // Note that here we're using the height of task toggle buttons so that all the
                // content in that row has consistent vertical size.
                if (ltr) {
                    anchoredButtons.setBounds(width - ins.right - anchoredButtonPanelWidth, y,
                            anchoredButtonPanelWidth, taskToggleButtonHeight);
                } else {
                    anchoredButtons.setBounds(ins.left, y, anchoredButtonPanelWidth,
                            taskToggleButtonHeight);
                }
                anchoredButtons.doLayout();
            }

            // task buttons
            if (ltr) {
                int taskButtonsWidth = (anchoredButtons.getComponentCount() > 0)
                        ? (anchoredButtons.getX() - tabButtonGap - x)
                        : (c.getWidth() - ins.right - x);
                taskToggleButtonsScrollablePanel.setBounds(x, y, taskButtonsWidth,
                        taskToggleButtonHeight);
            } else {
                int taskButtonsWidth = (anchoredButtons.getComponentCount() > 0)
                        ? (x - tabButtonGap - anchoredButtons.getX() - anchoredButtons.getWidth())
                        : (x - ins.left);
                taskToggleButtonsScrollablePanel.setBounds(x - taskButtonsWidth, y,
                        taskButtonsWidth, taskToggleButtonHeight);
            }

            TaskToggleButtonsHostPanel taskToggleButtonsHostPanel = taskToggleButtonsScrollablePanel
                    .getView();
            int taskToggleButtonsHostPanelMinWidth = taskToggleButtonsHostPanel
                    .getMinimumSize().width;
            taskToggleButtonsHostPanel
                    .setPreferredSize(new Dimension(taskToggleButtonsHostPanelMinWidth,
                            taskToggleButtonsScrollablePanel.getBounds().height));
            taskToggleButtonsScrollablePanel.doLayout();

            y += taskToggleButtonHeight;

            int extraHeight = taskToggleButtonHeight;

            if (bandScrollablePanel.getParent() == ribbon) {
                if (!ribbon.isMinimized() && (ribbon.getTaskCount() > 0)) {
                    // y += ins.top;
                    Insets bandInsets = (ribbon.getSelectedTask().getBandCount() == 0)
                            ? new Insets(0, 0, 0, 0)
                            : ribbon.getSelectedTask().getBand(0).getInsets();
                    bandScrollablePanel.setBounds(1 + ins.left, y + bandInsets.top,
                            c.getWidth() - 2 * ins.left - 2 * ins.right - 1,
                            c.getHeight() - extraHeight - ins.top - ins.bottom - bandInsets.top
                                    - bandInsets.bottom);
                    // System.out.println("Scrollable : "
                    // + bandScrollablePanel.getBounds());
                    BandHostPanel bandHostPanel = bandScrollablePanel.getView();
                    int bandHostPanelMinWidth = bandHostPanel.getMinimumSize().width;
                    bandHostPanel.setPreferredSize(new Dimension(bandHostPanelMinWidth,
                            bandScrollablePanel.getBounds().height));
                    bandScrollablePanel.doLayout();
                    bandHostPanel.doLayout();
                } else {
                    bandScrollablePanel.setBounds(0, 0, 0, 0);
                }
            }
        }
    }

    protected abstract static class BandHostPanel extends JPanel {
    }

    /**
     * Layout for the band host panel.
     * 
     * @author Kirill Grouchnikov
     */
    private class BandHostPanelLayout implements LayoutManager {
        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#addLayoutComponent(java.lang.String, java.awt.Component)
         */
        public void addLayoutComponent(String name, Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#removeLayoutComponent(java.awt.Component)
         */
        public void removeLayoutComponent(Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#preferredLayoutSize(java.awt.Container)
         */
        public Dimension preferredLayoutSize(Container c) {
            // Insets ins = c.getInsets();
            int maxPrefBandHeight = 0;
            if (ribbon.getTaskCount() > 0) {
                RibbonTask selectedTask = ribbon.getSelectedTask();
                for (AbstractRibbonBand<?> ribbonBand : selectedTask.getBands()) {
                    int bandPrefHeight = ribbonBand.getPreferredSize().height;
                    Insets bandInsets = ribbonBand.getInsets();
                    maxPrefBandHeight = Math.max(maxPrefBandHeight,
                            bandPrefHeight + bandInsets.top + bandInsets.bottom);
                }
            }

            return new Dimension(c.getWidth(), maxPrefBandHeight);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#minimumLayoutSize(java.awt.Container)
         */
        public Dimension minimumLayoutSize(Container c) {
            // go over all ribbon bands and sum the width
            // of ribbon buttons (of collapsed state)
            // Insets ins = c.getInsets();
            int width = 0;
            int maxMinBandHeight = 0;
            int gap = getBandGap();

            // minimum is when all the tasks are collapsed
            RibbonTask selectedTask = ribbon.getSelectedTask();
            // System.out.println(selectedTask.getTitle() + " min width");
            for (AbstractRibbonBand ribbonBand : selectedTask.getBands()) {
                int bandPrefHeight = ribbonBand.getMinimumSize().height;
                Insets bandInsets = ribbonBand.getInsets();
                RibbonBandUI bandUI = ribbonBand.getUI();
                int preferredCollapsedWidth = bandUI.getPreferredCollapsedWidth() + bandInsets.left
                        + bandInsets.right;
                width += preferredCollapsedWidth;
                // System.out.println("\t" + ribbonBand.getTitle() + ":" +
                // preferredCollapsedWidth);
                maxMinBandHeight = Math.max(maxMinBandHeight, bandPrefHeight
                // + bandInsets.top + bandInsets.bottom
                );
            }
            // add inter-band gaps
            width += gap * (selectedTask.getBandCount() + 1);
            // System.out.println("\t" + gap + "*" +
            // (selectedTask.getBandCount() + 1));

            // System.out.println(selectedTask.getTitle() + " min width:" +
            // width);

            // System.out.println("Returning min height of " +
            // maxMinBandHeight);

            return new Dimension(width, maxMinBandHeight);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#layoutContainer(java.awt.Container)
         */
        public void layoutContainer(Container c) {
            // System.err.println("Layout of band host panel " + c.getWidth() +
            // ":" + c.getHeight());
            int bandGap = getBandGap();

            // the top row - task bar components
            int x = 0;
            int y = 0;

            RibbonTask selectedTask = ribbon.getSelectedTask();
            if (selectedTask == null)
                return;

            // check that the resize policies are still consistent
            for (AbstractRibbonBand<?> band : selectedTask.getBands()) {
                FlamingoUtilities.checkResizePoliciesConsistency(band);
            }

            // start with the most "permissive" resize policy for each band
            for (AbstractRibbonBand<?> band : selectedTask.getBands()) {
                List<RibbonBandResizePolicy> policies = band.getResizePolicies();
                RibbonBandResizePolicy last = policies.get(0);
                band.setCurrentResizePolicy(last);
            }

            int availableBandHeight = c.getHeight();
            int availableWidth = c.getWidth();
            if (selectedTask.getBandCount() > 0) {
                RibbonBandResizeSequencingPolicy resizeSequencingPolicy = selectedTask
                        .getResizeSequencingPolicy();
                resizeSequencingPolicy.reset();
                AbstractRibbonBand<?> currToTakeFrom = resizeSequencingPolicy.next();
                while (true) {
                    // check whether all bands have the current resize
                    // policy as their last (most restrictive) registered policy
                    boolean noMore = true;
                    for (AbstractRibbonBand<?> band : selectedTask.getBands()) {
                        RibbonBandResizePolicy currentResizePolicy = band.getCurrentResizePolicy();
                        List<RibbonBandResizePolicy> resizePolicies = band.getResizePolicies();
                        if (currentResizePolicy != resizePolicies.get(resizePolicies.size() - 1)) {
                            noMore = false;
                            break;
                        }
                    }
                    if (noMore)
                        break;

                    // get the current preferred width of the bands
                    int totalWidth = 0;
                    // System.out.println("Iteration");
                    for (AbstractRibbonBand<?> ribbonBand : selectedTask.getBands()) {
                        RibbonBandResizePolicy currentResizePolicy = ribbonBand
                                .getCurrentResizePolicy();

                        Insets ribbonBandInsets = ribbonBand.getInsets();
                        AbstractBandControlPanel controlPanel = ribbonBand.getControlPanel();
                        if (controlPanel == null) {
                            controlPanel = ribbonBand.getPopupRibbonBand().getControlPanel();
                        }
                        Insets controlPanelInsets = controlPanel.getInsets();
                        int controlPanelGap = controlPanel.getUI().getLayoutGap();
                        int ribbonBandHeight = availableBandHeight - ribbonBandInsets.top
                                - ribbonBandInsets.bottom;
                        int availableHeight = ribbonBandHeight
                                - ribbonBand.getUI().getBandTitleHeight();
                        if (controlPanel != null) {
                            availableHeight = availableHeight - controlPanelInsets.top
                                    - controlPanelInsets.bottom;
                        }
                        int preferredWidth = currentResizePolicy.getPreferredWidth(availableHeight,
                                controlPanelGap) + ribbonBandInsets.left + ribbonBandInsets.right;
                        totalWidth += preferredWidth + bandGap;
                        // System.out.println("\t"
                        // + ribbonBand.getTitle()
                        // + ":"
                        // + currentResizePolicy.getClass()
                        // .getSimpleName() + ":" + preferredWidth
                        // + " under " + availableHeight + " with "
                        // + controlPanel.getComponentCount()
                        // + " children");
                    }
                    // System.out.println("\t:Total:" + totalWidth + "("
                    // + availableWidth + ")");
                    // System.out.println("\n");
                    if (totalWidth < availableWidth)
                        break;

                    // try to take from the currently rotating band
                    List<RibbonBandResizePolicy> policies = currToTakeFrom.getResizePolicies();
                    int currPolicyIndex = policies.indexOf(currToTakeFrom.getCurrentResizePolicy());
                    if (currPolicyIndex == (policies.size() - 1)) {
                        // nothing to take
                    } else {
                        currToTakeFrom.setCurrentResizePolicy(policies.get(currPolicyIndex + 1));
                    }
                    currToTakeFrom = resizeSequencingPolicy.next();
                }
            }

            boolean ltr = c.getComponentOrientation().isLeftToRight();
            x = ltr ? 1 : c.getWidth() - 1;
            // System.out.println("Will get [" + availableWidth + "]:");
            for (AbstractRibbonBand<?> ribbonBand : selectedTask.getBands()) {
                Insets ribbonBandInsets = ribbonBand.getInsets();
                RibbonBandResizePolicy currentResizePolicy = ribbonBand.getCurrentResizePolicy();
                AbstractBandControlPanel controlPanel = ribbonBand.getControlPanel();
                if (controlPanel == null) {
                    controlPanel = ribbonBand.getPopupRibbonBand().getControlPanel();
                }
                Insets controlPanelInsets = controlPanel.getInsets();
                int controlPanelGap = controlPanel.getUI().getLayoutGap();
                int ribbonBandHeight = availableBandHeight;
                // - ribbonBandInsets.top - ribbonBandInsets.bottom;
                int availableHeight = ribbonBandHeight - ribbonBandInsets.top
                        - ribbonBandInsets.bottom - ribbonBand.getUI().getBandTitleHeight();
                if (controlPanelInsets != null) {
                    availableHeight = availableHeight - controlPanelInsets.top
                            - controlPanelInsets.bottom;
                }

                int requiredBandWidth = currentResizePolicy.getPreferredWidth(availableHeight,
                        controlPanelGap) + ribbonBandInsets.left + ribbonBandInsets.right;

                if (ltr) {
                    ribbonBand.setBounds(x, y, requiredBandWidth, ribbonBandHeight);
                } else {
                    ribbonBand.setBounds(x - requiredBandWidth, y, requiredBandWidth,
                            ribbonBandHeight);
                }

                // System.out.println("\t" + ribbonBand.getTitle() + ":"
                // + currentResizePolicy.getClass().getSimpleName() + ":"
                // + requiredBandWidth + "[insets " + ribbonBandInsets.left + "," +
                // ribbonBandInsets.right + "] under " + ribbonBandHeight);

                if (ribbonBand.getHeight() > 0) {
                    ribbonBand.doLayout();
                }

                if (ltr) {
                    x += (requiredBandWidth + bandGap);
                } else {
                    x -= (requiredBandWidth + bandGap);
                }

            }
            // System.out.println();
        }
    }

    protected abstract class TaskToggleButtonsHostPanel extends JPanel {
        public static final String IS_SQUISHED = "flamingo.internal.ribbon.taskToggleButtonsHostPanel.isSquished";

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            this.paintContextualTaskGroupsOutlines(g);
            if (Boolean.TRUE.equals(this.getClientProperty(IS_SQUISHED))) {
                this.paintTaskOutlines(g);
            }
        }

        protected abstract void paintTaskOutlines(Graphics g);

        /**
         * Paints the outline of the contextual task groups.
         * 
         * @param g
         *            Graphics context.
         */
        protected void paintContextualTaskGroupsOutlines(Graphics g) {
            for (int i = 0; i < ribbon.getContextualTaskGroupCount(); i++) {
                RibbonContextualTaskGroup group = ribbon.getContextualTaskGroup(i);
                if (!ribbon.isVisible(group))
                    continue;
                // go over all the tasks in this group and compute the union
                // of bounds of the matching tab buttons
                Rectangle rect = getContextualTaskGroupBounds(group);
                rect.setLocation(SwingUtilities.convertPoint(ribbon, rect.getLocation(),
                        taskToggleButtonsScrollablePanel.getView()));
                this.paintContextualTaskGroupOutlines(g, group, rect);
            }
        }

        /**
         * Paints the outline of the specified contextual task group.
         * 
         * @param g
         *            Graphics context.
         * @param group
         *            Contextual task group.
         * @param groupBounds
         *            Contextual task group bounds.
         */
        protected abstract void paintContextualTaskGroupOutlines(Graphics g, RibbonContextualTaskGroup group,
                Rectangle groupBounds);

        // @Override
        // protected void paintComponent(Graphics g) {
        // //g.setColor(new Color(255, 200, 200));
        // //g.fillRect(0, 0, getWidth(), getHeight());
        // // g.setColor(Color.blue.darker());
        // // g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        // //System.err.println(System.currentTimeMillis() + ": tt-repaint");
        // }
        //
        // @Override
        // protected void paintBorder(Graphics g) {
        // }
        //
        // @Override
        // public void setBounds(int x, int y, int width, int height) {
        // System.out.println("Host : " + x + ":" + y + ":" + width + ":"
        // + height);
        // super.setBounds(x, y, width, height);
        // }
    }

    /**
     * Layout for the band host panel.
     * 
     * @author Kirill Grouchnikov
     */
    private class TaskToggleButtonsHostPanelLayout implements LayoutManager {
        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#addLayoutComponent(java.lang.String, java.awt.Component)
         */
        public void addLayoutComponent(String name, Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#removeLayoutComponent(java.awt.Component)
         */
        public void removeLayoutComponent(Component c) {
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#preferredLayoutSize(java.awt.Container)
         */
        public Dimension preferredLayoutSize(Container c) {
            int tabButtonGap = getTabButtonGap();
            int taskToggleButtonHeight = getTaskToggleButtonHeight();

            int totalTaskButtonsWidth = 0;
            List<RibbonTask> visibleTasks = getCurrentlyShownRibbonTasks();
            for (RibbonTask task : visibleTasks) {
                JRibbonTaskToggleButton tabButton = taskToggleButtons.get(task);
                int pw = tabButton.getPreferredSize().width;
                totalTaskButtonsWidth += (pw + tabButtonGap);
            }

            return new Dimension(totalTaskButtonsWidth, taskToggleButtonHeight);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#minimumLayoutSize(java.awt.Container)
         */
        public Dimension minimumLayoutSize(Container c) {
            int tabButtonGap = getTabButtonGap();
            int taskToggleButtonHeight = getTaskToggleButtonHeight();

            int totalTaskButtonsWidth = 0;
            List<RibbonTask> visibleTasks = getCurrentlyShownRibbonTasks();
            for (RibbonTask task : visibleTasks) {
                JRibbonTaskToggleButton tabButton = taskToggleButtons.get(task);
                int pw = tabButton.getMinimumSize().width;
                totalTaskButtonsWidth += (pw + tabButtonGap);
            }

            return new Dimension(totalTaskButtonsWidth, taskToggleButtonHeight);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.awt.LayoutManager#layoutContainer(java.awt.Container)
         */
        public void layoutContainer(Container c) {
            int y = 0;
            int tabButtonGap = getTabButtonGap();
            int taskToggleButtonHeight = getTaskToggleButtonHeight();

            int totalPrefWidth = 0;
            int totalMinWidth = 0;
            List<RibbonTask> visibleTasks = getCurrentlyShownRibbonTasks();
            Map<JRibbonTaskToggleButton, Integer> diffMap = new HashMap<JRibbonTaskToggleButton, Integer>();
            int totalDiff = 0;
            for (RibbonTask task : visibleTasks) {
                JRibbonTaskToggleButton tabButton = taskToggleButtons.get(task);
                int pw = tabButton.getPreferredSize().width;
                int mw = tabButton.getMinimumSize().width;
                diffMap.put(tabButton, pw - mw);
                totalDiff += (pw - mw);
                totalPrefWidth += pw;
                totalMinWidth += mw;
            }
            totalPrefWidth += tabButtonGap * visibleTasks.size();
            totalMinWidth += tabButtonGap * visibleTasks.size();

            boolean ltr = c.getComponentOrientation().isLeftToRight();

            // do we have enough width?
            if (totalPrefWidth <= c.getWidth()) {
                // compute bounds for the tab buttons
                int x = ltr ? 0 : c.getWidth();
                for (RibbonTask task : visibleTasks) {
                    JRibbonTaskToggleButton tabButton = taskToggleButtons.get(task);
                    int pw = tabButton.getPreferredSize().width;
                    if (ltr) {
                        tabButton.setBounds(x, y + 1, pw, taskToggleButtonHeight - 1);
                        x += (pw + tabButtonGap);
                    } else {
                        tabButton.setBounds(x - pw, y + 1, pw, taskToggleButtonHeight - 1);
                        x -= (pw + tabButtonGap);
                    }
                    tabButton.setActionRichTooltip(null);
                }
                ((JComponent) c).putClientProperty(TaskToggleButtonsHostPanel.IS_SQUISHED, null);
            } else {
                if (totalMinWidth > c.getWidth()) {
                    throw new IllegalStateException(
                            "Available width not enough to host minimized task tab buttons");
                }
                int x = ltr ? 0 : c.getWidth();
                // how much do we need to take from each toggle button?
                int toDistribute = totalPrefWidth - c.getWidth() + 2;
                for (RibbonTask task : visibleTasks) {
                    JRibbonTaskToggleButton tabButton = taskToggleButtons.get(task);
                    int pw = tabButton.getPreferredSize().width;
                    int delta = (toDistribute * diffMap.get(tabButton) / totalDiff);
                    int finalWidth = pw - delta;
                    if (ltr) {
                        tabButton.setBounds(x, y + 1, finalWidth, taskToggleButtonHeight - 1);
                        x += (finalWidth + tabButtonGap);
                    } else {
                        tabButton.setBounds(x - finalWidth, y + 1, finalWidth,
                                taskToggleButtonHeight - 1);
                        x -= (finalWidth + tabButtonGap);
                    }
                    // show the tooltip with the full title
                    tabButton.setActionRichTooltip(
                            new RichTooltip.RichTooltipBuilder().setTitle(task.getTitle()).build());
                }
                ((JComponent) c).putClientProperty(TaskToggleButtonsHostPanel.IS_SQUISHED,
                        Boolean.TRUE);
            }
        }
    }

    private class AnchoredButtonsPanelLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            int minWidth = 0;
            for (Component comp : parent.getComponents()) {
                minWidth += comp.getMinimumSize().width;
            }
            return new Dimension(minWidth, getTaskToggleButtonHeight());
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int prefWidth = 0;
            for (Component comp : parent.getComponents()) {
                prefWidth += comp.getPreferredSize().width;
            }
            return new Dimension(prefWidth, getTaskToggleButtonHeight());
        }

        @Override
        public void layoutContainer(Container parent) {
            boolean ltr = ribbon.getComponentOrientation().isLeftToRight();
            if (ltr) {
                int x = 0;
                for (Component comp : parent.getComponents()) {
                    int prefWidth = comp.getPreferredSize().width;
                    comp.setBounds(x, 0, prefWidth, parent.getHeight());
                    x += prefWidth;
                }
            } else {
                int x = parent.getWidth();
                for (Component comp : parent.getComponents()) {
                    int prefWidth = comp.getPreferredSize().width;
                    comp.setBounds(x - prefWidth, 0, prefWidth, parent.getHeight());
                    x -= prefWidth;
                }
            }
        }

    }

    protected void syncRibbonState() {
        // remove all existing ribbon bands
        BandHostPanel bandHostPanel = this.bandScrollablePanel.getView();
        bandHostPanel.removeAll();

        // remove all the existing task toggle buttons
        TaskToggleButtonsHostPanel taskToggleButtonsHostPanel = this.taskToggleButtonsScrollablePanel
                .getView();
        taskToggleButtonsHostPanel.removeAll();

        // remove the anchored buttons
        if (this.anchoredButtons != null) {
            this.ribbon.remove(this.anchoredButtons);
            this.anchoredButtons = null;
        }

        // go over all visible ribbon tasks and create a toggle button
        // for each one of them
        List<RibbonTask> visibleTasks = this.getCurrentlyShownRibbonTasks();
        final RibbonTask selectedTask = this.ribbon.getSelectedTask();
        for (final RibbonTask task : visibleTasks) {
            final JRibbonTaskToggleButton taskToggleButton = new JRibbonTaskToggleButton(task);
            taskToggleButton.setKeyTip(task.getKeyTip());
            // wire listener to select the task when the button is
            // selected
            taskToggleButton.addActionListener((ActionEvent e) -> {
                SwingUtilities.invokeLater(() -> {
                    scrollAndRevealTaskToggleButton(taskToggleButton);

                    ribbon.setSelectedTask(task);

                    // System.out.println("Button click on "
                    // + task.getTitle() + ", ribbon minimized? "
                    // + ribbon.isMinimized());

                    if (ribbon.isMinimized()) {
                        if (Boolean.TRUE.equals(ribbon.getClientProperty(JUST_MINIMIZED))) {
                            ribbon.putClientProperty(JUST_MINIMIZED, null);
                            return;
                        }

                        // special case - do we have this task currently
                        // shown in a popup?
                        List<PopupPanelManager.PopupInfo> popups = PopupPanelManager
                                .defaultManager().getShownPath();
                        if (popups.size() > 0) {
                            for (PopupPanelManager.PopupInfo popup : popups) {
                                if (popup.getPopupOriginator() == taskToggleButton) {
                                    // hide all popups and return (hides
                                    // the task popup and does not
                                    // show any additional popup).
                                    PopupPanelManager.defaultManager().hidePopups(null);
                                    return;
                                }
                            }
                        }

                        PopupPanelManager.defaultManager().hidePopups(null);
                        ribbon.remove(bandScrollablePanel);

                        int prefHeight = bandScrollablePanel.getView().getPreferredSize().height;
                        Insets ins = ribbon.getInsets();
                        prefHeight += ins.top + ins.bottom;
                        AbstractRibbonBand band = (ribbon.getSelectedTask().getBandCount() > 0)
                                ? ribbon.getSelectedTask().getBand(0)
                                : null;
                        if (band != null) {
                            Insets bandIns = band.getInsets();
                            prefHeight += bandIns.top + bandIns.bottom;
                        }

                        // System.out.println(prefHeight
                        // + ":"
                        // + bandScrollablePanel.getView()
                        // .getComponentCount());

                        JPopupPanel popupPanel = new BandHostPopupPanel(bandScrollablePanel,
                                new Dimension(ribbon.getWidth(), prefHeight));

                        int x = ribbon.getLocationOnScreen().x;
                        int y = ribbon.getLocationOnScreen().y + ribbon.getHeight();

                        // make sure that the popup stays in
                        // bounds
                        Rectangle scrBounds = ribbon.getGraphicsConfiguration().getBounds();
                        int pw = popupPanel.getPreferredSize().width;
                        if ((x + pw) > (scrBounds.x + scrBounds.width)) {
                            x = scrBounds.x + scrBounds.width - pw;
                        }
                        int ph = popupPanel.getPreferredSize().height;
                        if ((y + ph) > (scrBounds.y + scrBounds.height)) {
                            y = scrBounds.y + scrBounds.height - ph;
                        }

                        // get the popup and show it
                        popupPanel.setPreferredSize(new Dimension(ribbon.getWidth(), prefHeight));
                        Popup popup = PopupFactory.getSharedInstance().getPopup(taskToggleButton,
                                popupPanel, x, y);
                        PopupPanelManager.PopupListener tracker = new PopupPanelManager.PopupListener() {
                            @Override
                            public void popupShown(PopupEvent event) {
                                JComponent originator = event.getPopupOriginator();
                                if (originator instanceof JRibbonTaskToggleButton) {
                                    bandScrollablePanel.doLayout();
                                    bandScrollablePanel.repaint();
                                }
                            }

                            @Override
                            public void popupHidden(PopupEvent event) {
                                JComponent originator = event.getPopupOriginator();
                                if (originator instanceof JRibbonTaskToggleButton) {
                                    ribbon.add(bandScrollablePanel);
                                    PopupPanelManager.defaultManager().removePopupListener(this);
                                    ribbon.revalidate();
                                    ribbon.doLayout();
                                    ribbon.repaint();
                                }
                            }
                        };
                        PopupPanelManager.defaultManager().addPopupListener(tracker);
                        PopupPanelManager.defaultManager().addPopup(taskToggleButton, popup,
                                popupPanel);
                    }
                });
            });
            // wire listener to toggle ribbon minimization on double
            // mouse click
            taskToggleButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ((ribbon.getSelectedTask() == task) && (e.getClickCount() == 2)) {
                        boolean wasMinimized = ribbon.isMinimized();
                        ribbon.setMinimized(!wasMinimized);
                        if (!wasMinimized) {
                            // fix for issue 69 - mark the ribbon as
                            // "just minimized" to prevent the action handler
                            // of the toggle button to show the ribbon in
                            // popup mode
                            ribbon.putClientProperty(JUST_MINIMIZED, Boolean.TRUE);
                        }
                    }
                }
            });
            // set the background hue color on the tab buttons
            // of tasks in contextual groups
            if (task.getContextualGroup() != null) {
                taskToggleButton
                        .setContextualGroupHueColor(task.getContextualGroup().getHueColor());
            }

            taskToggleButton.putClientProperty(BasicCommandButtonUI.DONT_DISPOSE_POPUPS,
                    Boolean.TRUE);

            this.taskToggleButtonGroup.add(taskToggleButton);
            taskToggleButtonsHostPanel.add(taskToggleButton);
            this.taskToggleButtons.put(task, taskToggleButton);
        }

        JRibbonTaskToggleButton toSelect = this.taskToggleButtons.get(selectedTask);
        if (toSelect != null) {
            toSelect.getActionModel().setSelected(true);
        }

        for (int i = 0; i < this.ribbon.getTaskCount(); i++) {
            RibbonTask task = this.ribbon.getTask(i);
            for (AbstractRibbonBand band : task.getBands()) {
                bandHostPanel.add(band);
                band.setVisible(selectedTask == task);
            }
        }
        for (int i = 0; i < this.ribbon.getContextualTaskGroupCount(); i++) {
            RibbonContextualTaskGroup taskGroup = this.ribbon.getContextualTaskGroup(i);
            for (int j = 0; j < taskGroup.getTaskCount(); j++) {
                RibbonTask task = taskGroup.getTask(j);
                for (AbstractRibbonBand band : task.getBands()) {
                    bandHostPanel.add(band);
                    band.setVisible(selectedTask == task);
                }
            }
        }

        List<FlamingoCommand> anchoredCommands = this.ribbon.getAnchoredCommands();
        if (anchoredCommands != null) {
            this.anchoredButtons = new Container();
            this.anchoredButtons.setLayout(new AnchoredButtonsPanelLayout());
            for (FlamingoCommand anchoredCommand : anchoredCommands) {
                this.anchoredButtons.add(anchoredCommand.buildButton());
            }
            this.ribbon.add(this.anchoredButtons);
        }

        this.ribbon.revalidate();
        this.ribbon.repaint();
    }

    /**
     * Returns the list of currently shown ribbon tasks. This method is for internal use only.
     * 
     * @return The list of currently shown ribbon tasks.
     */
    protected List<RibbonTask> getCurrentlyShownRibbonTasks() {
        List<RibbonTask> result = new ArrayList<RibbonTask>();

        // add all regular tasks
        for (int i = 0; i < this.ribbon.getTaskCount(); i++) {
            RibbonTask task = this.ribbon.getTask(i);
            result.add(task);
        }
        // add all tasks of visible contextual groups
        for (int i = 0; i < this.ribbon.getContextualTaskGroupCount(); i++) {
            RibbonContextualTaskGroup group = this.ribbon.getContextualTaskGroup(i);
            if (this.ribbon.isVisible(group)) {
                for (int j = 0; j < group.getTaskCount(); j++) {
                    RibbonTask task = group.getTask(j);
                    result.add(task);
                }
            }
        }

        return result;
    }

    protected abstract void syncApplicationMenuTips();

    @Override
    public boolean isShowingScrollsForTaskToggleButtons() {
        return this.taskToggleButtonsScrollablePanel.isShowingScrollButtons();
    }

    @Override
    public boolean isShowingScrollsForBands() {
        return this.bandScrollablePanel.isShowingScrollButtons();
    }

    public Map<RibbonTask, JRibbonTaskToggleButton> getTaskToggleButtons() {
        return Collections.unmodifiableMap(taskToggleButtons);
    }

    public List<JCommandButton> getAnchoredCommandButtons() {
        List<JCommandButton> result = new ArrayList<>();
        for (Component anchored : this.anchoredButtons.getComponents()) {
            result.add((JCommandButton) anchored);
        }
        return Collections.unmodifiableList(result);
    }

    protected static class BandHostPopupPanel extends JPopupPanel {
        /**
         * The main component of <code>this</code> popup panel. Can be <code>null</code>.
         */
        // protected Component component;
        public BandHostPopupPanel(Component component, Dimension originalSize) {
            // this.component = component;
            this.setLayout(new BorderLayout());
            this.add(component, BorderLayout.CENTER);
            // System.out.println("Popup dim is " + originalSize);
            this.setPreferredSize(originalSize);
            this.setSize(originalSize);
        }
    }

    @Override
    public void handleMouseWheelEvent(MouseWheelEvent e) {
        // no mouse wheel scrolling when the ribbon is minimized
        if (ribbon.isMinimized())
            return;

        // get the visible tasks
        final List<RibbonTask> visibleTasks = getCurrentlyShownRibbonTasks();
        if (visibleTasks.size() == 0)
            return;

        int delta = e.getWheelRotation();
        if (delta == 0)
            return;

        // find the index of the currently selected task
        int currSelectedTaskIndex = visibleTasks.indexOf(ribbon.getSelectedTask());

        // compute the next task
        if (!ribbon.getComponentOrientation().isLeftToRight())
            delta = -delta;
        int newSelectedTaskIndex = currSelectedTaskIndex + ((delta > 0) ? 1 : -1);
        if (newSelectedTaskIndex < 0)
            return;
        if (newSelectedTaskIndex >= visibleTasks.size())
            return;

        final int indexToSet = newSelectedTaskIndex;
        SwingUtilities.invokeLater(() -> {
            ribbon.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            ribbon.setSelectedTask(visibleTasks.get(indexToSet));
            ribbon.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        });
    }

    protected void scrollAndRevealTaskToggleButton(final JRibbonTaskToggleButton taskToggleButton) {
        // scroll the viewport of the scrollable panel
        // so that the button is fully viewed.
        Point loc = SwingUtilities.convertPoint(taskToggleButton.getParent(),
                taskToggleButton.getLocation(), taskToggleButtonsScrollablePanel.getView());
        taskToggleButtonsScrollablePanel.scrollToIfNecessary(loc.x, taskToggleButton.getWidth());
    }
}
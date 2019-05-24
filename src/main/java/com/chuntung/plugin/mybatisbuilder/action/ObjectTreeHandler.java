/*
 * Copyright (c) 2019 Tony Ho. Some rights reserved.
 */

package com.chuntung.plugin.mybatisbuilder.action;

import com.chuntung.plugin.mybatisbuilder.MybatisBuilderService;
import com.chuntung.plugin.mybatisbuilder.model.DatabaseItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.SQLException;
import java.util.List;

public class ObjectTreeHandler {
    // icons
    private Icon connectionIcon = IconLoader.getIcon("/images/connection.png");
    private Icon databaseIcon = IconLoader.getIcon("/images/database.png");
    private Icon tableIcon = IconLoader.getIcon("/images/table.png");

    private Project project;
    private JTree objectTree;
    private TreePath currentTreePath;

    // for connection or database
    private AnAction refreshAction = new AnAction("Refresh") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            if (currentTreePath != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentTreePath.getLastPathComponent();
                loadSubNodes(node, true);
                objectTree.updateUI();
            }
        }
    };

    // just for connection
    private AnAction disconnectAction = new AnAction("Disconnect") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            if (currentTreePath != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentTreePath.getLastPathComponent();
                node.removeFromParent();
                objectTree.updateUI();
            }
        }
    };

    private final ActionGroup connectionPopupActionGroup;
    private final DefaultActionGroup databasePopupActionGroup;

    public ObjectTreeHandler(JTree objectTree, Project project) {
        this.project = project;
        this.objectTree = objectTree;

        connectionPopupActionGroup = new DefaultActionGroup(
                refreshAction,
                new Separator(),
                disconnectAction);

        databasePopupActionGroup = new DefaultActionGroup(
                refreshAction
        );
    }

    public static ObjectTreeHandler getInstance(JTree objectTree, Project project) {
        return new ObjectTreeHandler(objectTree, project);
    }

    // load sub nodes for connection or database
    private void loadSubNodes(DefaultMutableTreeNode node, boolean forced) {
        int count = node.getChildCount();
        if (forced || count == 0) {
            MybatisBuilderService service = MybatisBuilderService.getInstance(project);

            node.removeAllChildren();

            try {
                DatabaseItem item = (DatabaseItem) node.getUserObject();
                if (DatabaseItem.ItemTypeEnum.CONNECTION.equals(item.getType())) {
                    List<DatabaseItem> databaseItems = service.fetchDatabases(item.getId());
                    for (DatabaseItem dbItem : databaseItems) {
                        node.add(new DefaultMutableTreeNode(dbItem,
                                true));
                    }
                } else if (DatabaseItem.ItemTypeEnum.DATABASE.equals(item.getType())) {
                    DefaultMutableTreeNode connNode = (DefaultMutableTreeNode) node.getParent();
                    String connectionId = ((DatabaseItem) connNode.getUserObject()).getId();
                    List<DatabaseItem> databaseItems = service.fetchTables(connectionId, item.getName());
                    for (DatabaseItem dbItem : databaseItems) {
                        node.add(new DefaultMutableTreeNode(dbItem, false));
                    }
                }
            } catch (SQLException e) {
                Messages.showErrorDialog(e.getMessage(), "Database Error");
            }
        }
    }

    public TreeWillExpandListener getTreeWillExpandListener() {
        TreeWillExpandListener listener = new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getUserObject() instanceof DatabaseItem) {
                    loadSubNodes(node, false);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                // NOOP
            }
        };

        return listener;
    }

    public MouseListener getMouseListener() {
        MouseListener mouseListener = new MouseAdapter() {
            private void checkForPopup(MouseEvent e) {
                if (e.getSource() instanceof JTree && e.isPopupTrigger()) {
                    JTree source = (JTree) e.getSource();
                    Point point = e.getPoint();
                    TreePath path = source.getPathForLocation(point.x, point.y);

                    // save current tree path for popup action
                    currentTreePath = path;
                    if (path == null) {
                        e.consume();
                        return;
                    }

                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    DatabaseItem item = (DatabaseItem) node.getUserObject();

                    ActionGroup popupActionGroup = null;
                    if (DatabaseItem.ItemTypeEnum.CONNECTION.equals(item.getType())) {
                        popupActionGroup = connectionPopupActionGroup;
                    } else if (DatabaseItem.ItemTypeEnum.DATABASE.equals(item.getType())) {
                        popupActionGroup = databasePopupActionGroup;
                    }

                    if (popupActionGroup != null) {
                        ActionPopupMenu actionPopupMenu = ActionManager.getInstance().createActionPopupMenu("", popupActionGroup);
                        JPopupMenu popupMenu = actionPopupMenu.getComponent();
                        popupMenu.show(source, point.x, point.y);
                    }
                }
            }

            public void mousePressed(MouseEvent e) {
                checkForPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                checkForPopup(e);
            }

            public void mouseClicked(MouseEvent e) {
                checkForPopup(e);
            }
        };

        return mouseListener;
    }

    public TreeCellRenderer getTreeCellRenderer() {
        TreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded,
                                                          boolean leaf,
                                                          int row, boolean hasFocus) {
                if (value instanceof DefaultMutableTreeNode) {
                    Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObject instanceof DatabaseItem) {
                        DatabaseItem item = (DatabaseItem) userObject;
                        if (DatabaseItem.ItemTypeEnum.CONNECTION.equals(item.getType())) {
                            setOpenIcon(connectionIcon);
                            setClosedIcon(connectionIcon);
                        } else if (DatabaseItem.ItemTypeEnum.DATABASE.equals(item.getType())) {
                            setOpenIcon(databaseIcon);
                            setClosedIcon(databaseIcon);
                        } else if (DatabaseItem.ItemTypeEnum.TABLE.equals(item.getType())) {
                            setLeafIcon(tableIcon);
                        }
                    }
                }

                return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            }
        };

        return renderer;
    }
}
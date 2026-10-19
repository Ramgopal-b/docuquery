package com.sedin.docuquery;

import com.documentum.fc.client.IDfType;
import com.documentum.fc.common.IDfAttr;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Repository Navigator in the dqMan layout:
 * Folders (cabinets/folders/documents), Types (Object Types + Registered Tables),
 * Groups (Groups &amp; Roles + Groups &amp; Users panel), Users (All Users + Groups panel),
 * Favorites (Standard Favorites + User's Favorites).
 */
final class NavigatorPanel extends JPanel {

    static final class Item {
        String id;
        String name;
        String type;
        String contentType = "";
        String lockOwner = "";

        boolean isFolder() {
            return id != null && (id.startsWith("0b") || id.startsWith("0c"));
        }

        boolean isCabinet() {
            return id != null && id.startsWith("0c");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A principal (group, role or user) shown in the Groups / Users trees and lists. */
    static final class Principal {
        final String name;
        final String kind; // "group", "role", "user"
        final boolean inactive;

        Principal(String name, String kind, boolean inactive) {
            this.name = name;
            this.kind = kind;
            this.inactive = inactive;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final class RegTable {
        final String name;
        final String owner;
        final String table;

        RegTable(String name, String owner, String table) {
            this.name = name;
            this.owner = owner;
            this.table = table;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final class FavNode {
        final String name;
        final String dql;
        final boolean user;

        FavNode(String name, String dql, boolean user) {
            this.name = name;
            this.dql = dql;
            this.user = user;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String LOADING = "Loading...";
    private static final int MAX_CHILDREN = 5000;

    private final SessionPanel ctx;
    private final FlatTabs tabs = new FlatTabs(FlatTabs.Style.NAV);

    // Folders
    private final JTextField filter = new JTextField();
    private final DefaultMutableTreeNode folderRoot = new DefaultMutableTreeNode("Cabinets & Folders");
    private final DefaultTreeModel folderModel = new DefaultTreeModel(folderRoot);
    private final JTree folderTree = new JTree(folderModel);
    private final List<Item> cabinets = new ArrayList<>();
    private boolean cabinetsLoaded;

    // Types
    private final JTextField typeFilter = new JTextField();
    private final DefaultMutableTreeNode typeRoot = new DefaultMutableTreeNode("Types");
    private final DefaultMutableTreeNode objTypesNode = new DefaultMutableTreeNode("Object Types (dm_sysobject...)");
    private final DefaultMutableTreeNode regNode = new DefaultMutableTreeNode("Registered Tables");
    private final DefaultTreeModel typeModel = new DefaultTreeModel(typeRoot);
    private final JTree typeTree = new JTree(typeModel);
    private final Map<String, String> typeSuper = new TreeMap<>();
    private final List<RegTable> registered = new ArrayList<>();
    private boolean typesLoaded;
    private boolean regLoaded;

    // Groups
    private final JTextField groupFilter = new JTextField();
    private final DefaultMutableTreeNode groupRoot = new DefaultMutableTreeNode("Groups & Roles");
    private final DefaultTreeModel groupModel = new DefaultTreeModel(groupRoot);
    private final JTree groupTree = new JTree(groupModel);
    private final List<Principal> groupList = new ArrayList<>();
    private boolean groupsLoaded;
    private final JComboBox<String> memberKind = new JComboBox<>(new String[]{"All", "Users", "Groups"});
    private final JTextField memberFilter = new JTextField();
    private final DefaultListModel<Principal> memberModel = new DefaultListModel<>();
    private final JList<Principal> memberList = new JList<>(memberModel);
    private final List<Principal> members = new ArrayList<>();
    private String selectedGroup;

    // Users
    private final JTextField userFilter = new JTextField();
    private final DefaultMutableTreeNode userRoot = new DefaultMutableTreeNode("All Users");
    private final DefaultTreeModel userModel = new DefaultTreeModel(userRoot);
    private final JTree userTree = new JTree(userModel);
    private final List<Principal> userList = new ArrayList<>();
    private boolean usersLoaded;
    private final DefaultListModel<Principal> userGroupModel = new DefaultListModel<>();
    private final JList<Principal> userGroupList = new JList<>(userGroupModel);
    private String selectedUser;

    // Favorites
    private final JTextField favFilter = new JTextField();
    private final DefaultMutableTreeNode favRoot = new DefaultMutableTreeNode("Favorites");
    private final DefaultTreeModel favModel = new DefaultTreeModel(favRoot);
    private final JTree favTree = new JTree(favModel);

    NavigatorPanel(SessionPanel ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        setBackground(Theme.PANEL_BG);
        setBorder(new EmptyBorder(0, 2, 0, 0));

        // ---- header: "Repository Navigator" + red close square
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(4, 2, 4, 4));
        head.add(new JLabel("Repository Navigator"), BorderLayout.WEST);
        JLabel close = new JLabel(Icons.closeRed());
        close.setToolTipText("Hide the navigator (toolbar: Navigator)");
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ctx.setNavigatorVisible(false);
            }
        });
        head.add(close, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        setupFolderTree();
        setupTypeTree();
        setupGroups();
        setupUsers();
        setupFavorites();

        tabs.add("Folders", null, tabPanel(filter, null, scroll(folderTree)), false, null);
        tabs.add("Types", null, tabPanel(typeFilter, this::rebuildTypes, scroll(typeTree)), false, null);
        tabs.add("Groups", null, tabPanel(groupFilter, this::rebuildGroups, groupsCenter()), false, null);
        tabs.add("Users", null, tabPanel(userFilter, this::rebuildUsers, usersCenter()), false, null);
        tabs.add("Favorites", null, tabPanel(favFilter, null, scroll(favTree)), false, null);
        tabs.select(tabs.components().get(0));
        tabs.onChange(this::ensureLoaded);
        add(tabs);
        Ui.bind(this, Ui.key(java.awt.event.KeyEvent.VK_F5), "navRefresh", this::refresh);

        onType(filter, this::rebuildCabinets);
        onType(typeFilter, this::rebuildTypes);
        onType(groupFilter, this::rebuildGroups);
        onType(userFilter, this::rebuildUsers);
        onType(favFilter, this::buildFavorites);
        onType(memberFilter, this::showMembers);
        setPreferredSize(new Dimension(285, 400));
    }

    // ------------------------------------------------------------ layout helpers

    /** "Filter [.......] (search)" row on top and the content in a white bordered box, as in dqMan. */
    private static JPanel tabPanel(JTextField f, Runnable search, JComponent center) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.PANEL_BG);
        p.add(filterRow("Filter", f, search, search == null ? 56 : 30), BorderLayout.NORTH);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 4, 2, 2));
        wrap.add(center);
        p.add(wrap);
        return p;
    }

    private static JPanel filterRow(String label, JTextField f, Runnable search, int right) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 8, 8, right));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(f);
        if (search != null) {
            JLabel b = new JLabel(Icons.search());
            b.setToolTipText("Search");
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    search.run();
                }
            });
            row.add(b, BorderLayout.EAST);
            f.addActionListener(e -> search.run());
        }
        return row;
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(Theme.box());
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    private static void onType(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    private static void styleTree(JTree t, boolean rootVisible) {
        t.setRootVisible(rootVisible);
        t.setShowsRootHandles(true);
        t.putClientProperty("JTree.lineStyle", "None");
        t.setRowHeight(19);
        t.setBorder(new EmptyBorder(4, 2, 4, 2));
        ToolTipManager.sharedInstance().registerComponent(t);
    }

    private static boolean matches(String text, JTextField f) {
        String t = f.getText().trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() || text.toLowerCase(Locale.ROOT).contains(t);
    }

    private static DefaultMutableTreeNode lazy(String label) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(label);
        n.add(new DefaultMutableTreeNode(LOADING));
        return n;
    }

    private static Object nodeAt(JTree tree, MouseEvent e) {
        TreePath p = tree.getPathForLocation(e.getX(), e.getY());
        if (p == null) return null;
        tree.setSelectionPath(p);
        return ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
    }

    // ------------------------------------------------------------ loading

    void start() {
        ensureLoaded();
    }

    private void ensureLoaded() {
        switch (tabs.selectedIndex()) {
            case 0:
                if (!cabinetsLoaded) loadCabinets();
                break;
            case 4:
                buildFavorites();
                break;
            default:
                break; // Types, Groups, Users load when their node is expanded
        }
    }

    private void refresh() {
        switch (tabs.selectedIndex()) {
            case 0: loadCabinets(); break;
            case 1: resetTypes(); break;
            case 2: loadGroups(); break;
            case 3: loadUsers(); break;
            case 4: buildFavorites(); break;
            default: break;
        }
    }

    /** Session > Refresh Valuelists: forget all cached lists and reload what is visible. */
    void refreshAll() {
        loadCabinets();
        resetTypes();
        groupsLoaded = false;
        usersLoaded = false;
        groupRoot.removeAllChildren();
        groupRoot.add(new DefaultMutableTreeNode(LOADING));
        groupModel.reload();
        userRoot.removeAllChildren();
        userRoot.add(new DefaultMutableTreeNode(LOADING));
        userModel.reload();
        buildFavorites();
        ctx.status("Value lists refreshed");
    }

    // ------------------------------------------------------------------ folders

    private void setupFolderTree() {
        styleTree(folderTree, true);
        folderTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
                Object u = ((DefaultMutableTreeNode) value).getUserObject();
                setToolTipText(null);
                if (u instanceof Item) {
                    Item it = (Item) u;
                    setIcon(it.isCabinet() ? Icons.cabinet() : it.isFolder() ? Icons.folder()
                            : it.lockOwner.isEmpty() ? Icons.document() : Icons.lockedDocument());
                    setText(it.name);
                    setToolTipText(it.type + "  " + it.id + (it.contentType.isEmpty() ? "" : "  (" + it.contentType + ")")
                            + (it.lockOwner.isEmpty() ? "" : "  – checked out by " + it.lockOwner));
                } else if (value == folderRoot) {
                    setIcon(Icons.repository());
                } else {
                    setIcon(null);
                }
                return this;
            }
        });
        folderTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                if (n.getChildCount() == 1 && LOADING.equals(((DefaultMutableTreeNode) n.getFirstChild()).getUserObject())) {
                    loadChildren(n);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                // nothing
            }
        });
        folderTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Item it = itemAt(folderTree, e);
                if (it != null && !it.isFolder()) ctx.openObject(it.id);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                folderPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                folderPopup(e);
            }
        });
    }

    private static Item itemAt(JTree tree, MouseEvent e) {
        TreePath p = tree.getPathForLocation(e.getX(), e.getY());
        if (p == null) return null;
        tree.setSelectionPath(p);
        Object u = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
        return u instanceof Item ? (Item) u : null;
    }

    private void loadCabinets() {
        cabinetsLoaded = true;
        folderRoot.removeAllChildren();
        folderRoot.add(new DefaultMutableTreeNode(LOADING));
        folderModel.reload();
        ctx.conn().run(s -> QueryResult.execute(s,
                "select r_object_id, object_name, r_object_type from dm_cabinet order by object_name", 0, null),
                r -> {
                    cabinets.clear();
                    for (int i = 0; i < r.rows.size(); i++) cabinets.add(item(r, i));
                    rebuildCabinets();
                },
                t -> {
                    folderRoot.removeAllChildren();
                    folderModel.reload();
                    Ui.error(this, "Could not load cabinets", t);
                });
    }

    private void rebuildCabinets() {
        String t = filter.getText().trim().toLowerCase(Locale.ROOT);
        folderRoot.removeAllChildren();
        for (Item c : cabinets) {
            if (!t.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(t)) continue;
            folderRoot.add(node(c));
        }
        folderModel.reload();
        folderTree.expandRow(0);
    }

    private static Item item(QueryResult r, int i) {
        Item it = new Item();
        it.id = r.get(i, "r_object_id");
        it.name = r.get(i, "object_name");
        it.type = r.get(i, "r_object_type");
        it.contentType = r.get(i, "a_content_type");
        it.lockOwner = r.get(i, "r_lock_owner").trim();
        if (it.name.isEmpty()) it.name = "(" + it.id + ")";
        return it;
    }

    private static DefaultMutableTreeNode node(Item it) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(it);
        if (it.isFolder()) n.add(new DefaultMutableTreeNode(LOADING));
        return n;
    }

    private void loadChildren(DefaultMutableTreeNode n) {
        Item folder = (Item) n.getUserObject();
        ctx.conn().run(s -> QueryResult.execute(s,
                "select r_object_id, object_name, r_object_type, a_content_type, r_lock_owner from dm_sysobject "
                        + "where folder(id('" + folder.id + "')) order by object_name", MAX_CHILDREN, null),
                r -> {
                    List<Item> folders = new ArrayList<>();
                    List<Item> docs = new ArrayList<>();
                    for (int i = 0; i < r.rows.size(); i++) {
                        Item it = item(r, i);
                        (it.isFolder() ? folders : docs).add(it);
                    }
                    n.removeAllChildren();
                    for (Item it : folders) n.add(node(it));
                    for (Item it : docs) n.add(node(it));
                    if (r.truncated) n.add(new DefaultMutableTreeNode("... more than " + MAX_CHILDREN + " objects (use DQL)"));
                    folderModel.nodeStructureChanged(n);
                    ctx.status(r.rows.size() + " objects in " + folder.name);
                },
                t -> {
                    n.removeAllChildren();
                    folderModel.nodeStructureChanged(n);
                    Ui.error(this, "Could not load folder", t);
                });
    }

    private void reloadNode(DefaultMutableTreeNode n) {
        if (n == folderRoot) {
            loadCabinets();
            return;
        }
        n.removeAllChildren();
        n.add(new DefaultMutableTreeNode(LOADING));
        folderModel.nodeStructureChanged(n);
        folderTree.expandPath(new TreePath(n.getPath()));
        loadChildren(n);
    }

    private void folderPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath p = folderTree.getPathForLocation(e.getX(), e.getY());
        if (p == null) return;
        folderTree.setSelectionPath(p);
        DefaultMutableTreeNode n = (DefaultMutableTreeNode) p.getLastPathComponent();
        JPopupMenu m = new JPopupMenu();
        Object u = n.getUserObject();
        if (u instanceof Item) {
            Item it = (Item) u;
            m.add(Ui.item("Properties", ev -> ctx.openObject(it.id)));
            m.add(Ui.item("Dump", ev -> Ops.dump(ctx, it.id)));
            m.add(Ui.item("Copy object id", ev -> Ui.copy(it.id)));
            m.addSeparator();
            if (it.isFolder()) {
                m.add(Ui.item("List contents in DQL", ev -> ctx.openDql(
                        "select r_object_id, object_name, r_object_type, a_content_type, r_modify_date, owner_name\n"
                                + "from dm_sysobject where folder(id('" + it.id + "'))\norder by object_name", true)));
                m.add(Ui.item("Import files here...", ev -> Ops.importInto(ctx, it.id, it.name, () -> reloadNode(n))));
                m.add(Ui.item("Refresh", ev -> reloadNode(n)));
            } else {
                m.add(Ui.item("Export content...", ev -> Ops.exportContent(ctx, it.id)));
                m.add(Ui.item("Check out", ev -> Ops.checkout(ctx, it.id, () -> reloadNode((DefaultMutableTreeNode) n.getParent()))));
                m.add(Ui.item("Check in...", ev -> Ops.checkin(ctx, it.id)));
                m.add(Ui.item("Cancel checkout", ev -> Ops.cancelCheckout(ctx, it.id, () -> reloadNode((DefaultMutableTreeNode) n.getParent()))));
            }
            m.addSeparator();
            JMenuItem d = Ui.item("Destroy...", ev -> Ops.destroy(ctx, it.id, it.name, () -> {
                folderModel.removeNodeFromParent(n);
            }));
            d.setForeground(new Color(0xB00020));
            m.add(d);
        } else if (n == folderRoot) {
            m.add(Ui.item("Refresh", ev -> loadCabinets()));
        }
        m.show(folderTree, e.getX(), e.getY());
    }

    // -------------------------------------------------------------------- types

    private void setupTypeTree() {
        styleTree(typeTree, false);
        objTypesNode.add(new DefaultMutableTreeNode(LOADING));
        regNode.add(new DefaultMutableTreeNode(LOADING));
        typeRoot.add(objTypesNode);
        typeRoot.add(regNode);
        typeModel.reload();
        typeTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
                Object u = ((DefaultMutableTreeNode) value).getUserObject();
                if (value == objTypesNode || value == regNode) setIcon(Icons.repository());
                else if (u instanceof RegTable) setIcon(Icons.table());
                else if (LOADING.equals(u)) setIcon(null);
                else setIcon(Icons.type());
                return this;
            }
        });
        typeTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                Object n = e.getPath().getLastPathComponent();
                if (n == objTypesNode && !typesLoaded) loadTypes();
                if (n == regNode && !regLoaded) loadRegistered();
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                // nothing
            }
        });
        typeTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Object u = nodeAt(typeTree, e);
                if (u instanceof RegTable) ctx.openDql("select * from " + regTableRef((RegTable) u), true);
                else if (u instanceof String && typeSuper.containsKey(u)) describeType((String) u);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                typePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                typePopup(e);
            }
        });
    }

    private static String regTableRef(RegTable t) {
        String owner = t.owner == null || t.owner.trim().isEmpty() ? "dm_dbo" : t.owner.trim();
        return owner + "." + (t.table == null || t.table.isEmpty() ? t.name : t.table);
    }

    private void typePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        Object u = nodeAt(typeTree, e);
        JPopupMenu m = new JPopupMenu();
        if (u instanceof RegTable) {
            RegTable t = (RegTable) u;
            m.add(Ui.item("select * from " + regTableRef(t), ev -> ctx.openDql("select * from " + regTableRef(t), true)));
            m.add(Ui.item("Registration details", ev -> ctx.openDql(
                    "select * from dm_registered where object_name = '" + QueryResult.esc(t.name) + "'", true)));
        } else if (u instanceof String && typeSuper.containsKey(u)) {
            String t = (String) u;
            m.add(Ui.item("Describe attributes", ev -> describeType(t)));
            m.add(Ui.item("select * from " + t, ev -> ctx.openDql("select * from " + t, false)));
            m.add(Ui.item("Count objects", ev -> ctx.openDql("select count(*) from " + t, true)));
            m.add(Ui.item("Type properties", ev -> ctx.openDql("select r_object_id, name, super_name, attr_count, start_pos "
                    + "from dm_type where name = '" + t + "'", true)));
            m.add(Ui.item("Copy name", ev -> Ui.copy(t)));
        }
        m.add(Ui.item("Refresh", ev -> resetTypes()));
        m.show(typeTree, e.getX(), e.getY());
    }

    private void resetTypes() {
        typesLoaded = false;
        regLoaded = false;
        objTypesNode.removeAllChildren();
        objTypesNode.add(new DefaultMutableTreeNode(LOADING));
        regNode.removeAllChildren();
        regNode.add(new DefaultMutableTreeNode(LOADING));
        typeModel.reload();
    }

    private void loadTypes() {
        typesLoaded = true;
        ctx.conn().run(s -> QueryResult.execute(s, "select name, super_name from dm_type order by name", 0, null),
                r -> {
                    typeSuper.clear();
                    for (String[] row : r.rows) typeSuper.put(row[0], row[1].trim());
                    rebuildTypes();
                    typeTree.expandPath(new TreePath(objTypesNode.getPath()));
                },
                t -> {
                    typesLoaded = false;
                    Ui.error(this, "Could not load types", t);
                });
    }

    private void loadRegistered() {
        regLoaded = true;
        ctx.conn().run(s -> QueryResult.execute(s,
                "select object_name, table_owner, table_name from dm_registered order by object_name", 0, null),
                r -> {
                    registered.clear();
                    for (String[] row : r.rows) registered.add(new RegTable(row[0], row[1], row[2]));
                    rebuildTypes();
                    typeTree.expandPath(new TreePath(regNode.getPath()));
                },
                t -> {
                    regLoaded = false;
                    Ui.error(this, "Could not load registered tables", t);
                });
    }

    private void rebuildTypes() {
        boolean filtered = !typeFilter.getText().trim().isEmpty();
        if (typesLoaded && !typeSuper.isEmpty()) {
            objTypesNode.removeAllChildren();
            if (filtered) {
                for (String t : typeSuper.keySet()) if (matches(t, typeFilter)) objTypesNode.add(new DefaultMutableTreeNode(t));
            } else {
                Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
                for (String t : typeSuper.keySet()) nodes.put(t, new DefaultMutableTreeNode(t));
                for (Map.Entry<String, String> e : typeSuper.entrySet()) {
                    DefaultMutableTreeNode parent = nodes.get(e.getValue());
                    (parent == null ? objTypesNode : parent).add(nodes.get(e.getKey()));
                }
            }
            typeModel.nodeStructureChanged(objTypesNode);
            if (filtered) typeTree.expandPath(new TreePath(objTypesNode.getPath()));
        }
        if (regLoaded) {
            regNode.removeAllChildren();
            for (RegTable t : registered) if (matches(t.name, typeFilter)) regNode.add(new DefaultMutableTreeNode(t));
            typeModel.nodeStructureChanged(regNode);
            if (filtered) typeTree.expandPath(new TreePath(regNode.getPath()));
        }
    }

    // -------------------------------------------------------------------- groups

    private final DefaultTreeCellRenderer principalRenderer = new DefaultTreeCellRenderer() {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp,
                                                      boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
            Object u = ((DefaultMutableTreeNode) value).getUserObject();
            if (u instanceof Principal) {
                Principal p = (Principal) u;
                setIcon("user".equals(p.kind) ? Icons.user() : "role".equals(p.kind) ? Icons.role() : Icons.group());
                if (p.inactive && !sel) setForeground(Color.GRAY);
            } else if (LOADING.equals(u)) {
                setIcon(null);
            } else {
                setIcon(Icons.repository());
            }
            return this;
        }
    };

    private final DefaultListCellRenderer principalListRenderer = new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
            super.getListCellRendererComponent(l, v, i, sel, f);
            if (v instanceof Principal) {
                Principal p = (Principal) v;
                setIcon("user".equals(p.kind) ? Icons.user() : "role".equals(p.kind) ? Icons.role() : Icons.group());
            }
            return this;
        }
    };

    private void setupGroups() {
        styleTree(groupTree, true);
        groupRoot.add(new DefaultMutableTreeNode(LOADING));
        groupModel.reload();
        groupTree.collapseRow(0);
        groupTree.setCellRenderer(principalRenderer);
        groupTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                if (e.getPath().getLastPathComponent() == groupRoot && !groupsLoaded) loadGroups();
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                // nothing
            }
        });
        groupTree.addTreeSelectionListener(e -> {
            Object n = e.getPath().getLastPathComponent();
            Object u = ((DefaultMutableTreeNode) n).getUserObject();
            if (u instanceof Principal) loadMembers(((Principal) u).name);
        });
        groupTree.addMouseListener(principalMouse(groupTree, true));
        memberList.setCellRenderer(principalListRenderer);
        memberList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Principal p = memberList.getSelectedValue();
                if (e.getClickCount() == 2 && p != null) openPrincipal(p);
            }
        });
        memberKind.addActionListener(e -> showMembers());
    }

    private JComponent groupsCenter() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(8, 4, 4, 4));
        head.add(new JLabel("Groups & Users"), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout(6, 0));
        right.setOpaque(false);
        memberKind.setPreferredSize(new Dimension(115, memberKind.getPreferredSize().height));
        right.add(memberKind);
        right.add(pencil("Open the selected group", () -> {
            if (selectedGroup != null) openPrincipal(new Principal(selectedGroup, "group", false));
        }), BorderLayout.EAST);
        head.add(right, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(filterRow("Filter", memberFilter, null, 22), BorderLayout.SOUTH);
        bottom.add(top, BorderLayout.NORTH);
        bottom.add(scroll(memberList));
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(groupTree), bottom);
        sp.setResizeWeight(0.5);
        sp.setDividerLocation(215);
        sp.setDividerSize(5);
        sp.setBorder(null);
        sp.setOpaque(false);
        return sp;
    }

    private static JLabel pencil(String tip, Runnable r) {
        JLabel l = new JLabel(Icons.pencil());
        l.setToolTipText(tip);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                r.run();
            }
        });
        return l;
    }

    private MouseAdapter principalMouse(JTree tree, boolean groups) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Object u = nodeAt(tree, e);
                if (u instanceof Principal) openPrincipal((Principal) u);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pop(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pop(e);
            }

            private void pop(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                Object u = nodeAt(tree, e);
                JPopupMenu m = new JPopupMenu();
                if (u instanceof Principal) {
                    Principal p = (Principal) u;
                    String esc = QueryResult.esc(p.name);
                    m.add(Ui.item("Properties", ev -> openPrincipal(p)));
                    if (groups) {
                        m.add(Ui.item("Members (DQL)", ev -> ctx.openDql("select group_name, users_names, groups_names "
                                + "from dm_group where group_name = '" + esc + "'", true)));
                    } else {
                        m.add(Ui.item("Groups of user (DQL)", ev -> ctx.openDql("select group_name from dm_group "
                                + "where any users_names = '" + esc + "' order by group_name", true)));
                        m.add(Ui.item("Inbox (DQL)", ev -> ctx.openDql("select r_object_id, item_id, task_name, sent_by, "
                                + "date_sent from dmi_queue_item where name = '" + esc + "' and delete_flag = 0 "
                                + "order by date_sent desc", true)));
                    }
                    m.add(Ui.item("Copy name", ev -> Ui.copy(p.name)));
                    m.addSeparator();
                }
                m.add(Ui.item("Refresh", ev -> {
                    if (groups) loadGroups();
                    else loadUsers();
                }));
                m.show(tree, e.getX(), e.getY());
            }
        };
    }

    private void openPrincipal(Principal p) {
        String esc = QueryResult.esc(p.name);
        String dql = "user".equals(p.kind) ? "select r_object_id from dm_user where user_name = '" + esc + "'"
                : "select r_object_id from dm_group where group_name = '" + esc + "'";
        ctx.conn().run(s -> QueryResult.execute(s, dql, 1, null), r -> {
            if (r.rows.isEmpty()) ctx.status("Not found: " + p.name);
            else ctx.openObject(r.rows.get(0)[0]);
        }, t -> Ui.error(this, "Open " + p.name, t));
    }

    private void loadGroups() {
        groupsLoaded = true;
        groupRoot.removeAllChildren();
        groupRoot.add(new DefaultMutableTreeNode(LOADING));
        groupModel.nodeStructureChanged(groupRoot);
        ctx.conn().run(s -> QueryResult.execute(s,
                "select group_name, group_class, is_private from dm_group order by group_name", 0, null),
                r -> {
                    groupList.clear();
                    for (String[] row : r.rows) {
                        String cls = row[1].trim().toLowerCase(Locale.ROOT);
                        groupList.add(new Principal(row[0], cls.contains("role") ? "role" : "group", false));
                    }
                    rebuildGroups();
                    groupTree.expandRow(0);
                    ctx.status(groupList.size() + " groups and roles");
                },
                t -> {
                    groupsLoaded = false;
                    Ui.error(this, "Could not load groups", t);
                });
    }

    private void rebuildGroups() {
        if (!groupsLoaded) {
            if (!groupFilter.getText().trim().isEmpty()) loadGroups();
            return;
        }
        if (groupList.isEmpty()) return;
        groupRoot.removeAllChildren();
        for (Principal p : groupList) if (matches(p.name, groupFilter)) groupRoot.add(new DefaultMutableTreeNode(p));
        groupModel.nodeStructureChanged(groupRoot);
        groupTree.expandRow(0);
    }

    private void loadMembers(String group) {
        selectedGroup = group;
        members.clear();
        memberModel.clear();
        ctx.conn().run(s -> QueryResult.execute(s, "select users_names, groups_names from dm_group where group_name = '"
                + QueryResult.esc(group) + "'", 1, null), r -> {
            members.clear();
            if (!r.rows.isEmpty()) {
                for (String u : r.rows.get(0)[0].split(";")) if (!u.trim().isEmpty()) members.add(new Principal(u.trim(), "user", false));
                for (String g : r.rows.get(0)[1].split(";")) if (!g.trim().isEmpty()) members.add(new Principal(g.trim(), "group", false));
            }
            members.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
            showMembers();
            ctx.status(group + ": " + members.size() + " member(s)");
        }, t -> Ui.error(this, "Could not load members", t));
    }

    private void showMembers() {
        memberModel.clear();
        String kind = String.valueOf(memberKind.getSelectedItem());
        for (Principal p : members) {
            if ("Users".equals(kind) && !"user".equals(p.kind)) continue;
            if ("Groups".equals(kind) && "user".equals(p.kind)) continue;
            if (matches(p.name, memberFilter)) memberModel.addElement(p);
        }
    }

    // -------------------------------------------------------------------- users

    private void setupUsers() {
        styleTree(userTree, true);
        userRoot.add(new DefaultMutableTreeNode(LOADING));
        userModel.reload();
        userTree.collapseRow(0);
        userTree.setCellRenderer(principalRenderer);
        userTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                if (e.getPath().getLastPathComponent() == userRoot && !usersLoaded) loadUsers();
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                // nothing
            }
        });
        userTree.addTreeSelectionListener(e -> {
            Object u = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();
            if (u instanceof Principal) loadUserGroups(((Principal) u).name);
        });
        userTree.addMouseListener(principalMouse(userTree, false));
        userGroupList.setCellRenderer(principalListRenderer);
        userGroupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Principal p = userGroupList.getSelectedValue();
                if (e.getClickCount() == 2 && p != null) openPrincipal(p);
            }
        });
    }

    private JComponent usersCenter() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(8, 4, 4, 4));
        head.add(new JLabel("Groups"), BorderLayout.WEST);
        head.add(pencil("Open the selected user", () -> {
            if (selectedUser != null) openPrincipal(new Principal(selectedUser, "user", false));
        }), BorderLayout.EAST);
        bottom.add(head, BorderLayout.NORTH);
        bottom.add(scroll(userGroupList));
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(userTree), bottom);
        sp.setResizeWeight(0.6);
        sp.setDividerLocation(215);
        sp.setDividerSize(5);
        sp.setBorder(null);
        sp.setOpaque(false);
        return sp;
    }

    private void loadUsers() {
        usersLoaded = true;
        userRoot.removeAllChildren();
        userRoot.add(new DefaultMutableTreeNode(LOADING));
        userModel.nodeStructureChanged(userRoot);
        ctx.conn().run(s -> QueryResult.execute(s, "select user_name, user_state from dm_user order by user_name", 0, null),
                r -> {
                    userList.clear();
                    for (String[] row : r.rows) userList.add(new Principal(row[0], "user", !"0".equals(row[1].trim())));
                    rebuildUsers();
                    userTree.expandRow(0);
                    ctx.status(userList.size() + " users");
                },
                t -> {
                    usersLoaded = false;
                    Ui.error(this, "Could not load users", t);
                });
    }

    private void rebuildUsers() {
        if (!usersLoaded) {
            if (!userFilter.getText().trim().isEmpty()) loadUsers();
            return;
        }
        if (userList.isEmpty()) return;
        userRoot.removeAllChildren();
        for (Principal p : userList) if (matches(p.name, userFilter)) userRoot.add(new DefaultMutableTreeNode(p));
        userModel.nodeStructureChanged(userRoot);
        userTree.expandRow(0);
    }

    private void loadUserGroups(String user) {
        selectedUser = user;
        userGroupModel.clear();
        ctx.conn().run(s -> QueryResult.execute(s, "select group_name, group_class from dm_group where any users_names = '"
                + QueryResult.esc(user) + "' order by group_name", 0, null), r -> {
            userGroupModel.clear();
            for (String[] row : r.rows) {
                userGroupModel.addElement(new Principal(row[0], row[1].toLowerCase(Locale.ROOT).contains("role") ? "role" : "group", false));
            }
            ctx.status(user + ": member of " + r.rows.size() + " group(s)");
        }, t -> Ui.error(this, "Could not load groups of " + user, t));
    }

    // -------------------------------------------------------------------- favorites

    private void setupFavorites() {
        styleTree(favTree, false);
        favTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) value;
                Object u = n.getUserObject();
                setToolTipText(null);
                if (u instanceof FavNode) {
                    setIcon(Icons.query());
                    setToolTipText(((FavNode) u).dql);
                } else if (n.getParent() == favRoot) {
                    setIcon(Icons.repository());
                } else {
                    setIcon(Icons.starSmall());
                }
                return this;
            }
        });
        favTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Object u = nodeAt(favTree, e);
                if (u instanceof FavNode) {
                    FavNode f = (FavNode) u;
                    ctx.openDql(f.dql, !f.dql.contains("<"));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                favPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                favPopup(e);
            }
        });
    }

    private void favPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        Object u = nodeAt(favTree, e);
        JPopupMenu m = new JPopupMenu();
        if (u instanceof FavNode) {
            FavNode f = (FavNode) u;
            m.add(Ui.item("Load", ev -> ctx.openDql(f.dql, false)));
            m.add(Ui.item("Load and run", ev -> ctx.openDql(f.dql, true)));
            if (f.user) {
                m.add(Ui.item("Delete", ev -> {
                    Prefs.removeFavorite(f.name);
                    buildFavorites();
                }));
            }
            m.addSeparator();
        }
        m.add(Ui.item("Add current query...", ev -> {
            MainFrame.addFavorite(ctx);
            buildFavorites();
        }));
        m.add(Ui.item("Refresh", ev -> buildFavorites()));
        m.show(favTree, e.getX(), e.getY());
    }

    void buildFavorites() {
        boolean filtered = !favFilter.getText().trim().isEmpty();
        favRoot.removeAllChildren();
        DefaultMutableTreeNode std = new DefaultMutableTreeNode("Standard Favorites");
        for (Map.Entry<String, List<StandardFavorites.Fav>> c : StandardFavorites.ALL.entrySet()) {
            DefaultMutableTreeNode cat = new DefaultMutableTreeNode(c.getKey());
            for (StandardFavorites.Fav f : c.getValue()) {
                if (matches(f.name, favFilter) || matches(c.getKey(), favFilter)) cat.add(new DefaultMutableTreeNode(new FavNode(f.name, f.dql, false)));
            }
            if (cat.getChildCount() > 0 || !filtered) std.add(cat);
        }
        DefaultMutableTreeNode user = new DefaultMutableTreeNode("User's Favorites");
        for (Map.Entry<String, String> f : Prefs.favorites().entrySet()) {
            if (matches(f.getKey(), favFilter)) user.add(new DefaultMutableTreeNode(new FavNode(f.getKey(), f.getValue(), true)));
        }
        favRoot.add(std);
        favRoot.add(user);
        favModel.reload();
        favTree.expandPath(new TreePath(std.getPath()));
        if (filtered) for (int i = 0; i < favTree.getRowCount(); i++) favTree.expandRow(i);
        if (user.getChildCount() > 0) favTree.expandPath(new TreePath(user.getPath()));
    }

    private void describeType(String type) {
        ctx.status("Describing " + type + "...");
        ctx.conn().run(s -> {
            IDfType t = s.getType(type);
            int start = t.getInt("start_pos");
            QueryResult r = QueryResult.of(new String[]{"attr_name", "data_type", "length", "repeating", "defined_in"});
            int n = t.getTypeAttrCount();
            for (int i = 0; i < n; i++) {
                IDfAttr a = t.getTypeAttr(i);
                r.rows.add(new String[]{a.getName(), QueryResult.typeName(a.getDataType()),
                        a.getDataType() == Dfc.DM_STRING ? String.valueOf(a.getLength()) : "",
                        a.isRepeating() ? "R" : "", i < start ? "inherited" : type});
            }
            r.dql = "describe " + type;
            return r;
        }, r -> {
            ResultPanel p = new ResultPanel(ctx, false);
            p.show(r);
            ctx.addTab("Type: " + type, p);
            ctx.status(type + ": " + r.rows.size() + " attributes (supertype " + typeSuper.getOrDefault(type, "?") + ")");
        }, t -> Ui.error(this, "Describe type failed", t));
    }
}

package Client.view;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;
import Server.network.ProtocolMessage;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Authoritative TCP lobby: online list, rooms, invitations and match launch.
 */
public final class LobbyScreen extends JFrame implements TcpGameClient.MessageListener {
    private final TcpGameClient client = GameClientSession.instance().client();
    private final long playerId;
    private final DefaultTableModel players = new DefaultTableModel(
            new String[]{"ID", "Người chơi", "Điểm", "Trận", "Thắng"}, 0) {
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"FRUIT_GROUP", "NUTRITION"});
    private final JButton create      = new JButton("Tạo phòng");
    private final JButton leaveRoom   = new JButton("Thoát phòng");
    private final JButton invite      = new JButton("Mời");
    private final JButton start       = new JButton("Bắt đầu trận");
    private final JLabel roomStatus   = new JLabel("Chưa có phòng", SwingConstants.CENTER);
    private final JLabel modeImgLabel = new JLabel();      // shows mode icon next to combobox
    private final JLabel statusIcon   = new JLabel();      // connection status icon (top-right)
    private final JLabel totalScoreLabel = new JLabel("Tổng điểm: —");
    private final JLabel selfStatusLabel = new JLabel("Trạng thái: ONLINE");
    private final JLabel onlineCountLabel = new JLabel("Người chơi online: —");
    private final java.util.List<FruitCatalogItem> fruitCatalog = new ArrayList<>();
    private final Map<String, String> modeDescriptions = new HashMap<>();
    private JPanel discoveryHost;
    private long roomId = -1;
    private boolean collectingPlayers;

    public LobbyScreen(String ignoredPlayerId, String ignoredName) {
        playerId = GameClientSession.instance().identity().playerId();
        client.addListener(this);
        setTitle("Fruit Battle Online — Lobby");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1080, 760);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);

        // ── Background ──────────────────────────────────────────────────────
        ImageIcon bg = AssetLoader.icon("backgrounds/lobby-orchard-background.png");
        ScaledBackgroundPanel bgPanel = new ScaledBackgroundPanel(bg);
        bgPanel.setLayout(new BorderLayout(0, 0));
        setContentPane(bgPanel);

        // ── Header (logo + room status + connection icon) ────────────────────
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));

        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            JLabel logoLbl = new JLabel(AssetLoader.scaleToFit(logoIcon, 40, 40));
            header.add(logoLbl, BorderLayout.WEST);
        }

        roomStatus.setFont(new Font("Arial", Font.BOLD, 14));
        roomStatus.setText("FRUIT BATTLE ONLINE");
        roomStatus.setForeground(new Color(17, 83, 54));
        header.add(roomStatus, BorderLayout.CENTER);

        // Connection online icon (top-right)
        ImageIcon onlineIcon = AssetLoader.icon("icons/connection-online.png");
        if (onlineIcon != null) {
            Image s = onlineIcon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH);
            statusIcon.setIcon(new ImageIcon(s));
        }
        header.add(statusIcon, BorderLayout.EAST);

        // ── Player table ─────────────────────────────────────────────────────
        players.addColumn("Trạng thái");
        JTable table = new JTable(players);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.setFont(new Font("Arial", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(0, 80, 160, 210));
        table.getTableHeader().setForeground(Color.WHITE);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(new Color(255, 255, 255, 180));
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));

        // ── Actions panel ────────────────────────────────────────────────────
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        actions.setOpaque(false);

        // Mode selector with image
        updateModeImage((String) mode.getSelectedItem());
        mode.addActionListener(e -> {
            String selected = (String) mode.getSelectedItem();
            updateModeImage(selected);
            if (roomId > 0) {
                send(new ProtocolMessage("CHANGE_GAME_MODE",
                        Map.of("roomId", Long.toString(roomId),
                               "modeCode", Objects.requireNonNull(selected))));
            }
        });

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        modePanel.setOpaque(false);
        JLabel modeLbl = new JLabel("Mode:");
        modeLbl.setForeground(new Color(17, 83, 54));
        modeLbl.setFont(new Font("Arial", Font.BOLD, 13));
        modePanel.add(modeLbl);
        modePanel.add(modeImgLabel);
        modePanel.add(mode);

        JButton profile = buildBtn("Hồ sơ",         new Color(80,  80,  80));
        JButton history = buildBtn("Lịch sử",       new Color(80,  80,  80));
        JButton rank    = buildBtn("Xếp hạng",      new Color(80,  80,  80));
        JButton logout  = buildBtn("Đăng xuất",     new Color(180, 0,   0));
        applyBtnStyle(create,    new Color(34,  139, 34));
        applyBtnStyle(leaveRoom, new Color(204, 102, 0));
        applyBtnStyle(invite,    new Color(0,   102, 204));
        applyBtnStyle(start,     new Color(160, 90,  0));
        leaveRoom.setEnabled(false);
        invite.setEnabled(false);
        start.setEnabled(false);

        actions.add(profile);
        actions.add(history);
        actions.add(rank);
        actions.add(logout);

        bgPanel.add(header, BorderLayout.NORTH);
        bgPanel.add(buildDashboard(), BorderLayout.CENTER);
        bgPanel.add(actions, BorderLayout.SOUTH);
        scroll.setVisible(false);

        // ── Listeners ────────────────────────────────────────────────────────
        create.addActionListener(e -> send(new ProtocolMessage("CREATE_ROOM",
                Map.of("modeCode", Objects.requireNonNull(mode.getSelectedItem()).toString()))));

        leaveRoom.addActionListener(e -> {
            if (roomId > 0) {
                send(new ProtocolMessage("LEAVE_ROOM", Map.of("roomId", Long.toString(roomId))));
            }
        });

        invite.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { notice("Chọn một người chơi để mời"); return; }
            send(new ProtocolMessage("INVITE_PLAYER",
                    Map.of("roomId", Long.toString(roomId),
                           "playerId", players.getValueAt(row, 0).toString())));
        });

        start.addActionListener(e ->
                send(new ProtocolMessage("START_MATCH", Map.of("roomId", Long.toString(roomId)))));

        profile.addActionListener(e -> new ProfileScreen(Long.toString(playerId)));
        history.addActionListener(e -> new HistoryScreen(Long.toString(playerId)));
        rank.addActionListener(e -> new RankScreen());

        logout.addActionListener(e -> {
            try { GameClientSession.instance().logout(); } catch (IOException ignored) {}
            dispose();
            new LoginScreen();
        });

        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
        send(ProtocolMessage.of("GET_PROFILE"));
        send(ProtocolMessage.of("GET_FRUIT_CATALOG"));
        send(ProtocolMessage.of("GET_GAME_MODES"));
        send(ProtocolMessage.of("GET_ROOM_SNAPSHOT"));
        setVisible(true);
    }

    private JPanel buildDashboard() {
        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(8, 28, 8, 28));

        String displayName = GameClientSession.instance().identity().displayName();
        JPanel profile = dashboardCard();
        profile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 82, 55), 1),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        JLabel welcome = new JLabel("Xin chào: " + displayName);
        welcome.setFont(new Font("Arial", Font.BOLD, 20));
        welcome.setForeground(new Color(17, 83, 54));
        totalScoreLabel.setFont(new Font("Arial", Font.BOLD, 17));
        totalScoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profile.add(welcome, BorderLayout.WEST);
        profile.add(totalScoreLabel, BorderLayout.EAST);
        root.add(profile, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 2, 18, 0));
        cards.setOpaque(false);
        cards.add(buildStartCard());
        cards.add(buildGuideCard());
        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(cards, BorderLayout.CENTER);
        discoveryHost = new JPanel(new BorderLayout());
        discoveryHost.setOpaque(false);
        discoveryHost.add(buildDiscoveryPanel(), BorderLayout.CENTER);
        center.add(discoveryHost, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildStartCard() {
        JPanel card = dashboardCard();
        card.add(cardTitle("BẮT ĐẦU CHƠI"), BorderLayout.NORTH);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JLabel subtitle = new JLabel("Thi đấu 1 vs 1 trong 30 giây", SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("Arial", Font.BOLD, 18));
        JLabel rules = new JLabel("Hứng đúng: +10    Hứng sai: -5", SwingConstants.CENTER);
        rules.setAlignmentX(Component.CENTER_ALIGNMENT);
        rules.setFont(new Font("Arial", Font.PLAIN, 16));
        content.add(Box.createVerticalStrut(14));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(16));
        JLabel fruitIcons = new JLabel("🍎   🍊   🍇   🍓", SwingConstants.CENTER);
        fruitIcons.setAlignmentX(Component.CENTER_ALIGNMENT);
        fruitIcons.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 30));
        content.add(fruitIcons);
        content.add(Box.createVerticalStrut(16));
        content.add(rules);
        card.add(content, BorderLayout.CENTER);
        applyBtnStyle(create, new Color(47, 150, 72));
        create.setText("TẠO TRẬN");
        create.setPreferredSize(new Dimension(220, 46));
        create.setFont(new Font("Arial", Font.BOLD, 17));
        JPanel button = new JPanel(new FlowLayout(FlowLayout.CENTER));
        button.setOpaque(false);
        button.add(create);
        card.add(button, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildGuideCard() {
        JPanel card = dashboardCard();
        card.add(cardTitle("HƯỚNG DẪN CÁCH CHƠI"), BorderLayout.NORTH);
        JTextArea text = new JTextArea("1. Tạo trận và mời đối thủ\n2. Chọn chế độ chơi\n3. Di chuyển giỏ để hứng quả\n4. Hứng đúng loại quả để ghi điểm");
        text.setOpaque(false);
        text.setEditable(false);
        text.setFont(new Font("Arial", Font.PLAIN, 16));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        card.add(text, BorderLayout.CENTER);
        JButton details = buildBtn("Xem chi tiết", new Color(30, 110, 85));
        details.setPreferredSize(new Dimension(150, 36));
        details.addActionListener(e -> showGuideDetails());
        JPanel button = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        button.setOpaque(false);
        button.add(details);
        card.add(button, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildDiscoveryPanel() {
        JPanel panel = dashboardCard();
        panel.add(cardTitle("KHÁM PHÁ HOA QUẢ"), BorderLayout.NORTH);
        JPanel fruits = new JPanel(new GridLayout(1, Math.max(1, Math.min(6, fruitCatalog.size())), 10, 0));
        fruits.setOpaque(false);
        if (fruitCatalog.isEmpty()) {
            JLabel loading = new JLabel("Đang tải danh sách hoa quả...", SwingConstants.CENTER);
            loading.setFont(new Font("Arial", Font.PLAIN, 15));
            fruits.add(loading);
        } else {
            fruitCatalog.stream().limit(6).forEach(fruit -> addFruitCard(fruits, fruit));
        }
        panel.add(fruits, BorderLayout.CENTER);
        JButton all = buildBtn("Xem tất cả hoa quả", new Color(30, 110, 85));
        all.setPreferredSize(new Dimension(190, 34));
        all.addActionListener(e -> showAllFruits());
        JPanel button = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        button.setOpaque(false);
        button.add(all);
        panel.add(button, BorderLayout.SOUTH);
        return panel;
    }

    private void addFruitCard(JPanel parent, String name, String group, String asset, String code) {
        addFruitCard(parent, name, group, asset, code, "");
    }

    private void addFruitCard(JPanel parent, String name, String group, String asset, String code, String nutritionLabels) {
        addFruitCard(parent, name, group, asset, code, nutritionLabels, "");
    }

    private void addFruitCard(JPanel parent, String name, String group, String asset, String code, String nutritionLabels, String description) {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(new Color(255, 255, 255, 205));
        card.setBorder(BorderFactory.createLineBorder(new Color(75, 140, 95), 1));
        card.setPreferredSize(new Dimension(150, 150));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        ImageIcon icon = AssetLoader.icon(asset);
        JLabel image = new JLabel(icon == null ? new ImageIcon() : AssetLoader.scaleToFit(icon, 58, 58));
        image.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel(name, SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Arial", Font.BOLD, 15));
        JLabel subtitle = new JLabel(group, SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("Arial", Font.PLAIN, 12));
        card.add(Box.createVerticalStrut(5));
        card.add(image);
        card.add(title);
        card.add(subtitle);
        card.setToolTipText("Xem thông tin " + name);
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                showFruitDetails(name, group, code, nutritionLabels, description, asset);
            }
        });
        parent.add(card);
    }

    private void addFruitCard(JPanel parent, FruitCatalogItem fruit) {
        addFruitCard(parent, fruit.fruitName(), fruit.groupName(), normalizeAsset(fruit.assetPath()), fruit.groupCode(), fruit.nutritionLabels(), fruit.description());
    }

    private String normalizeAsset(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        int marker = normalized.indexOf("Client/assets/");
        return marker >= 0 ? normalized.substring(marker + "Client/assets/".length()) : normalized;
    }

    private void refreshDiscovery() {
        if (discoveryHost == null) return;
        discoveryHost.removeAll();
        discoveryHost.add(buildDiscoveryPanel(), BorderLayout.CENTER);
        discoveryHost.revalidate();
        discoveryHost.repaint();
    }

    private JPanel dashboardCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setOpaque(true);
        card.setBackground(new Color(255, 255, 255, 224));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 82, 55), 2),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        return card;
    }

    private JLabel cardTitle(String text) {
        JLabel title = new JLabel(text, SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        title.setForeground(new Color(17, 83, 54));
        return title;
    }

    private void showGuideDetails() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setMaximumSize(new Dimension(500, Integer.MAX_VALUE));
        content.add(dialogSection("CÁCH CHƠI", "Thi đấu 1 vs 1 trong 30 giây. Di chuyển giỏ bằng phím mũi tên và hứng các quả phù hợp."));
        content.add(Box.createVerticalStrut(10));
        content.add(dialogSection("CHẾ ĐỘ 1 · FRUIT GROUP", modeDescriptions.getOrDefault("FRUIT_GROUP", "Chọn đúng giỏ theo nhóm của quả.")));
        content.add(Box.createVerticalStrut(10));
        content.add(dialogSection("CHẾ ĐỘ 2 · NUTRITION", modeDescriptions.getOrDefault("NUTRITION", "Hứng những quả phù hợp với mục tiêu dinh dưỡng của trận.")));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setViewPosition(new Point(0, 0));
        showStyledDialog("Hướng dẫn cách chơi", scroll, 560, 520);
    }

    private void showFruitDetails(String name, String group, String code) {
        showFruitDetails(name, group, code, "", "", "");
    }

    private void showFruitDetails(String name, String group, String code, String nutritionLabels) {
        showFruitDetails(name, group, code, nutritionLabels, "", "");
    }

    private void showFruitDetails(String name, String group, String code, String nutritionLabels, String description, String asset) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));
        JLabel title = new JLabel(name, SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setForeground(new Color(17, 83, 54));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        ImageIcon fruitIcon = AssetLoader.icon(asset);
        if (fruitIcon != null) {
            JLabel image = new JLabel(AssetLoader.scaleToFit(fruitIcon, 120, 120));
            image.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(Box.createVerticalStrut(8));
            content.add(image);
        }
        content.add(Box.createVerticalStrut(14));
        String labels = nutritionLabels == null || nutritionLabels.isBlank() ? "Chưa có dữ liệu" : nutritionLabels;
        JLabel info = new JLabel("<html><div style='text-align:center'>Nhóm: <b>" + group
                + "</b><br><br>Đặc tính dinh dưỡng:<br><b>" + labels + "</b></div></html>", SwingConstants.CENTER);
        info.setFont(new Font("Arial", Font.PLAIN, 16));
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(info);
        if (description != null && !description.isBlank()) {
            content.add(Box.createVerticalStrut(14));
            JTextArea descriptionText = new JTextArea(description);
            descriptionText.setColumns(28);
            descriptionText.setRows(5);
            descriptionText.setLineWrap(true);
            descriptionText.setWrapStyleWord(true);
            descriptionText.setEditable(false);
            descriptionText.setOpaque(false);
            descriptionText.setFont(new Font("Arial", Font.PLAIN, 14));
            descriptionText.setAlignmentX(Component.CENTER_ALIGNMENT);
            descriptionText.setMaximumSize(new Dimension(350, Integer.MAX_VALUE));
            descriptionText.setPreferredSize(new Dimension(350, descriptionText.getPreferredSize().height));
            content.add(descriptionText);
        }
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setViewPosition(new Point(0, 0));
        content.setPreferredSize(new Dimension(400, content.getPreferredSize().height));
        showStyledDialog("Thông tin hoa quả", scroll, 500, 430);
    }

    private JPanel dialogSection(String title, String body) {
        JPanel section = new JPanel(new BorderLayout(8, 5));
        section.setOpaque(true);
        section.setBackground(new Color(244, 251, 246));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(112, 172, 126)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Arial", Font.BOLD, 16));
        heading.setForeground(new Color(22, 105, 66));
        JTextArea text = new JTextArea(body);
        text.setColumns(38);
        text.setRows(3);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(new Font("Arial", Font.PLAIN, 14));
        section.add(heading, BorderLayout.NORTH);
        section.add(text, BorderLayout.CENTER);
        return section;
    }

    private void showAllFruits() {
        JDialog dialog = new JDialog(this, "Tất cả hoa quả", true);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(new Color(255, 252, 239));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));

        JComboBox<String> groupFilter = new JComboBox<>();
        JComboBox<String> nutritionFilter = new JComboBox<>();
        groupFilter.addItem("Tất cả nhóm");
        fruitCatalog.stream().map(FruitCatalogItem::groupName).filter(s -> s != null && !s.isBlank()).distinct().sorted().forEach(groupFilter::addItem);
        nutritionFilter.addItem("Tất cả dinh dưỡng");
        fruitCatalog.stream().flatMap(f -> Arrays.stream(f.nutritionLabels().split("\\s*,\\s*")))
                .map(String::trim).filter(s -> !s.isBlank()).distinct().sorted().forEach(nutritionFilter::addItem);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("Nhóm:")); filters.add(groupFilter);
        filters.add(new JLabel("Dinh dưỡng:")); filters.add(nutritionFilter);
        root.add(filters, BorderLayout.NORTH);

        JPanel grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        grid.setOpaque(false);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JLabel empty = new JLabel("Không có hoa quả phù hợp", SwingConstants.CENTER);
        empty.setForeground(new Color(70, 90, 70));
        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false); footer.add(empty, BorderLayout.CENTER);
        JButton close = buildBtn("Đóng", new Color(30, 110, 85));
        close.addActionListener(e -> dialog.dispose());
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); closePanel.setOpaque(false); closePanel.add(close); footer.add(closePanel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        Runnable refresh = () -> {
            String group = (String) groupFilter.getSelectedItem();
            String nutrition = (String) nutritionFilter.getSelectedItem();
            grid.removeAll();
            int count = 0;
            for (FruitCatalogItem fruit : fruitCatalog) {
                boolean groupMatches = "Tất cả nhóm".equals(group) || group.equals(fruit.groupName());
                boolean nutritionMatches = "Tất cả dinh dưỡng".equals(nutrition)
                        || Arrays.stream(fruit.nutritionLabels().split("\\s*,\\s*")).anyMatch(n -> n.trim().equals(nutrition));
                if (groupMatches && nutritionMatches) { addFruitCard(grid, fruit); count++; }
            }
            empty.setVisible(count == 0);
            grid.revalidate(); grid.repaint();
        };
        groupFilter.addActionListener(e -> refresh.run());
        nutritionFilter.addActionListener(e -> refresh.run());
        refresh.run();

        dialog.setContentPane(root);
        dialog.setSize(760, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showStyledDialog(String title, JComponent content, int width, int height) {
        JDialog dialog = new JDialog(this, title, true);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(255, 252, 239));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        root.add(content, BorderLayout.CENTER);
        JButton close = buildBtn("Đóng", new Color(30, 110, 85));
        close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildLobbySummary() {
        JPanel card = new JPanel(new BorderLayout(12, 16));
        card.setOpaque(true);
        card.setBackground(new Color(255, 255, 255, 224));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 82, 55), 2),
                BorderFactory.createEmptyBorder(28, 34, 28, 34)));
        card.setPreferredSize(new Dimension(560, 300));

        JLabel title = new JLabel("SẢNH CHÍNH", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setForeground(new Color(17, 83, 54));
        card.add(title, BorderLayout.NORTH);

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        String displayName = GameClientSession.instance().identity().displayName();
        JLabel welcome = new JLabel("Xin chào: " + displayName);
        welcome.setFont(new Font("Arial", Font.BOLD, 18));
        totalScoreLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        selfStatusLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        onlineCountLabel.setFont(new Font("Arial", Font.BOLD, 17));
        details.add(welcome);
        details.add(Box.createVerticalStrut(8));
        details.add(totalScoreLabel);
        details.add(Box.createVerticalStrut(22));
        details.add(onlineCountLabel);
        card.add(details, BorderLayout.CENTER);

        applyBtnStyle(create, new Color(47, 150, 72));
        create.setText("TẠO PHÒNG");
        create.setAlignmentX(Component.CENTER_ALIGNMENT);
        create.setPreferredSize(new Dimension(230, 46));
        create.setFont(new Font("Arial", Font.BOLD, 16));
        JPanel createPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        createPanel.setOpaque(false);
        createPanel.add(create);
        card.add(createPanel, BorderLayout.SOUTH);
        return card;
    }

    // ── Protocol handler ──────────────────────────────────────────────────────

    private void send(ProtocolMessage message) {
        try {
            client.send(message);
        } catch (IOException error) {
            notice(error.getMessage());
        }
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        // MATCH_PREPARE is immediately followed by FRUIT_CONFIG/FRUIT_SPAWN.
        // Install the RunGame listener before the TCP reader can dispatch those messages.
        if (message.type().equals("MATCH_PREPARE")) {
            runOnEventThreadAndWait(() -> handle(message));
            return;
        }
        SwingUtilities.invokeLater(() -> handle(message));
    }

    private void runOnEventThreadAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (Exception exception) {
            SwingUtilities.invokeLater(() -> notice("Không thể mở màn chơi: " + exception.getMessage()));
        }
    }

    private void handle(ProtocolMessage m) {
        switch (m.type()) {
            case "ONLINE_PLAYERS_BEGIN" -> {
                collectingPlayers = true;
                players.setRowCount(0);
                onlineCountLabel.setText("Người chơi online: " + m.fields().getOrDefault("count", "0"));
            }
            case "ONLINE_PLAYER" -> {
                if (collectingPlayers && Long.parseLong(m.fields().get("playerId")) != playerId)
                    players.addRow(new Object[]{
                            m.fields().get("playerId"), m.fields().get("displayName"),
                            m.fields().get("totalScore"), m.fields().get("totalGames"),
                            m.fields().get("totalWins"),
                            m.fields().getOrDefault("status", "ONLINE")});
            }
            case "ONLINE_PLAYERS_END" -> collectingPlayers = false;
            case "FRUIT_CATALOG_BEGIN" -> fruitCatalog.clear();
            case "FRUIT_CATALOG_ITEM" -> fruitCatalog.add(new FruitCatalogItem(
                    Integer.parseInt(m.fields().get("fruitId")),
                    Integer.parseInt(m.fields().get("groupId")),
                    m.fields().getOrDefault("groupCode", ""),
                    m.fields().getOrDefault("groupName", ""),
                    m.fields().getOrDefault("fruitCode", ""),
                    m.fields().getOrDefault("fruitName", ""),
                    m.fields().getOrDefault("description", ""),
                    m.fields().getOrDefault("assetPath", ""),
                    m.fields().getOrDefault("nutritionLabels", "")));
            case "FRUIT_CATALOG_END" -> refreshDiscovery();
            case "GAME_MODES_BEGIN" -> modeDescriptions.clear();
            case "GAME_MODE_ITEM" -> modeDescriptions.put(
                    m.fields().getOrDefault("modeCode", ""),
                    m.fields().getOrDefault("description", ""));
            case "PROFILE_DATA" -> totalScoreLabel.setText("Tổng điểm: " + m.fields().getOrDefault("totalScore", "0"));
            case "ROOM_CREATED" -> {
                roomId = Long.parseLong(m.fields().get("roomId"));
                create.setEnabled(false);
                leaveRoom.setEnabled(true);
                invite.setEnabled(true);
                roomStatus.setText("Đang mở phòng chờ #" + roomId + "...");
            }
            case "ROOM_UPDATED" -> {
                openWaitingRoom(m);
            }
            case "ROOM_LEFT" -> {
                roomId = -1;
                create.setEnabled(true);
                leaveRoom.setEnabled(false);
                invite.setEnabled(false);
                start.setEnabled(false);
                mode.setEnabled(true);
                roomStatus.setText("Chưa có phòng");
                String reason = m.fields().getOrDefault("reason", "");
                if ("HOST_CANCELLED".equals(reason)) {
                    notice("Chủ phòng đã giải tán phòng!");
                }
            }
            case "INVITE_NOTIFICATION" -> showInvite(m);
            case "MATCH_PREPARE" -> {
                RunGame game = new RunGame(client, playerId);
                game.onMessage(m);
                game.setVisible(true);
                dispose();
            }
            case "ERROR" -> notice(m.fields().getOrDefault("message", "Yêu cầu bị từ chối"));
            default -> {}
        }
    }

    private void showInvite(ProtocolMessage m) {
        int answer = JOptionPane.showConfirmDialog(this,
                "Bạn nhận được lời mời vào phòng #" + m.fields().get("roomId"),
                "Mời thi đấu", JOptionPane.YES_NO_OPTION);
        send(new ProtocolMessage(answer == JOptionPane.YES_OPTION ? "INVITE_ACCEPT" : "INVITE_REJECT",
                Map.of("inviteId", m.fields().get("inviteId"))));
    }

    private void openWaitingRoom(ProtocolMessage roomUpdate) {
        WaitingRoomScreen waitingRoom = new WaitingRoomScreen(playerId, roomUpdate);
        waitingRoom.setVisible(true);
        dispose();
    }

    private void notice(String text) {
        JOptionPane.showMessageDialog(this, text, "Fruit Battle", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void dispose() {
        client.removeListener(this);
        super.dispose();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Updates the mode icon label whenever the combobox selection changes. */
    private void updateModeImage(String modeCode) {
        String asset = "NUTRITION".equals(modeCode) ? "modes/mode-nutrition.png" : "modes/mode-fruit-group.png";
        ImageIcon icon = AssetLoader.icon(asset);
        if (icon != null) {
            modeImgLabel.setIcon(AssetLoader.scaleToFit(icon, 26, 26));
        }
    }

    private JButton buildBtn(String text, Color color) {
        JButton btn = new JButton(text);
        applyBtnStyle(btn, color);
        return btn;
    }

    private void applyBtnStyle(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Arial", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(110, 34));
    }

    private record FruitCatalogItem(int fruitId, int groupId, String groupCode, String groupName,
                                    String fruitCode, String fruitName, String description,
                                    String assetPath, String nutritionLabels) {}
}

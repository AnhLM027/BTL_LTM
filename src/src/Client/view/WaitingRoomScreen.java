package Client.view;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Client.ui.AssetPaths;
import Client.ui.ScaledBackgroundPanel;
import Server.network.ProtocolMessage;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Visual 1v1 waiting room. Room state and all actions remain TCP/server driven.
 */
public final class WaitingRoomScreen extends JPanel implements TcpGameClient.MessageListener, ClientScreen {
    private static final String ASSET_ROOT = AssetPaths.WAITING_ROOM;
    private static final Color BROWN = new Color(83, 43, 20);
    private static final Color CREAM = new Color(255, 248, 222);

    private final TcpGameClient client = GameClientSession.instance().client();
    private final long playerId;
    private final Map<Long, OnlinePlayer> onlinePlayers = new LinkedHashMap<>();
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"CLASSIC", "ORDER"});

    private final JLabel roomLabel = label("", 19, Font.BOLD, BROWN, SwingConstants.CENTER);
    private final JLabel hostName = label("Đang tải...", 24, Font.BOLD, BROWN, SwingConstants.CENTER);
    private final JLabel hostSubtitle = label("", 15, Font.PLAIN, BROWN, SwingConstants.CENTER);
    private final JLabel hostReady = label("", 16, Font.BOLD, new Color(20, 104, 56), SwingConstants.CENTER);
    private final JLabel guestName = label("Đang chờ đối thủ", 24, Font.BOLD, BROWN, SwingConstants.CENTER);
    private final JLabel guestSubtitle = label("Mời bạn vào chơi cùng", 15, Font.PLAIN, BROWN, SwingConstants.CENTER);
    private final JLabel guestReady = label("", 16, Font.BOLD, new Color(20, 104, 56), SwingConstants.CENTER);
    private final JLabel hostAvatar = new JLabel("", SwingConstants.CENTER);
    private final JLabel guestAvatar = new JLabel("", SwingConstants.CENTER);
    private final JLabel modeRuleLabel = label("", 16, Font.BOLD, BROWN, SwingConstants.CENTER);
    private final JLabel roomStatusLabel = label("Đang tải trạng thái phòng...", 16, Font.BOLD, BROWN, SwingConstants.CENTER);
    private final ImageButton inviteButton = new ImageButton("Mời người chơi", "buttons/green.png");
    private final ImageButton startButton = new ImageButton("Bắt đầu trận", "buttons/disabled.png");
    private final ImageButton leaveButton = new ImageButton("Rời phòng", "buttons/orange.png");

    private long roomId = -1;
    private long hostPlayerId = -1;
    private boolean hasGuest;
    private boolean applyingRoomUpdate;
    private boolean bufferingMatchSnapshot;
    private final List<ProtocolMessage> pendingMatchSnapshot = new ArrayList<>();

    public WaitingRoomScreen(long playerId, ProtocolMessage initialRoom) {
        this.playerId = playerId;
        client.addListener(this);

        setLayout(new BorderLayout());

        ScaledBackgroundPanel root = new ScaledBackgroundPanel(AssetLoader.icon("backgrounds/orchard.png"));
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder());
        add(root, BorderLayout.CENTER);
        root.add(buildContent(), BorderLayout.CENTER);

        mode.addActionListener(this::changeMode);
        inviteButton.addActionListener(event -> openInviteDialog());
        startButton.addActionListener(event -> send(new ProtocolMessage(
                "START_MATCH", Map.of("roomId", Long.toString(roomId)))));
        leaveButton.addActionListener(event -> {
            if (roomId > 0) send(new ProtocolMessage("LEAVE_ROOM", Map.of("roomId", Long.toString(roomId))));
        });

        if (initialRoom != null) handle(initialRoom);
        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
        // Re-read the authoritative room state after returning from a match.
        // The local result screen is only an immediate transition hint.
        send(ProtocolMessage.of("GET_ROOM_SNAPSHOT"));
    }

    private JPanel buildContent() {
        DesignCanvas canvas = new DesignCanvas();

        canvas.addAt(new JLabel(scale(asset("icons/logo.png"), 168, 100)), 42, 24, 168, 100);

        ImagePanel header = new ImagePanel(asset("panels/header.png"));
        header.setLayout(new BorderLayout());
        JLabel title = label("PHÒNG CHỜ", 38, Font.BOLD, BROWN, SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(14, 10, 0, 10));
        header.add(title, BorderLayout.CENTER);
        canvas.addAt(header, 260, 24, 560, 120);

        ImagePanel roomInfo = new ImagePanel(asset("panels/room_info.png"));
        roomInfo.setLayout(new BorderLayout());
        roomInfo.add(roomLabel, BorderLayout.CENTER);
        canvas.addAt(roomInfo, 390, 160, 300, 34);

        canvas.addAt(buildPlayerCard(true), 138, 204, 320, 340);
        canvas.addAt(new JLabel(scale(asset("icons/vs.png"), 136, 94), SwingConstants.CENTER),
                472, 322, 136, 94);
        canvas.addAt(buildPlayerCard(false), 622, 204, 320, 340);

        canvas.addAt(buildModeBar(), 48, 568, 984, 92);

        leaveButton.setPreferredSize(new Dimension(196, 56));
        canvas.addAt(leaveButton, 48, 688, 196, 56);

        ImagePanel waitingStatus = new ImagePanel(asset("panels/waiting_status.png"));
        waitingStatus.setLayout(new BorderLayout());
        waitingStatus.add(roomStatusLabel, BorderLayout.CENTER);
        canvas.addAt(waitingStatus, 580, 694, 196, 44);

        startButton.setPreferredSize(new Dimension(240, 56));
        canvas.addAt(startButton, 792, 688, 240, 56);
        return canvas;
    }

    private JPanel buildHeader() {
        ImagePanel header = new ImagePanel(asset("panels/header.png"));
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(600, 142));
        JLabel title = label("PHÒNG CHỜ", 42, Font.BOLD, BROWN, SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20));
        header.add(title, BorderLayout.CENTER);
        roomLabel.setBorder(BorderFactory.createEmptyBorder(0, 15, 17, 15));
        header.add(roomLabel, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(header);
        return wrapper;
    }

    private JPanel buildPlayersRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;

        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 20, 0, 8);
        row.add(buildPlayerCard(true), c);

        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        JLabel versus = new JLabel(scale(asset("icons/vs.png"), 135, 135));
        versus.setHorizontalAlignment(SwingConstants.CENTER);
        row.add(versus, c);

        c.gridx = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 8, 0, 20);
        row.add(buildPlayerCard(false), c);
        return row;
    }

    private JPanel buildPlayerCard(boolean hostCard) {
        ImagePanel card = new ImagePanel(asset(hostCard ? "panels/player_blue.png" : "panels/player_orange.png"));
        card.setLayout(new BorderLayout(6, 4));
        card.setPreferredSize(new Dimension(320, 340));
        card.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));

        JLabel avatar = hostCard ? hostAvatar : guestAvatar;
        avatar.setPreferredSize(new Dimension(166, 166));
        avatar.setVerticalAlignment(SwingConstants.BOTTOM);
        card.add(avatar, BorderLayout.CENTER);

        JPanel details = new JPanel(new GridLayout(hostCard ? 3 : 4, 1, 1, 1));
        details.setOpaque(false);
        details.add(hostCard ? hostName : guestName);
        details.add(hostCard ? hostSubtitle : guestSubtitle);
        details.add(hostCard ? hostReady : guestReady);
        if (!hostCard) {
            inviteButton.setPreferredSize(new Dimension(220, 52));
            JPanel inviteRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            inviteRow.setOpaque(false);
            inviteRow.add(inviteButton);
            details.add(inviteRow);
        }
        card.add(details, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildModeBar() {
        ImagePanel bar = new ImagePanel(asset("panels/mode2.png"));
        bar.setLayout(new GridBagLayout());
        bar.setPreferredSize(new Dimension(984, 92));
        bar.setBorder(BorderFactory.createEmptyBorder(10, 26, 10, 26));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 9, 0, 9);
        c.anchor = GridBagConstraints.CENTER;

        c.gridx = 0;
        JLabel modeTitle = label("CHẾ ĐỘ", 18, Font.BOLD, BROWN, SwingConstants.CENTER);
        bar.add(modeTitle, c);
        c.gridx = 1;
        mode.setFont(new Font("Arial", Font.BOLD, 16));
        mode.setForeground(BROWN);
        mode.setPreferredSize(new Dimension(150, 34));
        bar.add(mode, c);
        c.gridx = 2;
        bar.add(separator(), c);
        c.gridx = 3;
        bar.add(iconText("icons/apple.png", "Hứng quả +10"), c);
        c.gridx = 4;
        bar.add(label("•", 20, Font.BOLD, BROWN, SwingConstants.CENTER), c);
        c.gridx = 5;
        bar.add(iconText("icons/bomb.png", "Trúng bom -10"), c);
        c.gridx = 6;
        bar.add(separator(), c);
        c.gridx = 7;
        bar.add(iconText("icons/clock.png", "30 giây"), c);
        c.gridx = 8;
        bar.add(iconText("icons/arrow_right.png", "Di chuyển giỏ"), c);

        return bar;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 8, 0, 8);

        c.gridx = 0;
        c.weightx = .25;
        c.anchor = GridBagConstraints.WEST;
        leaveButton.setPreferredSize(new Dimension(185, 58));
        actions.add(leaveButton, c);

        c.gridx = 1;
        c.weightx = .5;
        c.anchor = GridBagConstraints.CENTER;
        JPanel status = new JPanel(new GridLayout(2, 1, 0, 2));
        status.setOpaque(false);
        status.add(roomStatusLabel);
        status.add(modeRuleLabel);
        actions.add(status, c);

        c.gridx = 2;
        c.weightx = .25;
        c.anchor = GridBagConstraints.EAST;
        startButton.setPreferredSize(new Dimension(220, 58));
        actions.add(startButton, c);
        return actions;
    }

    private JLabel iconText(String iconPath, String text) {
        JLabel value = label(text, 15, Font.BOLD, BROWN, SwingConstants.CENTER);
        value.setIcon(scale(asset(iconPath), 25, 25));
        value.setIconTextGap(6);
        return value;
    }

    private Component separator() {
        JLabel separator = label("|", 26, Font.PLAIN, new Color(177, 112, 46), SwingConstants.CENTER);
        return separator;
    }

    private void changeMode(ActionEvent ignored) {
        if (applyingRoomUpdate || roomId <= 0 || playerId != hostPlayerId) return;
        send(new ProtocolMessage("CHANGE_GAME_MODE", Map.of(
                "roomId", Long.toString(roomId),
                "modeCode", Objects.requireNonNull(mode.getSelectedItem()).toString())));
    }

    @Override
    public void onMessage(ProtocolMessage message) {
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

    private void handle(ProtocolMessage message) {
        if (bufferingMatchSnapshot && isMatchSnapshotMessage(message)) {
            pendingMatchSnapshot.add(message);
            if ("MATCH_PREPARE_END".equals(message.type())) {
                bufferingMatchSnapshot = false;
                List<ProtocolMessage> snapshot = List.copyOf(pendingMatchSnapshot);
                pendingMatchSnapshot.clear();
                openGameSnapshot(snapshot);
            }
            return;
        }
        switch (message.type()) {
            case "ONLINE_PLAYERS_BEGIN" -> onlinePlayers.clear();
            case "ONLINE_PLAYER" -> receiveOnlinePlayer(message);
            case "ROOM_UPDATED" -> applyRoomUpdate(message);
            case "ROOM_LEFT" -> returnToLobby(message.fields().getOrDefault("reason", ""));
            case "MATCH_CANCELLED" -> MainClientFrame.instance().showLobby();
            case "MATCH_PREPARE" -> {
                bufferingMatchSnapshot = true;
                pendingMatchSnapshot.clear();
                pendingMatchSnapshot.add(message);
            }
            case "ROOM_ACTION_REJECTED", "ERROR" ->
                    notice(message.fields().getOrDefault("message", "Yêu cầu bị từ chối"));
            default -> {
            }
        }
    }

    private boolean isMatchSnapshotMessage(ProtocolMessage message) {
        return switch (message.type()) {
            case "FRUIT_CONFIG", "BASKET_CONFIG", "FRUIT_SPAWN", "MATCH_PREPARE_END" -> true;
            default -> false;
        };
    }

    private void receiveOnlinePlayer(ProtocolMessage message) {
        long id = Long.parseLong(message.fields().get("playerId"));
        if (id == playerId) return;
        onlinePlayers.put(id, new OnlinePlayer(id,
                message.fields().getOrDefault("displayName", "Người chơi #" + id),
                message.fields().getOrDefault("status", "ONLINE")));
    }

    private void applyRoomUpdate(ProtocolMessage message) {
        MainClientFrame.instance().rememberRoom(message);
        roomId = Long.parseLong(message.fields().getOrDefault("roomId", "-1"));
        hostPlayerId = Long.parseLong(message.fields().getOrDefault("hostPlayerId", "-1"));
        hasGuest = !message.fields().getOrDefault("guestPlayerId", "").isBlank();
        boolean isHost = hostPlayerId == playerId;
        String guestId = message.fields().getOrDefault("guestPlayerId", "");
        String state = message.fields().getOrDefault("roomState", "WAITING");

        applyingRoomUpdate = true;
        try {
            mode.setSelectedItem(modeCode(message.fields().get("modeId")));
        } finally {
            applyingRoomUpdate = false;
        }

        roomLabel.setText("Phòng #" + roomId + "  •  " + (hasGuest ? "2/2 người chơi" : "1/2 người chơi"));
        hostName.setText(hostPlayerId == playerId ? GameClientSession.instance().identity().displayName()
                : "Người chơi #" + hostPlayerId);
        hostSubtitle.setText(hostPlayerId == playerId ? "Chủ phòng • Bạn" : "Chủ phòng");
        hostReady.setText(ready(message, "hostReady"));
        hostAvatar.setIcon(scale(asset("avatars/player.png"), 176, 155));

        if (hasGuest) {
            boolean localGuest = guestId.equals(Long.toString(playerId));
            guestName.setText(localGuest ? GameClientSession.instance().identity().displayName() : "Người chơi #" + guestId);
            guestSubtitle.setText(localGuest ? "Khách • Bạn" : "Đối thủ");
            guestReady.setText(ready(message, "guestReady"));
            guestAvatar.setIcon(scale(asset("avatars/player.png"), 176, 155));
        } else {
            guestName.setText("Đang chờ đối thủ");
            guestSubtitle.setText("Mời bạn vào chơi cùng");
            guestReady.setText("");
            guestAvatar.setIcon(scale(asset("avatars/waiting.png"), 176, 155));
        }

        mode.setEnabled(isHost && ("WAITING".equals(state) || "FULL".equals(state)));
        inviteButton.setVisible(isHost && !hasGuest);
        inviteButton.setEnabled(isHost && !hasGuest);
        startButton.setEnabled(isHost && hasGuest && "FULL".equals(state));
        startButton.setAsset(startButton.isEnabled() ? "buttons/green.png" : "buttons/disabled.png");
        roomStatusLabel.setText(roomStatus(state));
        modeRuleLabel.setText(modeRule((String) mode.getSelectedItem()));
        repaint();
    }

    private String ready(ProtocolMessage message, String field) {
        return Boolean.parseBoolean(message.fields().getOrDefault(field, "false")) ? "✓ Đã sẵn sàng" : "Đang chờ";
    }

    private String modeCode(String modeId) {
        return "2".equals(modeId) ? "ORDER" : "CLASSIC";
    }

    private String roomStatus(String state) {
        return switch (state) {
            case "FULL" -> "Đã đủ người chơi • Sẵn sàng bắt đầu";
            case "PREPARING" -> "Đang chuẩn bị trận đấu...";
            case "READY" -> "Đang chờ người chơi sẵn sàng";
            default -> "Đang chờ đối thủ";
        };
    }

    private String modeRule(String modeCode) {
        return "ORDER".equals(modeCode) ? "Hứng đúng 3 loại quả trong đơn hàng" : "Hứng quả, né bom • 30 giây";
    }

    private void openInviteDialog() {
        if (roomId <= 0 || hasGuest || playerId != hostPlayerId) return;
        DefaultListModel<OnlinePlayer> candidates = new DefaultListModel<>();
        onlinePlayers.values().stream()
                .filter(player -> "ONLINE".equals(player.status()) || "IN_ROOM".equals(player.status()))
                .forEach(candidates::addElement);
        if (candidates.isEmpty()) {
            notice("Hiện chưa có người chơi phù hợp để mời.");
            return;
        }

        JDialog dialog = new JDialog(MainClientFrame.instance(), "Mời người chơi", true);
        ImagePanel content = new ImagePanel(asset("panels/invite_dialog.png"));
        content.setLayout(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(30, 42, 30, 42));
        JLabel title = label("MỜI NGƯỜI CHƠI", 24, Font.BOLD, BROWN, SwingConstants.CENTER);
        content.add(title, BorderLayout.NORTH);
        JList<OnlinePlayer> list = new JList<>(candidates);
        list.setFont(new Font("Arial", Font.PLAIN, 16));
        list.setBackground(new Color(255, 250, 232, 220));
        list.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        content.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        bottom.setOpaque(false);
        ImageButton cancel = new ImageButton("Đóng", "buttons/orange.png");
        ImageButton confirm = new ImageButton("Gửi lời mời", "buttons/green.png");
        cancel.setPreferredSize(new Dimension(130, 42));
        confirm.setPreferredSize(new Dimension(175, 42));
        cancel.addActionListener(event -> dialog.dispose());
        confirm.addActionListener(event -> {
            OnlinePlayer selected = list.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "Hãy chọn một người chơi.", "Fruit Battle", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            send(new ProtocolMessage("INVITE_PLAYER", Map.of(
                    "roomId", Long.toString(roomId), "playerId", Long.toString(selected.id()))));
            dialog.dispose();
        });
        bottom.add(cancel);
        bottom.add(confirm);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(520, 410);
        dialog.setLocationRelativeTo(MainClientFrame.instance());
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private void openGame(ProtocolMessage message) {
        MainClientFrame.instance().showGame(client, playerId, message);
    }

    private void openGameSnapshot(List<ProtocolMessage> snapshot) {
        if (snapshot.isEmpty()) return;
        openGame(snapshot.get(0));
        RunGame game = MainClientFrame.instance().currentGame();
        if (game != null) for (ProtocolMessage message : snapshot.subList(1, snapshot.size())) game.onMessage(message);
    }

    private void returnToLobby(String reason) {
        MainClientFrame.instance().showLobby();
        if ("HOST_CANCELLED".equals(reason)) {
            JOptionPane.showMessageDialog(MainClientFrame.instance(), "Chủ phòng đã giải tán phòng.", "Fruit Battle", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void send(ProtocolMessage message) {
        try {
            client.send(message);
        } catch (IOException exception) {
            notice(exception.getMessage());
        }
    }

    private void notice(String text) {
        JOptionPane.showMessageDialog(this, text, "Fruit Battle", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onHide() {
        client.removeListener(this);
    }

    private static ImageIcon asset(String path) {
        return AssetLoader.icon(ASSET_ROOT + path);
    }

    private static ImageIcon scale(ImageIcon source, int width, int height) {
        return AssetLoader.scaleToFit(source, width, height);
    }

    private static JLabel label(String text, int size, int style, Color color, int alignment) {
        JLabel label = new JLabel(text, alignment);
        label.setFont(new Font("Arial", style, size));
        label.setForeground(color);
        return label;
    }

    private record OnlinePlayer(long id, String displayName, String status) {
        @Override
        public String toString() {
            return displayName + "  •  " + switch (status) {
                case "IN_ROOM" -> "Trong phòng chờ";
                default -> "Đang ở sảnh";
            };
        }
    }

    /**
     * Positions the v4 artwork on its 1080x760 design canvas, then applies one
     * uniform scale factor when the MainClientFrame is resized.
     */
    private static final class DesignCanvas extends JPanel {
        private static final int DESIGN_WIDTH = 1_080;
        private static final int DESIGN_HEIGHT = 760;
        private final Map<Component, Rectangle> designBounds = new LinkedHashMap<>();

        private DesignCanvas() {
            setOpaque(false);
            setLayout(null);
        }

        private void addAt(Component component, int x, int y, int width, int height) {
            designBounds.put(component, new Rectangle(x, y, width, height));
            add(component);
        }

        @Override
        public void doLayout() {
            double scale = Math.min(getWidth() / (double) DESIGN_WIDTH,
                    getHeight() / (double) DESIGN_HEIGHT);
            int offsetX = (int) Math.round((getWidth() - DESIGN_WIDTH * scale) / 2.0);
            int offsetY = (int) Math.round((getHeight() - DESIGN_HEIGHT * scale) / 2.0);
            for (Map.Entry<Component, Rectangle> entry : designBounds.entrySet()) {
                Rectangle box = entry.getValue();
                entry.getKey().setBounds(
                        offsetX + (int) Math.round(box.x * scale),
                        offsetY + (int) Math.round(box.y * scale),
                        Math.max(1, (int) Math.round(box.width * scale)),
                        Math.max(1, (int) Math.round(box.height * scale)));
            }
        }
    }

    private static final class ImagePanel extends JPanel {
        private final ImageIcon background;

        private ImagePanel(ImageIcon background) {
            this.background = background;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (background == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(background.getImage(), 0, 0, getWidth(), getHeight(), this);
            g.dispose();
        }
    }

    private static final class ImageButton extends JButton {
        private ImageIcon background;

        private ImageButton(String text, String assetPath) {
            super(text);
            setAsset(assetPath);
            setFont(new Font("Arial", Font.BOLD, 16));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        private void setAsset(String assetPath) {
            background = asset(assetPath);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            if (background != null) {
                float alpha = isEnabled() ? 1f : .72f;
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                g.drawImage(background.getImage(), 0, 0, getWidth(), getHeight(), this);
            } else {
                g.setColor(isEnabled() ? new Color(35, 138, 59) : Color.GRAY);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}

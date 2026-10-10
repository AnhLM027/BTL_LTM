package Client.view;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;
import Server.network.ProtocolMessage;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Waiting room shown after CREATE_ROOM or INVITE_ACCEPT.
 * The server remains authoritative; this screen only renders ROOM_UPDATED and
 * sends host/guest actions back through the persistent TCP session.
 */
public final class WaitingRoomScreen extends JFrame implements TcpGameClient.MessageListener {
    private final TcpGameClient client = GameClientSession.instance().client();
    private final long playerId;
    private final DefaultTableModel players = new DefaultTableModel(
            new String[]{"ID", "Người chơi", "Điểm", "Trận", "Thắng"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"CLASSIC", "ORDER"});
    private final JLabel roomLabel = new JLabel("Phòng chờ", SwingConstants.CENTER);
    private final JLabel hostLabel = new JLabel("HOST: —");
    private final JLabel guestLabel = new JLabel("GUEST: Đang chờ người chơi");
    private final JLabel stateLabel = new JLabel("Trạng thái: ROOM_WAITING");
    private final JLabel readyLabel = new JLabel("Sẵn sàng: HOST — | GUEST —");
    private final JButton invite = new JButton("Mời người chơi");
    private final JButton start = new JButton("Bắt đầu trận");
    private final JButton leave = new JButton("Rời phòng");
    private final JLabel modeImage = new JLabel();
    private JTable playerTable;
    private long roomId = -1;
    private long hostPlayerId = -1;
    private boolean hasGuest;
    private boolean collectingPlayers;
    private boolean applyingRoomUpdate;

    public WaitingRoomScreen(long playerId, ProtocolMessage initialRoom) {
        this.playerId = playerId;
        client.addListener(this);
        setTitle("Fruit Battle Online — Phòng chờ");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 590);
        setLocationRelativeTo(null);

        ImageIcon background = AssetLoader.icon("backgrounds/lobby-orchard-background.png");
        ScaledBackgroundPanel panel = new ScaledBackgroundPanel(background);
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setContentPane(panel);

        roomLabel.setForeground(Color.WHITE);
        roomLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(roomLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(buildRoomCard(), BorderLayout.NORTH);

        players.addColumn("Trạng thái");
        players.addColumn("Mời");
        playerTable = new JTable(players);
        JTable table = playerTable;
        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerTable.setRowHeight(26);
        playerTable.setFont(new Font("Arial", Font.PLAIN, 13));
        playerTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        playerTable.getTableHeader().setBackground(new Color(0, 80, 160, 210));
        playerTable.getTableHeader().setForeground(Color.WHITE);
        playerTable.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
        playerTable.getColumnModel().getColumn(6).setCellRenderer(new InviteButtonRenderer());
        playerTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = playerTable.rowAtPoint(event.getPoint());
                int column = playerTable.columnAtPoint(event.getPoint());
                if (row < 0 || column != 6 || !"Mời".equals(players.getValueAt(row, column))) return;
                send(new ProtocolMessage("INVITE_PLAYER", Map.of(
                        "roomId", Long.toString(roomId),
                        "playerId", players.getValueAt(row, 0).toString())));
            }
        });
        JScrollPane scroll = new JScrollPane(playerTable);
        scroll.getViewport().setBackground(new Color(255, 255, 255, 190));
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 150)),
                "Người chơi online để mời", 0, 0,
                new Font("Arial", Font.BOLD, 13), Color.WHITE));
        center.add(scroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 9, 4));
        actions.setOpaque(false);
        styleButton(invite, new Color(0, 102, 204));
        styleButton(start, new Color(160, 90, 0));
        styleButton(leave, new Color(190, 75, 20));
        actions.add(start);
        actions.add(leave);
        panel.add(actions, BorderLayout.SOUTH);

        mode.addActionListener(event -> {
            if (applyingRoomUpdate || roomId <= 0 || playerId != hostPlayerId) return;
            send(new ProtocolMessage("CHANGE_GAME_MODE", Map.of(
                    "roomId", Long.toString(roomId),
                    "modeCode", Objects.requireNonNull(mode.getSelectedItem()).toString())));
        });
        invite.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                notice("Chọn một người chơi để mời");
                return;
            }
            send(new ProtocolMessage("INVITE_PLAYER", Map.of(
                    "roomId", Long.toString(roomId),
                    "playerId", players.getValueAt(row, 0).toString())));
        });
        start.addActionListener(event -> send(new ProtocolMessage(
                "START_MATCH", Map.of("roomId", Long.toString(roomId)))));
        leave.addActionListener(event -> {
            if (roomId > 0) send(new ProtocolMessage("LEAVE_ROOM", Map.of("roomId", Long.toString(roomId))));
        });

        if (initialRoom != null) handle(initialRoom);
        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
        setVisible(true);
    }

    private JPanel buildRoomCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(true);
        card.setBackground(new Color(20, 55, 95, 220));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 8, 3, 18);

        c.gridx = 0; c.gridy = 0; card.add(hostLabel, c);
        c.gridx = 1; card.add(guestLabel, c);
        c.gridx = 0; c.gridy = 1; card.add(stateLabel, c);
        c.gridx = 1; card.add(readyLabel, c);
        c.gridx = 0; c.gridy = 2; card.add(new JLabel("Kiểu chơi:"), c);
        c.gridx = 1; card.add(modeImage, c);
        c.gridx = 2; card.add(mode, c);
        for (Component component : card.getComponents()) {
            if (component instanceof JLabel label) {
                label.setForeground(Color.WHITE);
                label.setFont(new Font("Arial", Font.BOLD, 13));
            }
        }
        updateModeImage((String) mode.getSelectedItem());
        return card;
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        // MATCH_PREPARE is immediately followed by configuration messages.
        // Install RunGame before the TCP reader dispatches the next message.
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

    private void handle(ProtocolMessage message) {
        switch (message.type()) {
            case "ONLINE_PLAYERS_BEGIN" -> {
                collectingPlayers = true;
                players.setRowCount(0);
            }
            case "ONLINE_PLAYER" -> {
                if (collectingPlayers && Long.parseLong(message.fields().get("playerId")) != playerId) {
                    players.addRow(new Object[]{
                            message.fields().get("playerId"), message.fields().get("displayName"),
                            message.fields().get("totalScore"), message.fields().get("totalGames"),
                            message.fields().get("totalWins"),
                            message.fields().getOrDefault("status", "ONLINE"),
                            "ONLINE".equals(message.fields().getOrDefault("status", "ONLINE"))
                                    || "IN_ROOM".equals(message.fields().getOrDefault("status", "ONLINE")) ? "Mời" : ""});
                }
            }
            case "ONLINE_PLAYERS_END" -> collectingPlayers = false;
            case "ROOM_UPDATED" -> applyRoomUpdate(message);
            case "ROOM_LEFT" -> returnToLobby(message.fields().getOrDefault("reason", ""));
            case "MATCH_PREPARE" -> openGame(message);
            case "ROOM_ACTION_REJECTED" -> notice(message.fields().getOrDefault("message", "Yêu cầu bị từ chối"));
            case "ERROR" -> notice(message.fields().getOrDefault("message", "Yêu cầu bị từ chối"));
            default -> { }
        }
    }

    private void applyRoomUpdate(ProtocolMessage message) {
        roomId = Long.parseLong(message.fields().get("roomId"));
        hostPlayerId = Long.parseLong(message.fields().get("hostPlayerId"));
        hasGuest = !message.fields().getOrDefault("guestPlayerId", "").isBlank();
        boolean host = hostPlayerId == playerId;
        String guestId = hasGuest ? message.fields().get("guestPlayerId") : "Đang chờ người chơi";

        applyingRoomUpdate = true;
        try {
            mode.setSelectedItem(modeCode(message.fields().get("modeId")));
        } finally {
            applyingRoomUpdate = false;
        }
        updateModeImage((String) mode.getSelectedItem());
        roomLabel.setText("Phòng #" + roomId + " — Phòng chờ");
        hostLabel.setText("HOST: #" + hostPlayerId + (host ? " (Bạn)" : ""));
        guestLabel.setText("GUEST: " + (hasGuest ? "#" + guestId + (guestId.equals(Long.toString(playerId)) ? " (Bạn)" : "") : guestId));
        stateLabel.setText("Trạng thái: " + message.fields().getOrDefault("roomState", "WAITING"));
        readyLabel.setText("Sẵn sàng: HOST " + ready(message, "hostReady") + " | GUEST " + ready(message, "guestReady"));
        String roomState = message.fields().getOrDefault("roomState", "WAITING");
        mode.setEnabled(host && ("WAITING".equals(roomState) || "FULL".equals(roomState)));
        invite.setEnabled(host && !hasGuest);
        start.setEnabled(host && hasGuest && "FULL".equals(roomState));
        leave.setEnabled(true);
        refreshInviteActions();
    }

    private String ready(ProtocolMessage message, String field) {
        return Boolean.parseBoolean(message.fields().getOrDefault(field, "false")) ? "✓" : "—";
    }

    private String modeCode(String modeId) {
        return "2".equals(modeId) ? "ORDER" : "CLASSIC";
    }

    private void refreshInviteActions() {
        if (playerTable == null) return;
        boolean canInvite = playerId == hostPlayerId && !hasGuest;
        for (int row = 0; row < players.getRowCount(); row++) {
            String status = String.valueOf(players.getValueAt(row, 5));
            players.setValueAt(canInvite && ("ONLINE".equals(status) || "IN_ROOM".equals(status)) ? "Mời" : "", row, 6);
        }
    }

    private void openGame(ProtocolMessage message) {
        RunGame game = new RunGame(client, playerId);
        game.onMessage(message);
        game.setVisible(true);
        dispose();
    }

    private void returnToLobby(String reason) {
        dispose();
        LobbyScreen lobby = new LobbyScreen(Long.toString(playerId), GameClientSession.instance().identity().displayName());
        if ("HOST_CANCELLED".equals(reason)) {
            JOptionPane.showMessageDialog(lobby, "Chủ phòng đã giải tán phòng.", "Fruit Battle", JOptionPane.INFORMATION_MESSAGE);
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
    public void dispose() {
        client.removeListener(this);
        super.dispose();
    }

    private void updateModeImage(String modeCode) {
        String asset = "ORDER".equals(modeCode) ? "modes/mode-nutrition.png" : "modes/mode-fruit-group.png";
        ImageIcon icon = AssetLoader.icon(asset);
        if (icon != null) {
            modeImage.setIcon(AssetLoader.scaleToFit(icon, 26, 26));
        }
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(140, 34));
    }

    private static final class InviteButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                        boolean focused, int row, int column) {
            setText(value == null ? "" : value.toString());
            setEnabled(!getText().isBlank());
            setFont(new Font("Arial", Font.BOLD, 12));
            setForeground(Color.WHITE);
            setBackground(new Color(0, 102, 204));
            setFocusPainted(false);
            return this;
        }
    }

    private static final class StatusRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        StatusRenderer() {
            setOpaque(true);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                        boolean focused, int row, int column) {
            String status = value == null ? "ONLINE" : value.toString();
            setText(switch (status) {
                case "IN_ROOM" -> "Trong phòng";
                case "READY" -> "Đã sẵn sàng";
                case "PLAYING" -> "Đang thi đấu";
                default -> "Đang ở sảnh";
            });
            setBackground(selected ? table.getSelectionBackground() : Color.WHITE);
            setForeground(selected ? table.getSelectionForeground() : Color.DARK_GRAY);
            return this;
        }
    }
}

package Client.view;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Server.network.ProtocolMessage;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public final class HistoryScreen extends JFrame {
    private final DefaultTableModel rows = new DefaultTableModel(
            new String[]{"Mã trận", "Thời gian", "Đối thủ", "Mode", "Điểm", "Kết quả"}, 0) {
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };

    public HistoryScreen(String ignoredPlayerId) {
        setTitle("Lịch sử trận đấu");
        setSize(780, 440);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 0));

        // ── Header ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        header.setBackground(new Color(0, 80, 160));
        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            Image scaled = logoIcon.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH);
            header.add(new JLabel(new ImageIcon(scaled)));
        }
        JLabel titleLbl = new JLabel("Lịch sử trận đấu");
        titleLbl.setFont(new Font("Arial", Font.BOLD, 17));
        titleLbl.setForeground(Color.WHITE);
        header.add(titleLbl);
        add(header, BorderLayout.NORTH);

        // ── Table ────────────────────────────────────────────────────────────
        JTable table = new JTable(rows);
        table.setRowHeight(26);
        table.setFont(new Font("Arial", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(0, 80, 160));
        table.getTableHeader().setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(173, 214, 255));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(scroll, BorderLayout.CENTER);

        // ── Back button ──────────────────────────────────────────────────────
        JButton back = new JButton("Trở về");
        back.setBackground(new Color(80, 80, 80));
        back.setForeground(Color.WHITE);
        back.setFocusPainted(false);
        back.setBorderPainted(false);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> dispose());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        bottomPanel.add(back);
        add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);

        try {
            TcpQuery.collect(GameClientSession.instance().client(),
                    ProtocolMessage.of("GET_MATCH_HISTORY"),
                    "MATCH_HISTORY_ITEM", "MATCH_HISTORY_END")
                    .thenAccept(this::showRows);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void showRows(List<ProtocolMessage> list) {
        SwingUtilities.invokeLater(() -> {
            rows.setRowCount(0);
            for (var m : list)
                rows.addRow(new Object[]{
                        m.fields().get("matchId"),   m.fields().get("endedAt"),
                        m.fields().get("opponentName"), m.fields().get("modeCode"),
                        m.fields().get("score"),     m.fields().get("result")});
        });
    }
}

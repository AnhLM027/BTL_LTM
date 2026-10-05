package Client.view;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Server.network.ProtocolMessage;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public final class RankScreen extends JFrame {
    private final DefaultTableModel rows = new DefaultTableModel(
            new String[]{"#", "Tên", "Điểm", "Thắng", "Trận"}, 0) {
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };

    public RankScreen() {
        setTitle("Bảng xếp hạng");
        setSize(620, 440);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 0));

        // ── Header ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        header.setBackground(new Color(180, 120, 0));
        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            Image scaled = logoIcon.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH);
            header.add(new JLabel(new ImageIcon(scaled)));
        }
        JLabel titleLbl = new JLabel("🏆 Bảng xếp hạng");
        titleLbl.setFont(new Font("Arial", Font.BOLD, 17));
        titleLbl.setForeground(Color.WHITE);
        header.add(titleLbl);
        add(header, BorderLayout.NORTH);

        // ── Table ────────────────────────────────────────────────────────────
        JTable table = new JTable(rows);
        table.setRowHeight(28);
        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(180, 120, 0));
        table.getTableHeader().setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(255, 236, 180));

        // Color top-3 rows with medal tones
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final Color[] MEDAL = {
                new Color(255, 223, 100), // gold
                new Color(210, 210, 210), // silver
                new Color(205, 127, 50),  // bronze
            };
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (!isSelected && row < MEDAL.length) c.setBackground(MEDAL[row]);
                else if (!isSelected) c.setBackground(Color.WHITE);
                return c;
            }
        });

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
                    ProtocolMessage.of("GET_LEADERBOARD"),
                    "LEADERBOARD_PLAYER", "LEADERBOARD_END")
                    .thenAccept(this::showRows);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void showRows(List<ProtocolMessage> list) {
        SwingUtilities.invokeLater(() -> {
            rows.setRowCount(0);
            int rank = 1;
            for (var m : list)
                rows.addRow(new Object[]{rank++,
                        m.fields().get("displayName"), m.fields().get("totalScore"),
                        m.fields().get("totalWins"),   m.fields().get("totalGames")});
        });
    }
}

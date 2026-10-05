package Client.view;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Server.network.ProtocolMessage;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public final class ProfileScreen extends JFrame {
    private final JPanel values = new JPanel(new GridLayout(0, 2, 8, 8));

    public ProfileScreen(String playerId) {
        setTitle("Hồ sơ người chơi");
        setSize(440, 320);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── Header with logo ────────────────────────────────────────────────
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        header.setBackground(new Color(0, 80, 160));
        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            Image scaled = logoIcon.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH);
            header.add(new JLabel(new ImageIcon(scaled)));
        }
        JLabel titleLbl = new JLabel("Hồ sơ người chơi");
        titleLbl.setFont(new Font("Arial", Font.BOLD, 18));
        titleLbl.setForeground(Color.WHITE);
        header.add(titleLbl);
        add(header, BorderLayout.NORTH);

        // ── Values grid ──────────────────────────────────────────────────────
        values.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        values.setBackground(new Color(245, 248, 255));
        add(values, BorderLayout.CENTER);

        // ── Back button ──────────────────────────────────────────────────────
        JButton back = new JButton("Trở về");
        back.setBackground(new Color(80, 80, 80));
        back.setForeground(Color.WHITE);
        back.setFocusPainted(false);
        back.setBorderPainted(false);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> dispose());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.add(back);
        add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);

        try {
            TcpQuery.single(GameClientSession.instance().client(),
                    new ProtocolMessage("GET_PROFILE", Map.of("playerId", playerId)),
                    "PROFILE_DATA")
                    .thenAccept(m -> SwingUtilities.invokeLater(() -> showProfile(m)));
        } catch (Exception e) {
            showError(e);
        }
    }

    private void showProfile(ProtocolMessage m) {
        values.removeAll();
        addRow("Tên hiển thị", m.fields().get("displayName"));
        addRow("Tổng điểm",   m.fields().get("totalScore"));
        addRow("Số trận",     m.fields().get("totalGames"));
        addRow("Số trận thắng", m.fields().get("totalWins"));
        values.revalidate();
        values.repaint();
    }

    private void addRow(String label, String value) {
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("Arial", Font.BOLD, 14));
        JLabel val = new JLabel(value);
        val.setFont(new Font("Arial", Font.PLAIN, 14));
        values.add(lbl);
        values.add(val);
    }

    private void showError(Exception e) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, e.getMessage()));
    }
}

package Client.view;

import javax.swing.*;
import java.awt.*;

/**
 * Compatibility screen: invitation is now integrated into the authoritative TCP lobby.
 */
public final class InviteScreen extends JFrame {
    public InviteScreen(String playerId, String username) {
        setTitle("Mời thi đấu");
        setSize(420, 160);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(new JLabel("Tạo phòng và mời người chơi trực tiếp từ Lobby.", SwingConstants.CENTER), BorderLayout.CENTER);
        JButton back = new JButton("Trở về Lobby");
        back.addActionListener(e -> {
            dispose();
            new LobbyScreen(playerId, username);
        });
        add(back, BorderLayout.SOUTH);
        setVisible(true);
    }
}

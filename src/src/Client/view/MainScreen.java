package Client.view;

import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/** Initial card of the player client. */
public final class MainScreen extends JPanel implements ClientScreen {
    public MainScreen() {
        setLayout(new BorderLayout());
        ScaledBackgroundPanel background = new ScaledBackgroundPanel(
                AssetLoader.icon("backgrounds/lobby-orchard-background.png"));
        background.setLayout(new BorderLayout());
        add(background, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 0, 28, 0));
        var logo = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logo != null) center.add(new JLabel(AssetLoader.scaleToFit(logo, 180, 180), SwingConstants.CENTER), BorderLayout.CENTER);
        JLabel title = new JLabel("Hứng Hoa Quả", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 34));
        title.setForeground(new Color(17, 83, 54));
        center.add(title, BorderLayout.SOUTH);
        background.add(center, BorderLayout.CENTER);

        JButton login = button("Đăng nhập", new Color(0, 102, 204));
        JButton register = button("Đăng ký", new Color(204, 102, 0));
        login.addActionListener(event -> MainClientFrame.instance().showLogin());
        register.addActionListener(event -> MainClientFrame.instance().showRegister());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 42, 0));
        actions.add(login);
        actions.add(register);
        background.add(actions, BorderLayout.SOUTH);
    }

    private JButton button(String text, Color color) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(150, 44));
        button.setFont(new Font("Arial", Font.BOLD, 17));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}

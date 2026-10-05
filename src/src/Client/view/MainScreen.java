package Client.view;

import javax.swing.*;

import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

public class MainScreen {
    private Font customFont;

    public MainScreen() {
        // Load the custom font
        try {
            // Load the font from the resources folder
            customFont = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("../assets/FVF.ttf"));
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("Game phân loại rác");
        frame.setTitle("Hứng Hoa Quả - Thi đấu đối kháng online");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(810, 540);
        frame.setLocationRelativeTo(null);

        // Load and set background
        ImageIcon backgroundIcon = AssetLoader.icon("backgrounds/lobby-orchard-background.png");
        ScaledBackgroundPanel backgroundLabel = new ScaledBackgroundPanel(backgroundIcon);
        backgroundLabel.setLayout(new BorderLayout()); // Use BorderLayout to position components

        // Center panel: logo + title
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 28, 0));
        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            JLabel logoLabel = new JLabel(AssetLoader.scaleToFit(logoIcon, 180, 180), SwingConstants.CENTER);
            centerPanel.add(logoLabel, BorderLayout.CENTER);
        }
        JLabel titleLabel = new JLabel("Hứng Hoa Quả", SwingConstants.CENTER);
        titleLabel.setFont(customFont != null ? customFont.deriveFont(Font.BOLD, 30f) : new Font("Serif", Font.BOLD, 30));
        titleLabel.setForeground(new Color(17, 83, 54));
        centerPanel.add(titleLabel, BorderLayout.SOUTH);
        backgroundLabel.add(centerPanel, BorderLayout.CENTER);

        // Create buttons with different background colors
        JButton btnLogin = createCustomButton("Đăng nhập", new Color(0, 102, 204), new Color(0, 153, 255));
        JButton btnRegister = createCustomButton("Đăng ký", new Color(204, 102, 0), new Color(255, 153, 51));

        // Add action listeners
        btnLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false);
                new LoginScreen();
            }
        });
        btnRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false);
                new RegisterScreen();
            }
        });

        // Create a panel for buttons and position them at the bottom
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false); // Make panel transparent to show background
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20)); // Center buttons with spacing
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 42, 0));
        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);

        // Add button panel to the bottom of the background label
        backgroundLabel.add(buttonPanel, BorderLayout.SOUTH);

        // Add background label to the frame
        frame.add(backgroundLabel);
        frame.setVisible(true);
    }

    private JButton createCustomButton(String text, Color defaultColor, Color hoverColor) {
        // Create a button with a custom size and style
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(120, 40)); // Keep the original button size
        button.setFont(customFont.deriveFont(Font.BOLD, 16f)); // Set the custom font with size
        button.setForeground(Color.WHITE); // Set text color
        button.setBackground(defaultColor); // Set the default background color
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2)); // Optional border
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // Change cursor on hover

        // Add hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor); // Change background color on hover
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(defaultColor); // Reset background color
            }
        });

        return button;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainScreen());
    }
}

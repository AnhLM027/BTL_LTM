package Client.view.admin;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Admin view of the fruit/item catalog.  The catalog is loaded through the
 * authenticated TCP session; it is never hard-coded in the admin client.
 */
public final class ItemManagementScreen extends JFrame {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Hoa quả", "Nhóm", "Dinh dưỡng", "Mô tả", "Asset"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public ItemManagementScreen() {
        setTitle("Quản lý vật phẩm");
        setSize(980, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        table.setAutoCreateRowSorter(true);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(4).setPreferredWidth(360);
        table.getColumnModel().getColumn(5).setPreferredWidth(220);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("Làm mới");
        JButton close = new JButton("Đóng");
        refresh.addActionListener(event -> load());
        close.addActionListener(event -> dispose());
        actions.add(refresh);
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        setVisible(true);
        load();
    }

    private void load() {
        try {
            TcpQuery.collect(GameClientSession.instance().client(),
                    ProtocolMessage.of("GET_FRUIT_CATALOG"),
                    "FRUIT_CATALOG_ITEM", "FRUIT_CATALOG_END")
                    .thenAccept(this::showItems)
                    .exceptionally(error -> {
                        SwingUtilities.invokeLater(() -> notice(error.getMessage()));
                        return null;
                    });
        } catch (Exception error) {
            notice(error.getMessage());
        }
    }

    private void showItems(List<ProtocolMessage> items) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (ProtocolMessage item : items) {
                model.addRow(new Object[]{
                        item.fields().getOrDefault("fruitId", ""),
                        item.fields().getOrDefault("fruitName", ""),
                        item.fields().getOrDefault("groupName", ""),
                        item.fields().getOrDefault("nutritionLabels", "").replace("|", ", "),
                        item.fields().getOrDefault("description", ""),
                        item.fields().getOrDefault("assetPath", "")
                });
            }
        });
    }

    private void notice(String message) {
        JOptionPane.showMessageDialog(this,
                message == null || message.isBlank() ? "Không thể tải danh mục vật phẩm" : message,
                "Quản lý vật phẩm", JOptionPane.INFORMATION_MESSAGE);
    }
}

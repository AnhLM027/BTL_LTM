# Khởi tạo database

Database mặc định của server là `fruit_battle_online` trên MySQL local:

- host: `localhost`
- port: `3306`
- user: `root`
- password: rỗng

TCP server đọc cấu hình JDBC theo system property hoặc environment variable (ưu tiên system property):

| System property | Environment variable | Giá trị mặc định |
| --- | --- | --- |
| `fruitbattle.db.url` | `FRUIT_BATTLE_DB_URL` | `jdbc:mysql://localhost:3306/fruit_battle_online` |
| `fruitbattle.db.user` | `FRUIT_BATTLE_DB_USER` | `root` |
| `fruitbattle.db.password` | `FRUIT_BATTLE_DB_PASSWORD` | rỗng |

Chạy file `schema.sql` bằng MySQL Workbench hoặc command line:

```bash
mysql -u root -p < database/schema.sql
```

Schema khởi tạo sẵn hai game mode cùng danh mục hoa quả, nhóm hoa quả, nhãn dinh dưỡng và giỏ tương ứng. Tài khoản mẫu sẽ được tạo bởi module xác thực để mật khẩu luôn được lưu dưới dạng hash.

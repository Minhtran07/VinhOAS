# Contributing Guide

## Quy ước commit (Conventional Commits)

Format: `<type>(<scope>): <subject>`

Ví dụ:
```
feat(server): add auto-bid engine with priority queue
fix(client): handle null endTime in countdown
refactor(shared): extract validation to common helper
test(shared): add concurrent bidding test case
docs(readme): update setup instructions for MySQL 8
chore(ci): bump GitHub Actions setup-java to v4
```

Type được phép: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`, `perf`.

## Quy trình làm việc

1. Tạo branch theo phase / chức năng: `git checkout -b feat/p7-auto-bid`
2. Commit thường xuyên (≥ 1 commit/ngày làm việc)
3. Tạo PR vào `main`, chờ CI xanh
4. Self-review trước khi request review

## Phân công công việc theo Phase

| Phase | Phụ trách | Trạng thái |
|---|---|---|
| P0  Lớp cơ sở (entity, exception)         | … | done |
| P1  Schema DB + DAO                        | … | done |
| P2  Network protocol JSON-over-Socket      | … | done |
| P3  Observer + realtime push               | … | done |
| P4  Concurrency (SELECT FOR UPDATE)        | … | done |
| P5  Lifecycle scheduler                    | … | done |
| P6  UI JavaFX hoàn chỉnh                   | … | done |
| P7  Auto-bidding                           | … | done |
| P8  Line chart realtime                    | … | done |
| P9  Unit test JUnit 5                      | … | done |
| P10 CI/CD GitHub Actions                   | … | done |
| P11 Hardening (BCrypt, logging, config)    | … | done |
| P12 Documentation                          | … | done |

(Điền tên thành viên trước khi nộp.)

## Quy tắc về AI hỗ trợ

Theo đề bài: AI/Google/GitHub được phép dùng, nhưng **mỗi thành viên phải
hiểu và giải thích được mọi dòng code mình commit**. Trước khi merge PR,
tự đảm bảo:
- Đọc và hiểu logic
- Có thể trả lời "tại sao chọn cách này" cho mọi function

## Code style

- Google Java Style Guide
- Indent 4 space cho Java, 2 space cho FXML/XML
- Tên class PascalCase, method/field camelCase
- Comment tiếng Việt cho phần nghiệp vụ; tên symbol bằng tiếng Anh

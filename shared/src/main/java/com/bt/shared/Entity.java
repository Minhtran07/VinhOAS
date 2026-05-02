package com.bt.shared;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lớp cơ sở (Abstraction) cho mọi đối tượng nghiệp vụ trong hệ thống đấu giá.
 *
 * Đặc điểm thiết kế:
 *  - id kiểu {@link Long} (nullable): khớp với MySQL AUTO_INCREMENT.
 *    Khi chưa lưu DB, id = null. DAO sẽ set id sau insert.
 *  - createdAt: thời điểm tạo, ghi nhận tự động khi khởi tạo object trong JVM
 *    (DAO có thể override khi load từ DB để giữ đúng giá trị gốc).
 *  - implements {@link Serializable} để có thể truyền qua Socket / lưu file
 *    nếu sau này hệ thống dùng ObjectOutputStream. Nếu chuyển sang JSON
 *    (Gson/Jackson) cũng không bị cản trở.
 *  - equals/hashCode dựa trên (lớp cụ thể + id):
 *      * Hai entity được coi là bằng nhau nếu cùng kiểu thực và cùng id != null.
 *      * Hai entity chưa có id (id == null) chỉ bằng chính nó (reference equality)
 *        — đúng nguyên tắc của JPA/Hibernate, tránh bug khi cho vào HashSet
 *        trước khi persist.
 */
public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID do DB cấp, null khi entity chưa được persist. */
    private Long id;

    /** Thời điểm khởi tạo (immutable sau khi set). */
    private LocalDateTime createdAt;

    /** Constructor mặc định cho serialization / DAO mapping. */
    protected Entity() {
        this.createdAt = LocalDateTime.now();
    }

    /** Constructor khi load entity đã có sẵn id (từ DB / từ network). */
    protected Entity(Long id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    /**
     * Set id một lần duy nhất, thường là sau khi DAO insert vào DB và lấy
     * generated key. Không cho phép thay đổi id đã có để tránh lẫn entity.
     */
    public void setId(Long id) {
        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException(
                    "Entity id đã được set là " + this.id + ", không thể đổi sang " + id);
        }
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Mỗi class con phải tự định nghĩa cách hiển thị thông tin của mình
     * (Polymorphism). Dùng cho debug / log; UI nên dùng toString() hoặc
     * format riêng của controller.
     */
    public abstract void displayInfo();

    /**
     * Hai entity bằng nhau khi cùng kiểu thực và cùng id (id != null).
     * Dùng getClass() thay vì instanceof để tôn trọng phân cấp:
     * một Bidder và một Seller cùng id vẫn được coi là khác nhau.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity other = (Entity) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Hash cố định theo lớp khi id == null để không thay đổi khi entity
        // được persist (giữ contract của HashSet/HashMap).
        return Objects.hash(getClass().getName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + "}";
    }
}

package com.bt.shared;

/**
 * Người tham gia đấu giá (mua).
 *
 * Đặc thù:
 *  - {@code accountBalance}: số dư khả dụng để đặt giá. Khi đặt bid thắng,
 *    số tiền sẽ được trừ ở tầng service (không trừ trực tiếp ở đây).
 *  - balance không được âm.
 */
public class Bidder extends User {

    private static final long serialVersionUID = 1L;

    private double accountBalance;

    /** Constructor rỗng cho serialization / DAO. */
    public Bidder() {
        super();
    }

    public Bidder(String username, String email, String password, double startingBalance) {
        super(username, email, password);
        setAccountBalance(startingBalance);
    }

    public double getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(double accountBalance) {
        if (Double.isNaN(accountBalance) || accountBalance < 0) {
            throw new IllegalArgumentException(
                    "Số dư tài khoản phải >= 0, nhận: " + accountBalance);
        }
        this.accountBalance = accountBalance;
    }

    /** Set không validate — chỉ dùng khi load từ DB. Clamp âm về 0. */
    public void setAccountBalanceRaw(double accountBalance) {
        if (Double.isNaN(accountBalance) || accountBalance < 0) accountBalance = 0;
        this.accountBalance = accountBalance;
    }

    /** Tăng số dư (nạp tiền). */
    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải > 0");
        }
        this.accountBalance += amount;
    }

    /** Trừ số dư (khi thanh toán). Throw nếu không đủ. */
    public void withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải > 0");
        }
        if (amount > accountBalance) {
            throw new IllegalStateException(
                    "Số dư không đủ: cần " + amount + ", còn " + accountBalance);
        }
        this.accountBalance -= amount;
    }

    @Override
    public UserRole getRole() {
        return UserRole.BIDDER;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ Balance: $" + accountBalance);
    }
}

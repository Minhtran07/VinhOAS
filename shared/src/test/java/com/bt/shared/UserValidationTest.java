package com.bt.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserValidationTest {

    @Test
    void usernameTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bidder("ab", "x@y.com", "pw", 0));
    }

    @Test
    void invalidEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bidder("alice", "not-an-email", "pw", 0));
    }

    @Test
    void emptyPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bidder("alice", "a@b.com", "", 0));
    }

    @Test
    void negativeBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bidder("alice", "a@b.com", "pw", -1));
    }

    @Test
    void ratingOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new Seller("alice", "a@b.com", "pw", 6.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Seller("alice", "a@b.com", "pw", -0.1));
    }

    @Test
    void accessLevelOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new Admin("alice", "a@b.com", "pw", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Admin("alice", "a@b.com", "pw", 6));
    }

    @Test
    void rolesAreCorrect() {
        Bidder b = new Bidder("alice", "a@b.com", "pw", 100);
        Seller s = new Seller("alice", "a@b.com", "pw", 4.0);
        Admin a = new Admin("alice", "a@b.com", "pw", 3);
        assertEquals(UserRole.BIDDER, b.getRole());
        assertEquals(UserRole.SELLER, s.getRole());
        assertEquals(UserRole.ADMIN, a.getRole());
    }

    @Test
    void emailNormalizedToLowercase() {
        Bidder b = new Bidder("alice", "ALICE@Example.COM", "pw", 0);
        assertEquals("alice@example.com", b.getEmail());
    }
}

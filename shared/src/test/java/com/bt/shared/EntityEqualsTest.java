package com.bt.shared;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityEqualsTest {

    @Test
    void differentTypeNotEqual() {
        Bidder b = new Bidder("alice", "a@b.com", "pw", 0);
        Seller s = new Seller("alice", "a@b.com", "pw", 4.0);
        b.setId(10L);
        s.setId(10L);
        assertNotEquals(b, s);
    }

    @Test
    void sameIdSameTypeEqual() {
        Bidder b1 = new Bidder("alice", "a@b.com", "pw", 0);
        Bidder b2 = new Bidder("bob", "b@b.com", "pw", 0);
        b1.setId(10L);
        b2.setId(10L);
        assertEquals(b1, b2);
    }

    @Test
    void hashSetDeduplicatesById() {
        Bidder b1 = new Bidder("alice", "a@b.com", "pw", 0);
        Bidder b2 = new Bidder("bob", "b@b.com", "pw", 0);
        b1.setId(10L);
        b2.setId(10L);
        Set<Bidder> set = new HashSet<>();
        set.add(b1);
        set.add(b2);
        assertEquals(1, set.size());
    }

    @Test
    void cannotChangeIdOnceSet() {
        Bidder b = new Bidder("alice", "a@b.com", "pw", 0);
        b.setId(10L);
        assertThrows(IllegalStateException.class, () -> b.setId(20L));
    }

    @Test
    void canSetIdAfterEmpty() {
        Bidder b = new Bidder("alice", "a@b.com", "pw", 0);
        assertNull(b.getId());
        b.setId(10L);
        assertEquals(10L, b.getId());
    }
}

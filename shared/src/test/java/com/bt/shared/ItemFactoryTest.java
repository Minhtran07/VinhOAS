package com.bt.shared;

import com.bt.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemFactoryTest {

    @Test
    void createElectronics() throws Exception {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_BRAND, "Apple");
        spec.put(ItemFactory.KEY_WARRANTY_MONTHS, 12);
        Item it = ItemFactory.create(ItemCategory.ELECTRONICS,
                "iPhone 15", "Like new", 1200.0, spec);
        assertTrue(it instanceof Electronics);
        assertEquals("Apple", ((Electronics) it).getBrand());
        assertEquals(12, ((Electronics) it).getWarrantyMonths());
    }

    @Test
    void createArt() throws Exception {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_ARTIST, "Picasso");
        spec.put(ItemFactory.KEY_YEAR_CREATED, 1937);
        Item it = ItemFactory.create(ItemCategory.ART, "Guernica", "", 1_000_000, spec);
        assertTrue(it instanceof Art);
        assertEquals(1937, ((Art) it).getYearCreated());
    }

    @Test
    void createVehicle() throws Exception {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_MAKE, "Toyota");
        spec.put(ItemFactory.KEY_MODEL, "Camry");
        spec.put(ItemFactory.KEY_MILEAGE, 40000);
        Item it = ItemFactory.create(ItemCategory.VEHICLE, "Camry 2018", "", 18000, spec);
        assertTrue(it instanceof Vehicle);
        assertEquals(40000, ((Vehicle) it).getMileage());
    }

    @Test
    void missingRequiredField() {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_WARRANTY_MONTHS, 12);
        // Thiếu brand
        assertThrows(ValidationException.class,
                () -> ItemFactory.create(ItemCategory.ELECTRONICS,
                        "x", "y", 100, spec));
    }

    @Test
    void invalidIntField() {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_ARTIST, "Picasso");
        spec.put(ItemFactory.KEY_YEAR_CREATED, "abc");
        assertThrows(ValidationException.class,
                () -> ItemFactory.create(ItemCategory.ART, "x", "y", 100, spec));
    }

    @Test
    void overloadStringCategory() throws Exception {
        Map<String, Object> spec = new HashMap<>();
        spec.put(ItemFactory.KEY_BRAND, "Sony");
        spec.put(ItemFactory.KEY_WARRANTY_MONTHS, 24);
        Item it = ItemFactory.create("electronics", "TV", "4K", 800, spec);
        assertEquals(ItemCategory.ELECTRONICS, it.getCategory());
    }

    @Test
    void invalidStringCategory() {
        assertThrows(ValidationException.class,
                () -> ItemFactory.create("food", "Pizza", "", 10, new HashMap<>()));
    }
}

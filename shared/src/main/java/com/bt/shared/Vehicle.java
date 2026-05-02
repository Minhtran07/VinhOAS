package com.bt.shared;

/**
 * Phương tiện: ô tô, xe máy...
 * Có thông tin hãng (make), dòng (model), số km đã đi (mileage).
 */
public class Vehicle extends Item {

    private static final long serialVersionUID = 1L;

    private String make;
    private String model;
    private int mileage;

    public Vehicle() {
        super();
    }

    public Vehicle(String name, String description, double startingPrice,
                   String make, String model, int mileage) {
        super(name, description, startingPrice);
        setMake(make);
        setModel(model);
        setMileage(mileage);
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        if (make == null || make.trim().isEmpty()) {
            throw new IllegalArgumentException("Hãng (make) không được để trống");
        }
        this.make = make.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model không được để trống");
        }
        this.model = model.trim();
    }

    public int getMileage() {
        return mileage;
    }

    public void setMileage(int mileage) {
        if (mileage < 0) {
            throw new IllegalArgumentException("Mileage không được âm: " + mileage);
        }
        this.mileage = mileage;
    }

    /** Raw setter cho DAO. */
    public void setMakeRaw(String make) { this.make = make; }
    public void setModelRaw(String model) { this.model = model; }
    public void setMileageRaw(int mileage) { this.mileage = Math.max(0, mileage); }

    @Override
    public ItemCategory getCategory() {
        return ItemCategory.VEHICLE;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ " + make + " " + model + " | Mileage: " + mileage + " km");
    }
}

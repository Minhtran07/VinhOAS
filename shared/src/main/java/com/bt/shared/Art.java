package com.bt.shared;

import java.time.Year;

/**
 * Tác phẩm nghệ thuật: tranh, tượng, đồ cổ...
 * Có thông tin tác giả và năm sáng tác.
 */
public class Art extends Item {

    private static final long serialVersionUID = 1L;

    /** Năm tối thiểu chấp nhận: trước đó coi như dữ liệu không đáng tin. */
    private static final int MIN_YEAR = 1000;

    private String artist;
    private int yearCreated;

    public Art() {
        super();
    }

    public Art(String name, String description, double startingPrice,
               String artist, int yearCreated) {
        super(name, description, startingPrice);
        setArtist(artist);
        setYearCreated(yearCreated);
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên tác giả không được để trống");
        }
        this.artist = artist.trim();
    }

    public int getYearCreated() {
        return yearCreated;
    }

    public void setYearCreated(int yearCreated) {
        int currentYear = Year.now().getValue();
        if (yearCreated < MIN_YEAR || yearCreated > currentYear) {
            throw new IllegalArgumentException(
                    "Năm sáng tác phải trong [" + MIN_YEAR + ", " + currentYear
                            + "], nhận: " + yearCreated);
        }
        this.yearCreated = yearCreated;
    }

    /** Raw setter cho DAO. */
    public void setArtistRaw(String artist) { this.artist = artist; }
    public void setYearCreatedRaw(int year) { this.yearCreated = year; }

    @Override
    public ItemCategory getCategory() {
        return ItemCategory.ART;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ Artist: " + artist + " | Year: " + yearCreated);
    }
}

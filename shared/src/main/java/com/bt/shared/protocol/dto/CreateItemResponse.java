package com.bt.shared.protocol.dto;

public class CreateItemResponse {

    private ItemDto item;

    public CreateItemResponse() { /* Gson */ }

    public CreateItemResponse(ItemDto item) { this.item = item; }

    public ItemDto getItem() { return item; }
    public void setItem(ItemDto item) { this.item = item; }
}

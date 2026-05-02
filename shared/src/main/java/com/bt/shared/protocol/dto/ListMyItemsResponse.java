package com.bt.shared.protocol.dto;

import java.util.List;

public class ListMyItemsResponse {

    private List<ItemDto> items;

    public ListMyItemsResponse() { /* Gson */ }

    public ListMyItemsResponse(List<ItemDto> items) { this.items = items; }

    public List<ItemDto> getItems() { return items; }
    public void setItems(List<ItemDto> items) { this.items = items; }
}

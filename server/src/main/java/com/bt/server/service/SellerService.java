package com.bt.server.service;

import com.bt.server.dao.AuctionDAO;
import com.bt.server.dao.ItemDAO;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.AuctionEventBus;
import com.bt.server.event.ConnectionRegistry;
import com.bt.shared.Art;
import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.Electronics;
import com.bt.shared.Item;
import com.bt.shared.ItemFactory;
import com.bt.shared.User;
import com.bt.shared.UserRole;
import com.bt.shared.Vehicle;
import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionCreatedEvent;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.CreateAuctionRequest;
import com.bt.shared.protocol.dto.CreateItemRequest;
import com.bt.shared.protocol.dto.ItemDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Logic dành cho Seller: tạo Item, tạo Auction từ Item.
 *
 * Quy tắc bảo mật:
 *  - Mọi method nhận sellerId từ session (đã được RequestRouter verify) —
 *    không tin field từ client để tránh forge.
 *  - Verify role là SELLER trước khi cho thao tác.
 *  - Verify ownership: seller chỉ được tạo phiên cho item của mình.
 *
 * Tạo phiên thành công sẽ broadcast {@link AuctionCreatedEvent} qua
 * {@link ConnectionRegistry} để Dashboard mọi client tự refresh.
 */
public class SellerService {

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    private final ItemDAO itemDAO;
    private final AuctionDAO auctionDAO;
    private final UserDAO userDAO;
    private final AuctionService auctionService;
    private final ConnectionRegistry registry;
    private final AuctionEventBus eventBus;

    public SellerService(ItemDAO itemDAO, AuctionDAO auctionDAO, UserDAO userDAO,
                         AuctionService auctionService,
                         ConnectionRegistry registry,
                         AuctionEventBus eventBus) {
        this.itemDAO = itemDAO;
        this.auctionDAO = auctionDAO;
        this.userDAO = userDAO;
        this.auctionService = auctionService;
        this.registry = registry;
        this.eventBus = eventBus;
    }

    /** Tạo item mới của seller. */
    public ItemDto createItem(long sellerId, CreateItemRequest req) throws ValidationException {
        verifySellerRole(sellerId);
        if (req == null || req.getCategory() == null) {
            throw new ValidationException("Thiếu category");
        }

        Item item;
        try {
            item = ItemFactory.create(req.getCategory(), req.getName(),
                    req.getDescription(), req.getStartingPrice(), req.getSpec());
        } catch (ValidationException ex) {
            throw ex;
        }
        item.setSellerId(sellerId);

        Optional<Item> saved = itemDAO.insert(item);
        if (saved.isEmpty()) {
            throw new ValidationException("Lỗi khi lưu item vào DB");
        }
        log.info("Seller {} created item id={} ({})",
                sellerId, saved.get().getId(), saved.get().getName());
        return toDto(saved.get(), userDAO.findById(sellerId)
                .map(User::getUsername).orElse(null));
    }

    /** List item của 1 seller. */
    public List<ItemDto> listMyItems(long sellerId) {
        String sellerName = userDAO.findById(sellerId)
                .map(User::getUsername).orElse(null);
        List<Item> items = itemDAO.findBySeller(sellerId);
        List<ItemDto> dtos = new ArrayList<>(items.size());
        for (Item it : items) dtos.add(toDto(it, sellerName));
        return dtos;
    }

    /**
     * Tạo phiên đấu giá từ item. Quy tắc:
     *  - Item phải thuộc về sellerId
     *  - Item chưa có phiên active (OPEN/RUNNING/FINISHED-chưa-PAID)
     *  - endTime > startTime, endTime > now
     *  - Nếu startTime <= now → status RUNNING ngay; ngược lại OPEN
     */
    public AuctionDto createAuction(long sellerId, CreateAuctionRequest req)
            throws ValidationException, AuctionStateException {
        verifySellerRole(sellerId);
        if (req == null) throw new ValidationException("Request rỗng");
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new ValidationException("Thiếu startTime hoặc endTime");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new ValidationException("endTime phải sau startTime");
        }
        if (!req.getEndTime().isAfter(now)) {
            throw new ValidationException("endTime phải ở tương lai");
        }
        Duration d = Duration.between(req.getStartTime(), req.getEndTime());
        if (d.toMinutes() < 1) {
            throw new ValidationException("Phiên đấu giá phải kéo dài ít nhất 1 phút");
        }

        // Verify item ownership
        Optional<Item> opt = itemDAO.findById(req.getItemId());
        if (opt.isEmpty()) throw new ValidationException("Item không tồn tại");
        Item item = opt.get();
        if (item.getSellerId() == null || item.getSellerId() != sellerId) {
            throw new ValidationException("Bạn không phải chủ sở hữu item này");
        }
        if (itemDAO.hasActiveAuction(item.getId())) {
            throw new ValidationException(
                    "Item này đã có phiên đang chạy hoặc đã được bán cho người thắng cuộc. "
                            + "Hãy tạo item mới nếu muốn đăng bán lại.");
        }

        // Build và persist
        User sellerUser = userDAO.findById(sellerId)
                .orElseThrow(() -> new ValidationException("Seller không tồn tại"));
        com.bt.shared.Seller seller = (com.bt.shared.Seller) sellerUser;

        Auction auction = new Auction(item, seller, req.getStartTime(), req.getEndTime());
        // Nếu đã đến giờ → start luôn
        boolean startNow = !req.getStartTime().isAfter(now);
        if (startNow) {
            auction.start();
        }
        Optional<Auction> savedOpt = auctionDAO.insert(auction);
        if (savedOpt.isEmpty()) {
            throw new ValidationException("Lỗi khi lưu auction vào DB");
        }
        Auction saved = savedOpt.get();
        if (startNow) {
            // Insert đặt status OPEN mặc định trong DB; cần update sang RUNNING
            auctionDAO.updateStatus(saved.getId(), AuctionStatus.RUNNING, null);
        }
        log.info("Seller {} created auction id={} for item {} (startNow={})",
                sellerId, saved.getId(), item.getId(), startNow);

        // Build DTO bằng AuctionService để giữ format thống nhất với listAuctions
        AuctionDto dto = auctionService.getAuction(saved.getId())
                .orElseThrow(() -> new ValidationException("Lỗi load auction sau khi tạo"));

        // BROADCAST cho mọi client → Dashboard tự refresh
        registry.broadcast(MessageType.AUCTION_CREATED_EVENT,
                new AuctionCreatedEvent(dto));
        return dto;
    }

    // ---------- Helpers ----------

    private void verifySellerRole(long userId) throws ValidationException {
        Optional<User> opt = userDAO.findById(userId);
        if (opt.isEmpty()) throw new ValidationException("User không tồn tại");
        if (opt.get().getRole() != UserRole.SELLER) {
            throw new ValidationException("Chỉ Seller được thực hiện thao tác này");
        }
    }

    private ItemDto toDto(Item item, String sellerUsername) {
        ItemDto d = new ItemDto();
        d.setItemId(item.getId() == null ? 0 : item.getId());
        d.setName(item.getName());
        d.setDescription(item.getDescription());
        d.setStartingPrice(item.getStartingPrice());
        d.setCategory(item.getCategory());
        d.setSellerId(item.getSellerId() == null ? 0 : item.getSellerId());
        d.setSellerUsername(sellerUsername);

        if (item instanceof Electronics) {
            Electronics e = (Electronics) item;
            d.setBrand(e.getBrand());
            d.setWarrantyMonths(e.getWarrantyMonths());
        } else if (item instanceof Art) {
            Art a = (Art) item;
            d.setArtist(a.getArtist());
            d.setYearCreated(a.getYearCreated());
        } else if (item instanceof Vehicle) {
            Vehicle v = (Vehicle) item;
            d.setMake(v.getMake());
            d.setModel(v.getModel());
            d.setMileage(v.getMileage());
        }
        if (item.getId() != null) {
            d.setHasActiveAuction(itemDAO.hasActiveAuction(item.getId()));
        }
        return d;
    }
}

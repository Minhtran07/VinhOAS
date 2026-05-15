package com.bt.server.net;

import com.bt.server.event.AuctionEventBus;
import com.bt.server.event.ConnectionObserver;
import com.bt.server.event.ConnectionRegistry;
import com.bt.server.service.AdminService;
import com.bt.server.service.AuctionService;
import com.bt.server.service.AuthService;
import com.bt.server.service.PaymentService;
import com.bt.server.service.RatingService;
import com.bt.server.service.SellerService;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.UserRole;
import com.bt.shared.exception.AuctionException;
import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.AuthenticationException;
import com.bt.shared.exception.InvalidBidException;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.ErrorCode;
import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.BidDto;
import com.bt.shared.protocol.dto.CancelAutoBidRequest;
import com.bt.shared.protocol.dto.CancelAutoBidResponse;
import com.bt.shared.protocol.dto.CreateAuctionRequest;
import com.bt.shared.protocol.dto.CreateAuctionResponse;
import com.bt.shared.protocol.dto.CreateItemRequest;
import com.bt.shared.protocol.dto.CreateItemResponse;
import com.bt.shared.protocol.dto.ErrorResponse;
import com.bt.shared.protocol.dto.GetAuctionRequest;
import com.bt.shared.protocol.dto.GetBidHistoryRequest;
import com.bt.shared.protocol.dto.GetBidHistoryResponse;
import com.bt.shared.protocol.dto.ItemDto;
import com.bt.shared.protocol.dto.ListAuctionsRequest;
import com.bt.shared.protocol.dto.ListAuctionsResponse;
import com.bt.shared.protocol.dto.ListMyItemsResponse;
import com.bt.shared.protocol.dto.ListUsersRequest;
import com.bt.shared.protocol.dto.ListUsersResponse;
import com.bt.shared.protocol.dto.LoginRequest;
import com.bt.shared.protocol.dto.LoginResponse;
import com.bt.shared.protocol.dto.PayAuctionRequest;
import com.bt.shared.protocol.dto.PayAuctionResponse;
import com.bt.shared.protocol.dto.PlaceBidRequest;
import com.bt.shared.protocol.dto.PlaceBidResponse;
import com.bt.shared.protocol.dto.RateSellerRequest;
import com.bt.shared.protocol.dto.RateSellerResponse;
import com.bt.shared.protocol.dto.RegisterAutoBidRequest;
import com.bt.shared.protocol.dto.RegisterAutoBidResponse;
import com.bt.shared.protocol.dto.RegisterRequest;
import com.bt.shared.protocol.dto.RegisterResponse;
import com.bt.shared.protocol.dto.SetUserActiveRequest;
import com.bt.shared.protocol.dto.SetUserActiveResponse;
import com.bt.shared.protocol.dto.SubscribeRequest;
import com.bt.shared.protocol.dto.SubscriptionResponse;
import com.bt.shared.protocol.dto.UserSummaryDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Dispatch message tới service tương ứng.
 *
 * Mỗi connection có 1 router riêng. Router cũng đảm nhiệm register/unregister
 * connection vào {@link ConnectionRegistry} cho broadcast toàn cục.
 */
public class RequestRouter {

    private static final Logger log = LoggerFactory.getLogger(RequestRouter.class);

    private final ClientConnection conn;
    private final AuthService authService;
    private final AuctionService auctionService;
    private final SellerService sellerService;
    private final PaymentService paymentService;
    private final RatingService ratingService;
    private final AdminService adminService;
    private final AuctionEventBus eventBus;
    private final ConnectionRegistry registry;
    private final ConnectionObserver observer;

    public RequestRouter(ClientConnection conn, AuthService authService,
                         AuctionService auctionService, SellerService sellerService,
                         PaymentService paymentService, RatingService ratingService,
                         AdminService adminService,
                         AuctionEventBus eventBus, ConnectionRegistry registry) {
        this.conn = conn;
        this.authService = authService;
        this.auctionService = auctionService;
        this.sellerService = sellerService;
        this.paymentService = paymentService;
        this.ratingService = ratingService;
        this.adminService = adminService;
        this.eventBus = eventBus;
        this.registry = registry;
        this.observer = new ConnectionObserver(conn);
    }

    public void serve() {
        // Đăng ký connection để nhận broadcast (vd: AUCTION_CREATED_EVENT)
        registry.register(conn);
        try {
            Message msg;
            while ((msg = conn.readMessage()) != null) {
                handle(msg);
            }
        } catch (IOException e) {
            System.err.println("[" + conn + "] IO error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[" + conn + "] Unexpected: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup: gỡ subscriptions per-auction + global registry + đóng socket
            eventBus.unsubscribeAll(observer);
            registry.unregister(conn);
            conn.close();
        }
    }

    private void handle(Message msg) throws IOException {
        try {
            switch (msg.getType()) {
                case LOGIN_REQUEST:                  handleLogin(msg); break;
                case REGISTER_REQUEST:               handleRegister(msg); break;
                case LIST_AUCTIONS_REQUEST:          handleListAuctions(msg); break;
                case GET_AUCTION_REQUEST:            handleGetAuction(msg); break;
                case GET_BID_HISTORY_REQUEST:        handleGetBidHistory(msg); break;
                case PLACE_BID_REQUEST:              handlePlaceBid(msg); break;
                case REGISTER_AUTOBID_REQUEST:       handleRegisterAutoBid(msg); break;
                case CANCEL_AUTOBID_REQUEST:         handleCancelAutoBid(msg); break;
                case SUBSCRIBE_AUCTION_REQUEST:      handleSubscribe(msg); break;
                case UNSUBSCRIBE_AUCTION_REQUEST:    handleUnsubscribe(msg); break;
                case CREATE_ITEM_REQUEST:            handleCreateItem(msg); break;
                case LIST_MY_ITEMS_REQUEST:          handleListMyItems(msg); break;
                case CREATE_AUCTION_REQUEST:         handleCreateAuction(msg); break;
                case PAY_AUCTION_REQUEST:            handlePayAuction(msg); break;
                case RATE_SELLER_REQUEST:            handleRateSeller(msg); break;
                case LIST_USERS_REQUEST:             handleListUsers(msg); break;
                case SET_USER_ACTIVE_REQUEST:        handleSetUserActive(msg); break;
                case PING_REQUEST:                   handlePing(msg); break;
                default:
                    sendError(msg, ErrorCode.UNSUPPORTED_TYPE,
                            "Server chưa hỗ trợ type: " + msg.getType());
            }
        } catch (AuthenticationException e) {
            sendError(msg, ErrorCode.AUTH_FAILED, e.getMessage());
        } catch (ValidationException e) {
            sendError(msg, ErrorCode.VALIDATION_FAILED, e.getMessage());
        } catch (InvalidBidException e) {
            sendError(msg, ErrorCode.INVALID_BID, e.getMessage());
        } catch (AuctionStateException e) {
            sendError(msg, ErrorCode.AUCTION_STATE_INVALID, e.getMessage());
        } catch (AuctionException e) {
            sendError(msg, ErrorCode.INTERNAL_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendError(msg, ErrorCode.INTERNAL_ERROR, "Lỗi server: " + e.getMessage());
        }
    }

    // ---------- Auth ----------

    private void handleLogin(Message msg) throws IOException, AuthenticationException {
        LoginRequest req = MessageCodec.payloadAs(msg, LoginRequest.class);
        LoginResponse resp = authService.login(req.getUsername(), req.getPassword());
        conn.bindUser(resp.getUserId(), resp.getUsername(), resp.getRole().name());
        send(MessageType.LOGIN_RESPONSE, msg.getRequestId(), resp);
    }

    private void handleRegister(Message msg) throws IOException, ValidationException {
        RegisterRequest req = MessageCodec.payloadAs(msg, RegisterRequest.class);
        RegisterResponse resp = authService.register(req);
        send(MessageType.REGISTER_RESPONSE, msg.getRequestId(), resp);
    }

    // ---------- Auction read ----------

    private void handleListAuctions(Message msg) throws IOException {
        ListAuctionsRequest req = msg.getPayload() == null
                ? new ListAuctionsRequest(null)
                : MessageCodec.payloadAs(msg, ListAuctionsRequest.class);
        AuctionStatus status = (req == null) ? null : req.getStatusFilter();
        List<AuctionDto> list = auctionService.listAuctions(status);
        send(MessageType.LIST_AUCTIONS_RESPONSE, msg.getRequestId(),
                new ListAuctionsResponse(list));
    }

    private void handleGetAuction(Message msg) throws IOException, ValidationException {
        GetAuctionRequest req = MessageCodec.payloadAs(msg, GetAuctionRequest.class);
        Optional<AuctionDto> opt = auctionService.getAuction(req.getAuctionId());
        if (opt.isEmpty()) {
            throw new ValidationException("Auction không tồn tại: " + req.getAuctionId());
        }
        send(MessageType.GET_AUCTION_RESPONSE, msg.getRequestId(), opt.get());
    }

    private void handleGetBidHistory(Message msg) throws IOException {
        GetBidHistoryRequest req = MessageCodec.payloadAs(msg, GetBidHistoryRequest.class);
        List<BidDto> bids = auctionService.getBidHistory(req.getAuctionId());
        send(MessageType.GET_BID_HISTORY_RESPONSE, msg.getRequestId(),
                new GetBidHistoryResponse(req.getAuctionId(), bids));
    }

    // ---------- Bidding ----------

    private void handlePlaceBid(Message msg)
            throws IOException, InvalidBidException, AuctionStateException, ValidationException {
        if (!conn.isAuthenticated()) {
            log.warn("PlaceBid bị từ chối: chưa đăng nhập (conn={})", conn);
            sendError(msg, ErrorCode.FORBIDDEN, "Chưa đăng nhập");
            return;
        }
        // Server-side enforce: chỉ BIDDER được đặt giá
        if (!"BIDDER".equals(conn.getRole())) {
            log.warn("PlaceBid bị từ chối: role={} không phải BIDDER (conn={})",
                    conn.getRole(), conn);
            sendError(msg, ErrorCode.FORBIDDEN,
                    "Chỉ Bidder được đặt giá (role hiện tại: " + conn.getRole() + ")");
            return;
        }
        PlaceBidRequest req = MessageCodec.payloadAs(msg, PlaceBidRequest.class);
        log.info("PlaceBid request từ {}: auction={}, bidderId={}, amount={}",
                conn.getUsername(), req.getAuctionId(), req.getBidderId(), req.getAmount());
        if (req.getBidderId() != conn.getUserId()) {
            log.warn("PlaceBid bị từ chối: bidderId={} không khớp session userId={}",
                    req.getBidderId(), conn.getUserId());
            sendError(msg, ErrorCode.FORBIDDEN,
                    "bidderId không khớp session (request=" + req.getBidderId()
                            + ", session=" + conn.getUserId() + ")");
            return;
        }
        PlaceBidResponse resp = auctionService.placeBid(
                req.getAuctionId(), req.getBidderId(), req.getAmount());
        log.info("PlaceBid OK: bidId={} newPrice={}", resp.getBidId(), resp.getNewCurrentPrice());
        send(MessageType.PLACE_BID_RESPONSE, msg.getRequestId(), resp);
    }

    // ---------- Auto-bid ----------

    private void handleRegisterAutoBid(Message msg)
            throws IOException, InvalidBidException, AuctionStateException, ValidationException {
        if (!conn.isAuthenticated()) {
            sendError(msg, ErrorCode.FORBIDDEN, "Chưa đăng nhập");
            return;
        }
        if (!"BIDDER".equals(conn.getRole())) {
            sendError(msg, ErrorCode.FORBIDDEN,
                    "Chỉ Bidder được đăng ký auto-bid (role: " + conn.getRole() + ")");
            return;
        }
        RegisterAutoBidRequest req = MessageCodec.payloadAs(msg, RegisterAutoBidRequest.class);
        log.info("RegisterAutoBid từ {}: auction={} maxBid={} increment={}",
                conn.getUsername(), req.getAuctionId(), req.getMaxBid(), req.getIncrement());
        com.bt.server.autobid.AutoBidConfig cfg = auctionService.registerAutoBid(
                req.getAuctionId(), conn.getUserId(), conn.getUsername(),
                req.getMaxBid(), req.getIncrement());
        RegisterAutoBidResponse resp = new RegisterAutoBidResponse();
        resp.setAuctionId(req.getAuctionId());
        resp.setBidderId(conn.getUserId());
        resp.setMaxBid(cfg.getMaxBid());
        resp.setIncrement(cfg.getIncrement());
        resp.setActive(true);
        send(MessageType.REGISTER_AUTOBID_RESPONSE, msg.getRequestId(), resp);
    }

    private void handleCancelAutoBid(Message msg) throws IOException {
        if (!conn.isAuthenticated()) {
            sendError(msg, ErrorCode.FORBIDDEN, "Chưa đăng nhập");
            return;
        }
        CancelAutoBidRequest req = MessageCodec.payloadAs(msg, CancelAutoBidRequest.class);
        boolean ok = auctionService.cancelAutoBid(req.getAuctionId(), conn.getUserId());
        send(MessageType.CANCEL_AUTOBID_RESPONSE, msg.getRequestId(),
                new CancelAutoBidResponse(req.getAuctionId(), ok));
    }

    // ---------- Subscriptions ----------

    private void handleSubscribe(Message msg) throws IOException {
        SubscribeRequest req = MessageCodec.payloadAs(msg, SubscribeRequest.class);
        eventBus.subscribe(req.getAuctionId(), observer);
        conn.getSubscribedAuctions().add(req.getAuctionId());
        send(MessageType.SUBSCRIPTION_RESPONSE, msg.getRequestId(),
                new SubscriptionResponse(req.getAuctionId(), true,
                        eventBus.subscriberCount(req.getAuctionId())));
    }

    private void handleUnsubscribe(Message msg) throws IOException {
        SubscribeRequest req = MessageCodec.payloadAs(msg, SubscribeRequest.class);
        eventBus.unsubscribe(req.getAuctionId(), observer);
        conn.getSubscribedAuctions().remove(req.getAuctionId());
        send(MessageType.SUBSCRIPTION_RESPONSE, msg.getRequestId(),
                new SubscriptionResponse(req.getAuctionId(), false,
                        eventBus.subscriberCount(req.getAuctionId())));
    }

    // ---------- Seller ----------

    private void handleCreateItem(Message msg) throws IOException, ValidationException {
        requireRole(msg, UserRole.SELLER);
        CreateItemRequest req = MessageCodec.payloadAs(msg, CreateItemRequest.class);
        ItemDto dto = sellerService.createItem(conn.getUserId(), req);
        send(MessageType.CREATE_ITEM_RESPONSE, msg.getRequestId(),
                new CreateItemResponse(dto));
    }

    private void handleListMyItems(Message msg) throws IOException, ValidationException {
        requireRole(msg, UserRole.SELLER);
        List<ItemDto> items = sellerService.listMyItems(conn.getUserId());
        send(MessageType.LIST_MY_ITEMS_RESPONSE, msg.getRequestId(),
                new ListMyItemsResponse(items));
    }

    private void handleCreateAuction(Message msg)
            throws IOException, ValidationException, AuctionStateException {
        requireRole(msg, UserRole.SELLER);
        CreateAuctionRequest req = MessageCodec.payloadAs(msg, CreateAuctionRequest.class);
        AuctionDto dto = sellerService.createAuction(conn.getUserId(), req);
        send(MessageType.CREATE_AUCTION_RESPONSE, msg.getRequestId(),
                new CreateAuctionResponse(dto));
    }

    // ---------- Payment ----------

    private void handlePayAuction(Message msg)
            throws IOException, ValidationException, AuctionStateException {
        if (!conn.isAuthenticated()) {
            sendError(msg, ErrorCode.FORBIDDEN, "Chưa đăng nhập");
            return;
        }
        if (!"BIDDER".equals(conn.getRole())) {
            sendError(msg, ErrorCode.FORBIDDEN,
                    "Chỉ Bidder được thanh toán (role: " + conn.getRole() + ")");
            return;
        }
        PayAuctionRequest req = MessageCodec.payloadAs(msg, PayAuctionRequest.class);
        log.info("Pay request: auction={} bidder={}", req.getAuctionId(), conn.getUserId());
        PayAuctionResponse resp = paymentService.pay(req.getAuctionId(), conn.getUserId());
        send(MessageType.PAY_AUCTION_RESPONSE, msg.getRequestId(), resp);
    }

    // ---------- Rating ----------

    private void handleRateSeller(Message msg)
            throws IOException, ValidationException {
        if (!conn.isAuthenticated()) {
            sendError(msg, ErrorCode.FORBIDDEN, "Chưa đăng nhập");
            return;
        }
        if (!"BIDDER".equals(conn.getRole())) {
            sendError(msg, ErrorCode.FORBIDDEN,
                    "Chỉ Bidder được đánh giá seller");
            return;
        }
        RateSellerRequest req = MessageCodec.payloadAs(msg, RateSellerRequest.class);
        RateSellerResponse resp = ratingService.rate(req.getAuctionId(),
                conn.getUserId(), req.getStars(), req.getComment());
        send(MessageType.RATE_SELLER_RESPONSE, msg.getRequestId(), resp);
    }

    // ---------- Admin ----------

    private void handleListUsers(Message msg) throws IOException, ValidationException {
        requireRole(msg, UserRole.ADMIN);
        ListUsersRequest req = msg.getPayload() == null
                ? new ListUsersRequest(null, null)
                : MessageCodec.payloadAs(msg, ListUsersRequest.class);
        List<UserSummaryDto> users = adminService.listUsers(
                req.getRoleFilter(), req.getActiveFilter());
        send(MessageType.LIST_USERS_RESPONSE, msg.getRequestId(),
                new ListUsersResponse(users));
    }

    private void handleSetUserActive(Message msg) throws IOException, ValidationException {
        requireRole(msg, UserRole.ADMIN);
        SetUserActiveRequest req = MessageCodec.payloadAs(msg, SetUserActiveRequest.class);
        SetUserActiveResponse resp = adminService.setUserActive(
                conn.getUserId(), req.getUserId(), req.isActive());
        send(MessageType.SET_USER_ACTIVE_RESPONSE, msg.getRequestId(), resp);
    }

    // ---------- Heartbeat ----------

    private void handlePing(Message msg) throws IOException {
        // Echo lại để client đo RTT + biết kết nối còn sống.
        // Payload có thể null hoặc timestamp client gửi — server không cần đọc.
        send(MessageType.PONG_RESPONSE, msg.getRequestId(),
                new long[] { System.currentTimeMillis() });
    }

    // ---------- Helpers ----------

    /** Throw ValidationException nếu chưa đăng nhập hoặc sai role. */
    private void requireRole(Message msg, UserRole expected) throws ValidationException {
        if (!conn.isAuthenticated()) {
            throw new ValidationException("Chưa đăng nhập");
        }
        if (!expected.name().equals(conn.getRole())) {
            throw new ValidationException("Yêu cầu role " + expected
                    + ", session đang là " + conn.getRole());
        }
    }

    private void send(MessageType type, String requestId, Object payload) throws IOException {
        conn.sendMessage(MessageCodec.build(type, requestId, payload));
    }

    private void sendError(Message origin, ErrorCode code, String message) throws IOException {
        Message err = MessageCodec.build(MessageType.ERROR_RESPONSE,
                origin == null ? "" : origin.getRequestId(),
                new ErrorResponse(code, message));
        conn.sendMessage(err);
    }
}

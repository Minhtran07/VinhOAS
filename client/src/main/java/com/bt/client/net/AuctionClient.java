package com.bt.client.net;

import com.bt.shared.Auction.AuctionStatus;
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
import com.bt.shared.protocol.dto.ListMyItemsRequest;
import com.bt.shared.protocol.dto.ListMyItemsResponse;
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
import com.bt.shared.protocol.dto.SubscribeRequest;
import com.bt.shared.protocol.dto.SubscriptionResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * High-level API mà UI controller gọi. Bao bọc {@link ServerConnection},
 * convert response chung thành DTO cụ thể, và biến error response thành
 * exception để UI có thể catch tự nhiên.
 */
public class AuctionClient {

    /** 15s — đủ thời gian cho BCrypt + DB query. Tăng nếu server chậm hơn. */
    private static final long DEFAULT_TIMEOUT_MS = 15_000;

    private final ServerConnection conn;

    public AuctionClient(ServerConnection conn) {
        this.conn = conn;
    }

    public AuctionClient() {
        this(ServerConnection.getInstance());
    }

    public LoginResponse login(String username, String password) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.LOGIN_REQUEST,
                new LoginRequest(username, password));
        return MessageCodec.payloadAs(resp, LoginResponse.class);
    }

    public RegisterResponse register(RegisterRequest req) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.REGISTER_REQUEST, req);
        return MessageCodec.payloadAs(resp, RegisterResponse.class);
    }

    public List<AuctionDto> listAuctions(AuctionStatus statusFilter) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.LIST_AUCTIONS_REQUEST,
                new ListAuctionsRequest(statusFilter));
        return MessageCodec.payloadAs(resp, ListAuctionsResponse.class).getAuctions();
    }

    public AuctionDto getAuction(long auctionId) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.GET_AUCTION_REQUEST,
                new GetAuctionRequest(auctionId));
        return MessageCodec.payloadAs(resp, AuctionDto.class);
    }

    public List<BidDto> getBidHistory(long auctionId) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.GET_BID_HISTORY_REQUEST,
                new GetBidHistoryRequest(auctionId));
        return MessageCodec.payloadAs(resp, GetBidHistoryResponse.class).getBids();
    }

    public PlaceBidResponse placeBid(long auctionId, long bidderId, double amount)
            throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.PLACE_BID_REQUEST,
                new PlaceBidRequest(auctionId, bidderId, amount));
        return MessageCodec.payloadAs(resp, PlaceBidResponse.class);
    }

    public RegisterAutoBidResponse registerAutoBid(long auctionId, double maxBid,
                                                    double increment)
            throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.REGISTER_AUTOBID_REQUEST,
                new RegisterAutoBidRequest(auctionId, maxBid, increment));
        return MessageCodec.payloadAs(resp, RegisterAutoBidResponse.class);
    }

    public CancelAutoBidResponse cancelAutoBid(long auctionId)
            throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.CANCEL_AUTOBID_REQUEST,
                new CancelAutoBidRequest(auctionId));
        return MessageCodec.payloadAs(resp, CancelAutoBidResponse.class);
    }

    public SubscriptionResponse subscribe(long auctionId) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.SUBSCRIBE_AUCTION_REQUEST,
                new SubscribeRequest(auctionId));
        return MessageCodec.payloadAs(resp, SubscriptionResponse.class);
    }

    public SubscriptionResponse unsubscribe(long auctionId) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.UNSUBSCRIBE_AUCTION_REQUEST,
                new SubscribeRequest(auctionId));
        return MessageCodec.payloadAs(resp, SubscriptionResponse.class);
    }

    // ---------- Seller ----------

    public ItemDto createItem(CreateItemRequest req) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.CREATE_ITEM_REQUEST, req);
        return MessageCodec.payloadAs(resp, CreateItemResponse.class).getItem();
    }

    public List<ItemDto> listMyItems() throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.LIST_MY_ITEMS_REQUEST,
                new ListMyItemsRequest());
        return MessageCodec.payloadAs(resp, ListMyItemsResponse.class).getItems();
    }

    public AuctionDto createAuction(CreateAuctionRequest req) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.CREATE_AUCTION_REQUEST, req);
        return MessageCodec.payloadAs(resp, CreateAuctionResponse.class).getAuction();
    }

    // ---------- Payment & Rating ----------

    public PayAuctionResponse payAuction(long auctionId) throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.PAY_AUCTION_REQUEST,
                new PayAuctionRequest(auctionId));
        return MessageCodec.payloadAs(resp, PayAuctionResponse.class);
    }

    public RateSellerResponse rateSeller(long auctionId, int stars, String comment)
            throws AuctionClientException {
        Message resp = sendOrThrow(MessageType.RATE_SELLER_REQUEST,
                new RateSellerRequest(auctionId, stars, comment));
        return MessageCodec.payloadAs(resp, RateSellerResponse.class);
    }

    /** Heartbeat — gửi PING, đo RTT bằng response. Không throw nếu fail; trả -1. */
    public long ping(long timeoutMs) {
        try {
            long t0 = System.currentTimeMillis();
            Message resp = conn.sendRequestSync(MessageType.PING_REQUEST,
                    new long[] { t0 }, timeoutMs);
            if (resp.getType() == MessageType.PONG_RESPONSE) {
                return System.currentTimeMillis() - t0;
            }
            return -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    /** Gửi request và:
     *   - throw nếu IO/timeout
     *   - throw {@link AuctionClientException} với code+message nếu server trả ERROR_RESPONSE
     *   - return message bình thường nếu OK
     */
    private Message sendOrThrow(MessageType type, Object payload) throws AuctionClientException {
        try {
            Message resp = conn.sendRequestSync(type, payload, DEFAULT_TIMEOUT_MS);
            if (resp.getType() == MessageType.ERROR_RESPONSE) {
                ErrorResponse err = MessageCodec.payloadAs(resp, ErrorResponse.class);
                throw new AuctionClientException(err.getCode(), err.getMessage());
            }
            return resp;
        } catch (IOException e) {
            throw new AuctionClientException(ErrorCode.INTERNAL_ERROR,
                    "IO: " + e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new AuctionClientException(ErrorCode.INTERNAL_ERROR,
                    "Server không phản hồi trong thời gian cho phép");
        }
    }
}

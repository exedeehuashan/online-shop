package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/orders")
class OrderController {
  private final CreateOrderHandler create; private final GetOrderUseCase get; private final ListCustomerOrdersUseCase list;
  private final ConfirmOrderUseCase confirm; private final CancelOrderUseCase cancel;
  OrderController(CreateOrderHandler create, GetOrderUseCase get, ListCustomerOrdersUseCase list,
                  ConfirmOrderUseCase confirm, CancelOrderUseCase cancel) {
    this.create=create; this.get=get; this.list=list; this.confirm=confirm; this.cancel=cancel;
  }

  @PostMapping
  @Operation(summary="Create an order", description="Returns 201 for the first request and 200 for an identical idempotent replay.")
  @ApiResponses({@ApiResponse(responseCode="201"),@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400"),@ApiResponse(responseCode="422"),@ApiResponse(responseCode="409")})
  ResponseEntity<OrderResponse> create(@PathVariable String customerId, @RequestHeader("Idempotency-Key") String key,
      @RequestBody CreateRequest request) {
    validateKey(key);
    CreateOrderCommand command = request.command(customerId);
    CreateOrderResult result = create.handle(command, key, hash(request));
    OrderResponse body = OrderResponse.from(result.order());
    if (result.replay()) return ResponseEntity.ok(body);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customerId + "/orders/" + body.id())).body(body);
  }

  @GetMapping("/{orderId}")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="404")})
  OrderResponse detail(@PathVariable String customerId, @PathVariable UUID orderId) {
    return OrderResponse.from(get.handle(new GetOrderQuery(customerId, new OrderId(orderId))));
  }

  @GetMapping
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="422")})
  PageResponse list(@PathVariable String customerId, @RequestParam(defaultValue="0") int page,
                    @RequestParam(defaultValue="20") int size) {
    try { return new PageResponse(list.handle(new ListCustomerOrdersQuery(customerId,page,size)).stream().map(OrderResponse::from).toList(),page,size); }
    catch (IllegalArgumentException e) { throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,e.getMessage()); }
  }

  @PostMapping("/{orderId}/confirmation")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="404"),@ApiResponse(responseCode="409")})
  OrderResponse confirm(@PathVariable String customerId, @PathVariable UUID orderId, @RequestHeader("If-Match") String ifMatch) {
    return OrderResponse.from(confirm.handle(new ConfirmOrderCommand(customerId,new OrderId(orderId),version(ifMatch))));
  }

  @PostMapping("/{orderId}/cancellation")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="404"),@ApiResponse(responseCode="409"),@ApiResponse(responseCode="422")})
  OrderResponse cancel(@PathVariable String customerId, @PathVariable UUID orderId, @RequestHeader("If-Match") String ifMatch,
                       @RequestBody CancelRequest request) {
    return OrderResponse.from(cancel.handle(new CancelOrderCommand(customerId,new OrderId(orderId),request.reason(),version(ifMatch))));
  }

  private static long version(String value) {
    try { if (value == null || !value.matches("\"[0-9]+\"")) throw new NumberFormatException(); return Long.parseLong(value.substring(1,value.length()-1)); }
    catch (NumberFormatException e) { throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"If-Match must be a quoted non-negative version"); }
  }
  private static void validateKey(String key) {
    if (key == null || key.isBlank() || key.length()>128) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"Idempotency-Key must contain 1 to 128 characters");
  }
  private static String hash(CreateRequest request) {
    try {
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      String canonical=request.toString();
      return java.util.HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  record CreateRequest(String customerName,List<ItemRequest> items,AddressRequest shippingAddress) {
    CreateOrderCommand command(String customerId) {
      List<CreateOrderCommand.Item> mapped=items==null?null:items.stream().map(i -> i==null?null:i.command()).toList();
      return new CreateOrderCommand(customerId,customerName,mapped,shippingAddress==null?null:shippingAddress.command());
    }
  }
  record ItemRequest(String productId,String sku,String name,MoneyRequest unitPrice,Integer quantity) {
    CreateOrderCommand.Item command() {
      if (unitPrice==null) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"unit price is required");
      if (!"NZD".equals(unitPrice.currency())) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"currency must be NZD");
      return new CreateOrderCommand.Item(productId,sku,name,unitPrice.amount(),quantity==null?0:quantity);
    }
  }
  record MoneyRequest(BigDecimal amount,String currency) {}
  record AddressRequest(String recipient,String line1,String line2,String city,String region,String postalCode,String countryCode,String phone) {
    CreateOrderCommand.Address command(){return new CreateOrderCommand.Address(recipient,line1,line2,city,region,postalCode,countryCode,phone);}
  }
  record CancelRequest(String reason) {}
  record PageResponse(List<OrderResponse> content,int page,int size) {}
  record OrderResponse(UUID id,String customerId,String customerName,String status,List<ItemResponse> items,MoneyResponse total,
      AddressResponse shippingAddress,String cancellationReason,long version,java.time.Instant createdAt,java.time.Instant updatedAt) {
    static OrderResponse from(Order o) { return new OrderResponse(o.id().value(),o.customerReference().value(),o.customerNameSnapshot(),o.status().name(),
      o.items().stream().map(ItemResponse::from).toList(),MoneyResponse.from(o.total()),AddressResponse.from(o.shippingAddress()),
      o.cancellationReason().orElse(null),o.version(),o.createdAt(),o.updatedAt()); }
  }
  record ItemResponse(String productId,String sku,String name,MoneyResponse unitPrice,int quantity,MoneyResponse lineTotal) {
    static ItemResponse from(OrderItem i){return new ItemResponse(i.productReference().value(),i.sku(),i.productNameSnapshot(),MoneyResponse.from(i.unitPriceSnapshot()),i.quantity(),MoneyResponse.from(i.lineTotal()));}
  }
  record MoneyResponse(String amount,String currency) { static MoneyResponse from(Money m){return new MoneyResponse(m.amount().toPlainString(),m.currency());} }
  record AddressResponse(String recipient,String line1,String line2,String city,String region,String postalCode,String countryCode,String phone) {
    static AddressResponse from(AddressSnapshot a){return new AddressResponse(a.recipient(),a.line1(),a.line2(),a.city(),a.region(),a.postalCode(),a.countryCode(),a.phone());}
  }
}

package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/orders")
class OrderController {
  private final CreateOrderHandler create; private final GetOrderUseCase get; private final ListCustomerOrdersUseCase list;
  private final ConfirmOrderHandler confirm; private final CancelOrderHandler cancel; private final OrderAccessPolicy access;
  OrderController(CreateOrderHandler create, GetOrderUseCase get, ListCustomerOrdersUseCase list,
                  ConfirmOrderHandler confirm, CancelOrderHandler cancel, OrderAccessPolicy access) {
    this.create=create; this.get=get; this.list=list; this.confirm=confirm; this.cancel=cancel; this.access=access;
  }

  @PostMapping
  @Operation(summary="Create an order", description="Returns 201 for the first request and 200 for an identical idempotent replay.")
  @ApiResponses({
    @ApiResponse(responseCode="201",headers=@Header(name="Location",description="URI of the created order",schema=@Schema(type="string",format="uri"))),
    @ApiResponse(responseCode="200"),
    @ApiResponse(responseCode="400",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="409",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="415",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="422",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))})
  ResponseEntity<OrderResponse> create(@PathVariable String customerId, @RequestHeader("Idempotency-Key") String key,
      @RequestBody CreateRequest request, HttpServletRequest http) {
    OrderPrincipal principal=access.authorizeV1(customerId,false);
    validateKey(key);
    CreateOrderCommand command = request.command(customerId);
    boolean other=principal!=null&&!principal.userId().toString().equals(customerId);
    if(other&&!principal.permits("order:manage")) throw new org.springframework.security.access.AccessDeniedException("Request denied");
    CreateOrderResult result = principal==null?create.handle(command,key,hash(request)):
        create.handle(command,key,hash(request),principal.userId(),other?"V1_CREATE_FOR_OTHER":"ORDER_CREATED",OrderCorrelationFilter.current(http));
    OrderResponse body = OrderResponse.from(result.order());
    if (result.replay()) return ResponseEntity.ok(body);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customerId + "/orders/" + body.id())).body(body);
  }

  @GetMapping("/{orderId}")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="404",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))})
  OrderResponse detail(@PathVariable String customerId, @PathVariable UUID orderId) {
    access.authorizeV1(customerId,false);
    return OrderResponse.from(get.handle(new GetOrderQuery(customerId, new OrderId(orderId))));
  }

  @GetMapping
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="422",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))})
  PageResponse list(@PathVariable String customerId, @RequestParam(defaultValue="0") int page,
                    @RequestParam(defaultValue="20") int size) {
    access.authorizeV1(customerId,false);
    try { return new PageResponse(list.handle(new ListCustomerOrdersQuery(customerId,page,size)).stream().map(OrderResponse::from).toList(),page,size); }
    catch (IllegalArgumentException e) { throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,e.getMessage()); }
  }

  @PostMapping("/{orderId}/confirmation")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="404",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="409",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))})
  OrderResponse confirm(@PathVariable String customerId, @PathVariable UUID orderId, @RequestHeader("If-Match") String ifMatch,HttpServletRequest http) {
    OrderPrincipal principal=access.authorizeV1(customerId,true);
    return OrderResponse.from(principal==null?confirm.handle(new ConfirmOrderCommand(customerId,new OrderId(orderId),version(ifMatch))):
      confirm.handle(new ConfirmOrderCommand(customerId,new OrderId(orderId),version(ifMatch)),principal.userId(),
        principal.userId().toString().equals(customerId)?"ORDER_CONFIRMED":"V1_MANAGE_FOR_OTHER",OrderCorrelationFilter.current(http)));
  }

  @PostMapping("/{orderId}/cancellation")
  @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="400",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="404",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="409",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="415",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="422",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))})
  OrderResponse cancel(@PathVariable String customerId, @PathVariable UUID orderId, @RequestHeader("If-Match") String ifMatch,
                       @RequestBody CancelRequest request,HttpServletRequest http) {
    OrderPrincipal principal=access.authorizeV1(customerId,true);
    return OrderResponse.from(principal==null?cancel.handle(new CancelOrderCommand(customerId,new OrderId(orderId),request.reason(),version(ifMatch))):
      cancel.handle(new CancelOrderCommand(customerId,new OrderId(orderId),request.reason(),version(ifMatch)),principal.userId(),
        principal.userId().toString().equals(customerId)?"ORDER_CANCELLED":"V1_MANAGE_FOR_OTHER",OrderCorrelationFilter.current(http)));
  }

  static long version(String value) {
    try { if (value == null || !value.matches("\"[0-9]+\"")) throw new NumberFormatException(); return Long.parseLong(value.substring(1,value.length()-1)); }
    catch (NumberFormatException e) { throw new MalformedHeaderException("If-Match must be a quoted non-negative version"); }
  }
  static void validateKey(String key) {
    if (key == null || key.isBlank() || key.length()>128) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"Idempotency-Key must contain 1 to 128 characters");
  }
  static String hash(CreateRequest request) {
    try {
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      String canonical=request.canonical();
      return java.util.HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  record CreateRequest(String customerName,List<ItemRequest> items,AddressRequest shippingAddress) {
    CreateOrderCommand command(String customerId) {
      List<CreateOrderCommand.Item> mapped=items==null?null:items.stream().map(i -> i==null?null:i.command()).toList();
      return new CreateOrderCommand(customerId,customerName,mapped,shippingAddress==null?null:shippingAddress.command());
    }
    String canonical() { return canonicalValue(customerName)+canonicalList(items)+canonicalValue(shippingAddress); }
  }
  record ItemRequest(String productId,String sku,String name,MoneyRequest unitPrice,Integer quantity) {
    CreateOrderCommand.Item command() {
      if (unitPrice==null) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"unit price is required");
      if (!"NZD".equals(unitPrice.currency())) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"currency must be NZD");
      return new CreateOrderCommand.Item(productId,sku,name,unitPrice.amount(),quantity==null?0:quantity);
    }
    @Override public String toString() { return canonicalValue(productId)+canonicalValue(sku)+canonicalValue(name)+canonicalValue(unitPrice)+canonicalValue(quantity); }
  }
  record MoneyRequest(BigDecimal amount,String currency) {
    @Override public String toString() { return canonicalValue(amount==null?null:amount.stripTrailingZeros().toPlainString())+canonicalValue(currency); }
  }
  record AddressRequest(String recipient,String line1,String line2,String city,String region,String postalCode,String countryCode,String phone) {
    CreateOrderCommand.Address command(){return new CreateOrderCommand.Address(recipient,line1,line2,city,region,postalCode,countryCode,phone);}
    @Override public String toString(){return canonicalValue(recipient)+canonicalValue(line1)+canonicalValue(line2)+canonicalValue(city)+canonicalValue(region)+canonicalValue(postalCode)+canonicalValue(countryCode)+canonicalValue(phone);}
  }
  record CancelRequest(@Schema(requiredMode=Schema.RequiredMode.REQUIRED) String reason) {}
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
  private static String canonicalList(List<?> values) { return values==null?"-1:":values.stream().map(OrderController::canonicalValue).reduce(values.size()+":[",(a,b)->a+b)+"]"; }
  private static String canonicalValue(Object value) { String text=String.valueOf(value); return value==null?"-1:":text.length()+":"+text; }

}

final class MalformedHeaderException extends IllegalArgumentException {
  MalformedHeaderException(String message) { super(message); }
}

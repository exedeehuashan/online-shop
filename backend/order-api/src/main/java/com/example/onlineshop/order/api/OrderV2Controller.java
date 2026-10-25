package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
class OrderV2Controller {
  private final CreateOrderHandler create; private final ConfirmOrderHandler confirm; private final CancelOrderHandler cancel;
  private final OrderRepository orders; private final OrderAccessPolicy access;
  OrderV2Controller(CreateOrderHandler create,ConfirmOrderHandler confirm,CancelOrderHandler cancel,OrderRepository orders,OrderAccessPolicy access){
    this.create=create;this.confirm=confirm;this.cancel=cancel;this.orders=orders;this.access=access;
  }

  @PostMapping("/api/v2/me/orders")
  @Operation(operationId = "createSelfOrder")
  ResponseEntity<OrderController.OrderResponse> create(@RequestHeader("Idempotency-Key")String key,
      @RequestBody OrderController.CreateRequest request,HttpServletRequest http){
    OrderPrincipal principal=access.require("order:read:self");OrderController.validateKey(key);
    CreateOrderResult result=create.handle(request.command(principal.userId().toString()),key,OrderController.hash(request),principal.userId(),
        "ORDER_CREATED",OrderCorrelationFilter.current(http));
    OrderController.OrderResponse body=OrderController.OrderResponse.from(result.order());
    return ResponseEntity.status(result.replay()?200:201).location(URI.create("/api/v2/me/orders/"+body.id()))
        .eTag(Long.toString(body.version())).body(body);
  }

  @GetMapping("/api/v2/me/orders")
  OrderController.PageResponse selfList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
    OrderPrincipal p=access.require("order:read:self");validatePage(page,size);
    return page(orders.findByCustomer(new CustomerReference(p.userId().toString()),page,size),page,size);
  }
  @GetMapping("/api/v2/me/orders/{orderId}")
  ResponseEntity<OrderController.OrderResponse> selfDetail(@PathVariable UUID orderId){
    OrderPrincipal p=access.require("order:read:self");return response(orders.findByIdAndCustomer(new OrderId(orderId),new CustomerReference(p.userId().toString())).orElseThrow(OrderNotFoundException::new));
  }
  @PostMapping("/api/v2/me/orders/{orderId}/confirmation")
  ResponseEntity<OrderController.OrderResponse> selfConfirm(@PathVariable UUID orderId,@RequestHeader("If-Match")String match,HttpServletRequest http){
    OrderPrincipal p=access.require("order:read:self");return response(confirm.handle(new ConfirmOrderCommand(p.userId().toString(),new OrderId(orderId),OrderController.version(match)),p.userId(),"ORDER_CONFIRMED",OrderCorrelationFilter.current(http)));
  }
  @PostMapping("/api/v2/me/orders/{orderId}/cancellation")
  ResponseEntity<OrderController.OrderResponse> selfCancel(@PathVariable UUID orderId,@RequestHeader("If-Match")String match,
      @RequestBody OrderController.CancelRequest request,HttpServletRequest http){
    OrderPrincipal p=access.require("order:read:self");return response(cancel.handle(new CancelOrderCommand(p.userId().toString(),new OrderId(orderId),request.reason(),OrderController.version(match)),p.userId(),"ORDER_CANCELLED",OrderCorrelationFilter.current(http)));
  }

  @GetMapping("/api/v2/admin/orders")
  OrderController.PageResponse adminList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
    access.require("order:read:any");validatePage(page,size);return page(orders.findAll(page,size),page,size);
  }
  @GetMapping("/api/v2/admin/orders/{orderId}")
  ResponseEntity<OrderController.OrderResponse> adminDetail(@PathVariable UUID orderId){access.require("order:read:any");return response(any(orderId));}
  @PostMapping("/api/v2/admin/orders/{orderId}/confirmation")
  ResponseEntity<OrderController.OrderResponse> adminConfirm(@PathVariable UUID orderId,@RequestHeader("If-Match")String match,HttpServletRequest http){
    OrderPrincipal p=access.requireBoth("order:read:any","order:manage");Order found=any(orderId);
    return response(confirm.handle(new ConfirmOrderCommand(found.customerReference().value(),new OrderId(orderId),OrderController.version(match)),p.userId(),"ORDER_CONFIRMED",OrderCorrelationFilter.current(http)));
  }
  @PostMapping("/api/v2/admin/orders/{orderId}/cancellation")
  ResponseEntity<OrderController.OrderResponse> adminCancel(@PathVariable UUID orderId,@RequestHeader("If-Match")String match,
      @RequestBody OrderController.CancelRequest request,HttpServletRequest http){
    OrderPrincipal p=access.requireBoth("order:read:any","order:manage");Order found=any(orderId);
    return response(cancel.handle(new CancelOrderCommand(found.customerReference().value(),new OrderId(orderId),request.reason(),OrderController.version(match)),p.userId(),"ORDER_CANCELLED",OrderCorrelationFilter.current(http)));
  }
  private Order any(UUID id){return orders.findById(new OrderId(id)).orElseThrow(OrderNotFoundException::new);}
  private static ResponseEntity<OrderController.OrderResponse> response(Order order){OrderController.OrderResponse body=OrderController.OrderResponse.from(order);return ResponseEntity.ok().eTag(Long.toString(body.version())).body(body);}
  private static OrderController.PageResponse page(java.util.List<Order> orders,int page,int size){return new OrderController.PageResponse(orders.stream().map(OrderController.OrderResponse::from).toList(),page,size);}
  private static void validatePage(int page,int size){if(page<0||size<1||size>100)throw new DomainValidationException(DomainErrorCode.INVALID_VALUE,"page must be non-negative and size must be between 1 and 100");}
}

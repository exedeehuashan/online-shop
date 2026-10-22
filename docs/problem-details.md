# Order API problem details

The Order API returns RFC 9457 Problem Details using `application/problem+json`. The stable type namespace is this document plus a lowercase kebab-case error-code fragment, for example `#malformed-request`.

## malformed-request
The request syntax, path/header binding, or required header is malformed.

## invalid-value
One or more request values violate semantic validation. The `violations` array contains validation messages.

## duplicate-sku
The same SKU occurs more than once in an order.

## order-not-found
The order does not exist for the customer identified by the request path.

## idempotency-conflict
An idempotency key was previously used with a different request.

## optimistic-lock-conflict
The supplied version does not match the current order version.

## order-state-conflict
The requested transition is invalid for the current order state.

## method-not-allowed
The HTTP method is not supported by the target resource.

## unsupported-media-type
The request content type is not supported.

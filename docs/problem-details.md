# Order API problem details

The Order API returns RFC 9457 Problem Details using `application/problem+json`. The stable type namespace is this document plus a lowercase kebab-case error-code fragment, for example `#malformed-request`.

Stable machine codes include `MALFORMED_REQUEST`, `INVALID_PRODUCT`, and `PRODUCT_NOT_FOUND`; clients must use `code`, not localized titles.

## invalid-product
Catalog input failed semantic validation (`INVALID_PRODUCT`).

## product-not-found
The requested Catalog UUID does not exist (`PRODUCT_NOT_FOUND`).

## malformed-request
The request syntax, path/header binding, or required header is malformed.

## invalid-catalog-query

Catalog v2 browse parameters failed allow-list, bounds, or referenced-value validation (`INVALID_CATALOG_QUERY`).

## internal-error

An unexpected Catalog v2 read failure occurred (`INTERNAL_ERROR`). The correlation ID can be used for support without exposing internal details.

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

# Identity security

- `AUTHENTICATION_REQUIRED` (401): bearer credentials are missing, malformed, expired, or fail issuer, audience, signature, time, or algorithm validation.
- `LOCAL_PRINCIPAL_DENIED` (403): the externally authenticated issuer/subject does not map to one active application principal.
- `ACCESS_DENIED` (403): the active application principal lacks the exact required permission.

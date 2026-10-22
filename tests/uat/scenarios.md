# UAT scenarios

## Catalog (#1)
Given a healthy local stack, when a synthetic product is created, then list and detail return the same UUID, exact decimal price, ISO currency and stock. Invalid input is 422 and an unknown UUID is 404.

## Order creation (#13)
Given deterministic customer/item/address snapshots, when an order is created and the same idempotency key is replayed, then 201 followed by 200 returns one stable public UUID and the snapshots remain unchanged.

## Order retrieval (#14)
Given the created order, when list and detail are requested for its customer, then it appears newest-first and no internal numeric IDs are exposed.

## Confirmation (#15)
Given a draft order version, when confirmation uses the matching quoted `If-Match`, then status is CONFIRMED; a stale retry conflicts.

## Cancellation (#16)
Given a separate draft order, when cancellation supplies a reason and matching version, then status/reason are retained; invalid transitions conflict.

## Human visual step (#17)
At `http://127.0.0.1:8080`, keyboard-navigate list/create/detail, confirm visible focus, readable errors/status, responsive layout at 320/768/1280 px, and no obvious contrast or clipping defect. Record browser/OS/screenshots in the independent tester report.

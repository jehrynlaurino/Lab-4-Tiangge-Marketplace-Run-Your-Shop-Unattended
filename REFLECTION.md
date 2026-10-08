# REFLECTION.md

## Question 1

**At 19:03:44 your request for BuyerRef "TEST-001" (X-Request-Id TEST-REQ-001) received a 503, but LegacySupply had already created PO-100005. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.**

The 503 (`E-SYS-50 Processing error`) meant the response was lost, not that the order failed to process - LegacySupply had actually created `PO-100005` before or while it sent back the 503. When I retried the exact same request, I reused the same `X-Request-Id` (`TEST-REQ-001`) rather than generating a new one. LegacySupply's own idempotency layer recognized this as a replay of an already-processed request and returned the existing `PO-100005` instead of creating a second order - this is exactly what the self-check page's "2 chaos events on orders, 3 safe replays, 0 duplicates" metric confirms happened. In `SupplierGatewayImpl`, this same protection is automatic: the `requestId` is generated once with `UUID.randomUUID()` and persisted to `supplier_orders` *before* the first network call, so any retry - whether inside `LegacySupplyClient`'s own backoff loop or a later resend by `PendingReorderScheduler` - always sends that identical `X-Request-Id`. A duplicate order would only be possible if I generated a fresh `requestId` on each retry, which the code deliberately never does.

## Question 2

**PO-100215 (BuyerRef "RO-47e27967-3faf-48d4-985e-b6b14417cd4e") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?**

I checked the interface manual directly (Rev. 2.3.1) and confirmed the "Status and error codes" section only documents 10 (Accepted), 20 (Picking), 30 (Shipped), and 40 (Delivered) - `90` doesn't appear anywhere, and the manual explicitly says partner support won't answer questions the manual already covers, so I treated it as a genuinely undocumented status rather than guessing at its meaning. `DeliveryTrackingScheduler.mapStatusCode()` returns `null` for any code outside those four, and when that happens the order's stored status is left exactly as it was - not advanced to `DELIVERED`, not marked `FAILED` - and a `WARN` log line is written instead (visible in my logs as repeated "Unexpected LegacySupply status code 90" entries). Because the order stays `SUBMITTED`, it remains in the open-orders polling set and keeps getting checked every cycle, which is why the self-check page shows repeated "status poll, saw 90" entries for it. Practically, this means the 1 unit of Mechanical Keyboard tied up in `PO-100215` is never restocked into Inventory, since `SupplierOrderDelivered` is only published on a confirmed transition into `DELIVERED` - I decided that silently guessing (either crediting stock that may never arrive, or marking the order `FAILED` and potentially triggering an automatic re-reorder for stock that's actually still in transit) was riskier than leaving it flagged for a human to investigate.

## Question 3

**LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.**

I measured this twice from my own request logs. The first measurement: I authenticated at 18:58:53 and a repeated catalog call on the same token succeeded, but the next one failed with `E-AUTH-07` at 19:02:28 - a lifetime under about 3 minutes 35 seconds. The second measurement, from the self-check page's own request log: I authenticated at 03:16:11 and the first `401` on that token appeared at 03:19:25 (3 min 14 sec later); after re-authenticating at 03:19:39, the next `401` appeared at 03:23:16 (3 min 37 sec later). Both measurements land in the same narrow window, so I'd put the real session lifetime at roughly **3 to 3.5 minutes**. My adapter never tries to track or predict this expiry directly - `LegacySupplySessionManager` authenticates lazily on first use and simply holds the token in memory until something tells it the token is bad. When `LegacySupplyClient` gets an HTTP 401 back from any call, it invalidates the cached session exactly once and retries that same request immediately with a freshly obtained token, rather than sleeping through a backoff cycle first. This reactive approach means I never need to guess the real lifetime correctly in code - a short, unpredictable session (like this one) is handled the same way as a long one would be.

---

# Lab 4: Marketplace (Tiangge) reflection

## Question 1

**Event evt_4300cb2458975ef5 (order TG-ESJNQF) reached your application twice, as seq 1614 and seq 1617, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.**

I deduplicate on Tiangge's `orderId`, not on `seq` or `eventId`, because a redelivered event arrives with a new seq. `ChannelOrderWriter.decideAndPersistPlaced` first calls `channelOrderRepository.findByTiangeOrderId(...)`. If a row already exists, it returns that row and never calls `OrderService.placeOrder` again. The `channel_orders` table uses `tiangge_order_id` as its primary key, and the row is saved in the same transaction that places the real order, so an order can't exist without its row or the other way around. On the second delivery the lookup found the row, so no second order was created, and the only possible follow-up was resuming a Tiangge call that was still marked unsynced. If the app restarted between the two deliveries, the row is in Postgres rather than in memory, so the later delivery would hit the same lookup and do nothing harmful.

## Question 2

**Order TG-YXK936 was backordered at 00:41:14 and accepted at 00:43:20, after PO-103888 was delivered at 00:42:08. Trace how the delivery reached your Inventory and what then resumed the backordered order.**

`DeliveryTrackingScheduler` in the supplier module polled PO-103888, saw status code 40, mapped it to my own `DELIVERED` status, and published `SupplierOrderDelivered`. `SupplierDeliveryRestockListener` in Inventory handled that event synchronously and called `InventoryService.restock`, which published `InventoryStockChanged`. `BackorderResolutionListener` also receives the delivery event, but it is `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`, so it only runs after the restock has committed. It calls `TiangeOrderProcessor.onProductDelivered`, which finds `BACKORDERED` rows containing that product. For each one, `ChannelOrderWriter.attemptBackorderResolution` re-fetches the row under a pessimistic write lock, re-checks that it is still backordered, and calls `OrderService.placeOrder` again. Once that succeeded, the row became `ACCEPTED` with a pending `RESOLUTION`, and `TiangeReportDispatcher` posted the resolution to Tiangge.

## Question 3

**During your restart test your application was down for about 141 seconds while 4 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?**

The feed position is stored in the single-row `channel_feed_cursor` table in Postgres, not in memory. On startup `FeedPollerScheduler` reads `last_seq` and calls `GET /feed?after=<last_seq>`, so Tiangge returned only events after the point where the old instance stopped, including the 4 orders that arrived while I was down. The cursor only advances past an event after that event's database work succeeded, so a crash can never skip an order. Even if an earlier event were redelivered, the `channel_orders` lookup by `tiangge_order_id` would make it a no-op. The restarted app also generates a new instance ID and republishes its listings, which is why it shows as a new instance on the self-check page.

## A bug found along the way

TG-EXAGP3 needed both P100 and P200, and two supplier deliveries triggered backorder resolution for it at nearly the same time. Both runs saw it as still backordered and both placed a real order, which reserved its stock twice. I fixed this with a pessimistic lock on the channel order row (`findForUpdate`) and a re-check of its status under that lock.

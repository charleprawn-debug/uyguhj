# Provider marketplace API research

## Proxy-Cheap
Source: https://docs.proxy-cheap.com/

- Authentication uses `X-Api-Key` and `X-Api-Secret` headers.
- Base API: `https://api.proxy-cheap.com`.
- `GET /v2/order` returns services and optional plans.
- `POST /v2/order/{serviceId}` with `{ "planId": "" }` returns setup options such as countries, regions, cities, and ISPs.
- `POST /v2/order/{serviceId}/price` prices a configuration. The documented body includes `planId`, `quantity`, `country`, `region`, `city`, `ispId`, `period: { unit, value }`, and `couponCode`.
- `POST /v2/order/{serviceId}/execute` buys the same configuration and additionally accepts `autoExtend: { isEnabled, traffic }` and `traffic`.
- `GET /orders/{id}/proxies` returns credentials and connection ports. The response may initially be `PENDING`, so the integration must poll briefly before saving the proxy.
- Prices can vary by selected location, so the application must calculate the price after the location is selected rather than using constants.

## Proxy-Seller API v2
Sources:
- https://docs.proxy-seller.com/api-v2/overview-2
- https://docs.proxy-seller.com/api-v2/authorization
- https://docs.proxy-seller.com/api-v2/order-actions
- https://docs.proxy-seller.com/api-v2/order-actions/complete-information
- https://docs.proxy-seller.com/api-v2/order-actions/calculate-the-order
- https://docs.proxy-seller.com/api-v2/order-actions/place-an-order
- https://docs.proxy-seller.com/api-v2/actions-with-proxies-2

- Base URL: `https://proxy-seller.com/personal/api/v2/{API_KEY}/`; the key is in the URL path.
- `GET /reference/list/{type}` returns valid country, period, target and carrier identifiers. Supported types include `ipv4`, `ipv6`, `mobile`, `isp`, `resident`, `mix`, and `mix_isp`.
- `POST /order/calc` is a dry-run price calculation. Common fields include `countryId`, `periodId`, `paymentId: "balance"`, `quantity`, `authorization`, `customTargetName`, and `sectionCode`; protocol is included for IPv6 and mobile variants.
- `POST /order/make` places the order and charges the provider balance. It returns `orderId`, `total`, and order numbers. API business errors can be returned with HTTP 200 and a populated `errors[]` array, so status alone is insufficient.
- `GET /proxy/list/{type}?orderId=...` retrieves provisioned proxies. Provisioning can take seconds to a minute, so the integration must poll.
- Prices are USD and must be fetched from `order/calc`; no application-side price constants should be used.

# Order authentication configuration

Order authentication is opt-in so the frozen unauthenticated v1 local stack remains available. The switch is explicit and deny-by-default: when enabled, v1 and v2 API routes require a validated bearer token, only health is public, and every unlisted route is denied. Missing strict settings make startup fail; they do not fall back to anonymous access.

| Environment variable | Spring property | Required when enabled | Meaning |
|---|---|---:|---|
| `ORDER_SECURITY_ENABLED` | `order.security.enabled` | yes (`true`) | Select authenticated security chain |
| `ORDER_OIDC_JWKS_URI` | `order.security.jwk-set-uri` | yes | Internal HTTPS/JWKS endpoint for signature validation |
| `ORDER_OIDC_ISSUER` | `order.security.issuer` | yes | Exact accepted `iss` value |
| `ORDER_OIDC_AUDIENCE` | `order.security.audience` | yes | Required token audience |
| `ORDER_IDENTITY_BASE_URL` | `order.security.identity-base-url` | yes | Internal Identity API base URL used for `/api/v1/me` |

Production values must come from the deployment secret/configuration system. Do not commit tokens, private keys, client secrets, or production endpoints. The checked-in Compose overlay is a disposable, loopback-only UAT mechanism: it generates an ephemeral RSA key at startup, publishes only a short-lived synthetic token endpoint on loopback, seeds a synthetic active Identity principal, and contains no reusable secret.

```sh
make order-security-up
make order-security-ready
make test-order-v2-uat
make order-security-down
```

The overlay is `compose.order-security.yaml`; inspect the fully resolved contract with:

```sh
docker compose -f compose.yaml -f compose.order-security.yaml config
```

The token issuer is a test fixture and must never be publicly exposed or used with real data. The authenticated chain intentionally denies `/v3/api-docs`; checked-in OpenAPI is exported from the local legacy-mode application and verified separately. The base stack remains unauthenticated and loopback-only for frozen v1 UAT; never expose it publicly.

# PayFast subscription processing with ReactJS and Java

A minimal demo application of a fictitious store that sells magazine subscriptions.

## What's it for?
This demo webapp aims to showcase one way subscription payments can be processed using PayFast, with a ReactJS UI and a Java REST API.

There's a brief write-up about it here: [Hashnode](https://njabulo.hashnode.dev/accept-payfast-subscription-payment-with-reactjs-and-java)

## What it covers (and what it doesn't)
The demo covers **starting a subscription and keeping its status up to date**:

1. The customer picks a magazine and a billing option: **monthly** (12 monthly payments) or **annually** (12 months less 10%, billed once a year until cancelled). Prices are always worked out by the backend.
2. At checkout the backend saves a `PENDING` subscription and returns a signed form. The browser posts it to PayFast, which collects the first payment and sets up the recurring billing.
3. PayFast reports every payment, failure and cancellation to the backend's notification endpoint (ITN). Each notification is validated and recorded in the subscription's payment history, and the subscription becomes `ACTIVE`, `FAILED` or `CANCELLED`.

It does **not** manage a subscription afterwards. Pausing, cancelling or changing it from the shop through PayFast's subscriptions API, emails, and a "my subscriptions" page are left out. The PayFast token that would be needed for that is stored, though.

## How do I run it?
If you have docker on your environment, you can issue the start-up command below

```
docker-compose up -d
```
***Note:***
When running on localhost, you may want to make use of tools such as localtunnel, ngrok, or something similar to create a tunnel to your localhost.

Also, whether on localhost or on a hosted environment, remember that the backend reads the settings below from environment variables (see `application.properties` and `PayFastProperties.java`). `docker-compose` passes them through from your shell or from a `.env` file next to `docker-compose.yaml`.
> MERCHANT_ID, MERCHANT_KEY, PASS_PHRASE, RETURN_URL, CANCEL_URL and, NOTIFY_URL

`NOTIFY_URL` must point at `<your public backend URL>/shop/payment/notification`. Every notification (ITN) PayFast sends there is checked before it is trusted: the signature, the merchant id, that it came from a PayFast server, that it belongs to a known subscription, that a payment is for the right amount, and finally PayFast is asked to confirm it. Duplicate notifications are acknowledged but not applied twice, and a cancelled subscription is never reactivated.

Optional settings:
> PAYFAST_SANDBOX (default `true`), PAYFAST_VERIFY_SOURCE_IP (default `true`), PAYFAST_VERIFY_WITH_SERVER (default `true`), H2_CONSOLE_ENABLED (default `false`)

`PAYFAST_SANDBOX` decides both where the checkout form is posted (`sandbox.payfast.co.za` or `www.payfast.co.za`) and which server confirms notifications, so the two always match.

The source-IP check works through ngrok, localtunnel and Docker because the backend trusts `X-Forwarded-For` from private/loopback proxies. If your tunnel doesn't send that header, set `PAYFAST_VERIFY_SOURCE_IP=false` while testing locally.

The H2 database console (`/h2`) is off by default. Turn it on with `H2_CONSOLE_ENABLED=true` only for local debugging, and never while the backend is exposed through a tunnel.

To stop it, issue the stop command below

```
docker-compose down
```

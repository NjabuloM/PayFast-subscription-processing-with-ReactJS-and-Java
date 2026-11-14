# PayFast subscription processing with ReactJS and Java

A minimal demo application of a fictitious store that sells magazine subscriptions.

## What's it for?
This demo webapp aims to showcase one way subscription payments can be processed using PayFast, with a ReactJS UI and a Java REST API.

There's a brief write-up about it here: [Hashnode](https://njabulo.hashnode.dev/accept-payfast-subscription-payment-with-reactjs-and-java)

## How do I run it?
If you have docker on your environment, you can issue the start-up command below

```
docker-compose up -d
```
***Note:***
When running on localhost, you may want to make use of tools such as localtunnel, ngrok, or something similar to create a tunnel to your localhost.

Also, whether on localhost or on a hosted environment, remember that the backend reads the settings below from environment variables (see `application.properties` and `PayFastProperties.java`). `docker-compose` passes them through from your shell or from a `.env` file next to `docker-compose.yaml`.
> MERCHANT_ID, MERCHANT_KEY, PASS_PHRASE, RETURN_URL, CANCEL_URL and, NOTIFY_URL

`NOTIFY_URL` must point at `<your public backend URL>/shop/payment/notification`. Every notification (ITN) PayFast sends there is checked before it is trusted: the signature, the merchant id, that it came from a PayFast server, that the amount matches, and finally PayFast is asked to confirm it. Only then is the subscription marked `ACTIVE`, `CANCELLED` or `FAILED` and the PayFast token saved.

Optional settings:
> PAYFAST_SANDBOX (default `true`), PAYFAST_VERIFY_SOURCE_IP (default `true`), PAYFAST_VERIFY_WITH_SERVER (default `true`)

The source-IP check works through ngrok, localtunnel and Docker because the backend trusts `X-Forwarded-For` from private/loopback proxies. If your tunnel doesn't send that header, set `PAYFAST_VERIFY_SOURCE_IP=false` while testing locally.

To stop it, issue the stop command below

```
docker-compose down
```

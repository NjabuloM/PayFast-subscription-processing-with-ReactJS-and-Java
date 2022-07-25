# PayFast subscription processing with ReactJS and Java

A minimal demo application of a fictitious store that sells magazine subscriptions.

## What's it for?
This demo webapp aims to showcase one way subscription payments can be processed using PayFast, with a ReactJS UI and a Java REST API.

There's a brief write-up about it here: [Hashnode](https://hashnode.com)

## How do I run it?
If you have docker on your environment, you can issue the start-up command below

```
docker-compose up -d
```
***Note:***
When running on localhost, you may want to make use of tools such as localtunnel, ngrok, or something similar to create a tunnel to your localhost.

Also, whether on localhost or on a hosted environment, remember that the backend reads the fields below from System properties. You can either set them or change the code to read them from somewhere else (see ShopService.java, line 49-56).
> MERCHANT_ID, MERCHANT_KEY, PASS_PHRASE, RETURN_URL, CANCEL_URL and, NOTIFY_URL

To stop it, issue the stop command below

```
docker-compose down
```

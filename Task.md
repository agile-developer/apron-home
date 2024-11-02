# Apron - Technical Take Home Exercise

Create an app to pay multiple invoices at once.

## User workflow

You're building an app for businesses to pay their invoices.

1. User selects one or more invoices to pay.
2. User sends total invoices amount to the app.
3. App pays the invoices selected by the user, using the `TransferGateway`.

## Task

* Your primary task is to distribute funds. As part of this, you may discover that in order to have sufficient funds to distribute, you will first have to to somehow collect funds from the customer. Feel free to introduce a suitable API to perform these top ups.
* Implement the following models:
    * `Invoice`: represents an invoice with an amount to pay and payment details. Payment details is just a string like
      `"77777777"`.
    * `Account`: represents a user account with a balance. Balance should be of the `BigDecimal` type.
    * Feel free to implement any other models that you would find useful.
* For the payouts, use `TransferGateway` interface (details below).

Be free to using any testing strategy or framework you prefer.

You are welcome to use LLMs for guidance, however please note that you are likely to be asked in detail about your code, so you should fully understand anything that you decide to copy and paste.

## TransferGateway

`TransferGateway` represents an external service you should use to make payouts.
`TransferGateway#transfer()` receives the following values:

* `targetPaymentDetails`: the payment details to transfer money to (like `"77777777"`, comes from `Invoice`).
* `amount`: the amount to transfer.
* `currency`: the currency of the amount to transfer, always `GBP` for now.

`transfer()` can throw the following exceptions:

* `RequestTimeoutException`: sometimes the request may timeout (e.g. there's some network issue or the external service
  is temporarily unavailable). You can retry the transfer.
* `TransferDeclinedException`: the transfer was declined. This can happen for various reasons (e.g. the target account
  doesn't exist, the target account is blocked, the target account doesn't accept transfers, etc.). In case of
  decline, retrying will not help. You can skip paying this invoice.

Also: every successful transfer will cost a flat fee (~£1.00).

## Out of scope

You are not required to implement the features below, but we may touch on some of them later during the interview:

* Multiple currencies (assume all invoices are in `GBP`).
* User interface.
* User authentication.
* `TransferGateway` implementation (you can use `DemoTransferGateway` provided).

Bonus points for any of the above that you do implement, however!

## Requirements

* Use Kotlin or Java (or Scala if you strongly prefer to).
* Use an application server of your choice.
* Bonus points if you implement DB persistence.
* Please provide instructions on how to run the app.

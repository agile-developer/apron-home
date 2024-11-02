# Apron Home Assignment

### Summary
This is a simple application that provides endpoints to:
- search for accounts for a given user
- top-up a single account, by account-id, with a given amount and currency
- search for invoices for given user
    - all invoices
    - by invoice status
- transfer one or more invoices by invoice-id, indicating which account to use for payment using account-id

### Running the application
This project is implemented in Kotlin (`1.9.25`) and compiled for Java 21. Please ensure the target machine has Java 21 installed, and `java` is available on the `PATH`. It also uses Postgres for data, so a Postgres server must be available on or accessible from the local machine. The simplest way is to run Postgres as a Docker container. You'll need Docker Desktop, Podman, Rancher or some other OCI compliant runtime that can run container images.

For Docker, I've used the following command:
```shell
docker run -d \
--name apron-home \
-p 5432:5432 \
-e POSTGRES_DB=apron-home-db \
-e POSTGRES_USER=apron-home-user \
-e POSTGRES_PASSWORD=this1isNotGood! \
postgres:latest
```
**Important**: The database must be available before trying to run the application.

Once you've cloned the repository, or unzipped the source directory, change to the `home-task-be-agile-developer` folder and run the following command, to build sources:
```shell
./gradlew clean build
```
To start the application, run:
```shell
java -jar build/libs/apron-home-0.0.1-SNAPSHOT.jar
```
This should start the application listening on port `8080` of your local machine.

### API calls with cURL

#### Search for accounts for a given user
```shell
curl -v \
-H 'Accept: application/json' \
-H 'X-User-Id: 1' \
http://localhost:8080/apron-home/accounts
```
This should return a response like this:
```json
[
  {
    "id": 1,
    "userId": 1,
    "type": "CURRENT",
    "balance": 500.00,
    "currency": "GBP",
    "state": "ACTIVE"
  },
  {
    "id": 2,
    "userId": 1,
    "type": "SAVINGS",
    "balance": 100.00,
    "currency": "GBP",
    "state": "ACTIVE"
  }
]
```
#### Top-up an account for a given user with a certain amount and currency
```shell
curl -v \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
-H 'X-User-Id: 1' \
-H 'X-Idempotency-Id: 803b7cba-6eaf-454e-a146-ff3715dab095' \
-d '{ "amount" : "50.00", "currency" : "GBP" }' \
http://localhost:8080/apron-home/accounts/1/top-up
```
This should return a response like this:
```json
{
  "newBalance": 550.00,
  "currency": "GBP"
}
```
#### Fetch all invoices for a user
```shell
curl -v \
-H 'Accept: application/json' \
-H 'X-User-Id: 1' \
http://localhost:8080/apron-home/invoices
```
This should return a response like this:
```json
[
  {
    "id": 1,
    "userId": 1,
    "targetPaymentDetails": "77777777",
    "amount": 25.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 2,
    "userId": 1,
    "targetPaymentDetails": "77777778",
    "amount": 50.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 3,
    "userId": 1,
    "targetPaymentDetails": "77777713",
    "amount": 10.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 4,
    "userId": 1,
    "targetPaymentDetails": "77777780",
    "amount": 50.00,
    "currency": "GBP",
    "status": "PAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 5,
    "userId": 1,
    "targetPaymentDetails": "77777781",
    "amount": 15.00,
    "currency": "GBP",
    "status": "PAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 6,
    "userId": 1,
    "targetPaymentDetails": "77777713",
    "amount": 50.00,
    "currency": "GBP",
    "status": "DECLINED",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  }
]
```
#### Fetch invoices for a user by invoice status
```shell
curl -v \
-H 'Accept: application/json' \
-H 'X-User-Id: 1' \
http://localhost:8080/apron-home/invoices\?status\=UNPAID
```
This should return a response like this:
```json
[
  {
    "id": 1,
    "userId": 1,
    "targetPaymentDetails": "77777777",
    "amount": 25.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 2,
    "userId": 1,
    "targetPaymentDetails": "77777778",
    "amount": 50.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  },
  {
    "id": 3,
    "userId": 1,
    "targetPaymentDetails": "77777713",
    "amount": 10.00,
    "currency": "GBP",
    "status": "UNPAID",
    "vendorName": "Acme Services Ltd",
    "details": "For services rendered"
  }
]
```
#### Transfer one or more invoices, by invoice-id, for a user from a given account
```shell
curl -v \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
-H 'X-User-Id: 1' \
-H 'X-Idempotency-Id: 803b7cba-6eaf-454e-a146-ff3715dab095' \
-d '{ "invoiceIds" : [1, 2, 3], "accountId" : 1 }' \
http://localhost:8080/apron-home/invoices/transfer
```
This should return a response like this:
```json
[
  {
    "invoiceId": 1,
    "amount": 25.00,
    "currency": "GBP",
    "oldStatus": "UNPAID",
    "newStatus": "PAID",
    "message": "Payment succeeded"
  },
  {
    "invoiceId": 2,
    "amount": 50.00,
    "currency": "GBP",
    "oldStatus": "UNPAID",
    "newStatus": "PAID",
    "message": "Payment succeeded"
  },
  {
    "invoiceId": 3,
    "amount": 10.00,
    "currency": "GBP",
    "oldStatus": "UNPAID",
    "newStatus": "DECLINED",
    "message": "Payment declined: Unrecoverable error or retries exhausted"
  }
]
```

INSERT INTO users (id, name, created_at)
VALUES (1, 'User One', now());

INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, created_at)
VALUES (1, 1, 'CURRENT', 50000, 'GBP', 'ACTIVE', now());
INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, created_at)
VALUES (2, 1, 'SAVINGS', 10000, 'GBP', 'ACTIVE', now());

INSERT INTO transfer_idempotency_ids (id)
VALUES ('idempotency-id-1');
INSERT INTO transfer_idempotency_ids (id)
VALUES ('idempotency-id-2');
INSERT INTO transfer_idempotency_ids (id)
VALUES ('idempotency-id-3');

INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, created_at)
VALUES (1, 1, '77777777', 2500, 'GBP', 'UNPAID', now());
INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, created_at)
VALUES (2, 1, '77777778', 5000, 'GBP', 'UNPAID', now());
INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, created_at)
VALUES (3, 1, '77777713', 1000, 'GBP', 'UNPAID', now());
INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, transfer_idempotency_id, paid_by_account_id, created_at, last_updated)
VALUES (4, 1, '77777780', 5000, 'GBP', 'PAID', 'idempotency-id-1', 1, now(), now());
INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, transfer_idempotency_id, paid_by_account_id, created_at, last_updated)
VALUES (5, 1, '77777781', 1500, 'GBP', 'PAID', 'idempotency-id-2', 1, now(), now());
INSERT INTO invoices (id, user_id, target_payment_details, amount_in_minor_units, currency, status, transfer_idempotency_id, paid_by_account_id, created_at, last_updated)
VALUES (6, 1, '77777713', 5000, 'GBP', 'DECLINED', 'idempotency-id-3', 1, now(), now());

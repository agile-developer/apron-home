create table if not exists users
(
    id integer primary key,
    name varchar(100) not null,
    created_at timestamp not null,
    last_updated timestamp
);

create table if not exists accounts
(
    id integer primary key,
    user_id integer not null,
    type varchar(20) not null,
    balance_in_minor_units integer not null,
    currency char(3) not null,
    state varchar(20) not null,
    locked boolean not null default false,
    created_at timestamp not null,
    last_updated timestamp,
    constraint fk_account_user foreign key(user_id) references users(id)
);

create table if not exists top_ups
(
    id serial primary key,
    account_id integer not null,
    amount_in_minor_units integer not null,
    balance_at_top_up_in_minor_units integer not null,
    top_up_idempotency_id varchar(100) unique,
    created_at timestamp not null,
    constraint fk_top_up_account foreign key(account_id) references accounts(id)
);

create table if not exists transfer_idempotency_ids
(
    id varchar(100) primary key
);

create table if not exists invoices
(
    id integer primary key,
    user_id integer not null,
    target_payment_details varchar(100) not null,
    amount_in_minor_units integer not null,
    currency char(3) not null,
    status varchar(20) not null,
    vendor_name varchar(100) not null default 'Acme Services Ltd',
    details text default 'For services rendered',
    transfer_idempotency_id varchar(100),
    paid_by_account_id integer,
    created_at timestamp not null,
    last_updated timestamp,
    constraint fk_invoice_user foreign key(user_id) references users(id),
    constraint fk_invoice_account foreign key(paid_by_account_id) references accounts(id),
    constraint fk_invoice_transfer_idempotency foreign key(transfer_idempotency_id) references transfer_idempotency_ids(id)
);

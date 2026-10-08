-- Run this in Supabase: SQL Editor -> New query -> Run.
-- Recreates the full schema from scratch, including seed data. Do not
-- hand-edit tables in Supabase - always change this script and re-run it.

drop table if exists channel_order_items;
drop table if exists channel_orders;
drop table if exists channel_feed_cursor;
drop table if exists notifications;
drop table if exists supplier_orders;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id varchar(50) primary key,
    name varchar(150) not null,
    stock integer not null check (stock >= 0)
);

create table orders (
    order_id bigserial primary key,
    status varchar(20) not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason varchar(255),
    created_at timestamptz not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id bigint not null references orders (order_id) on delete cascade,
    product_id varchar(50) not null,
    quantity integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigserial primary key,
    message varchar(255) not null,
    created_at timestamptz not null default now()
);

create table supplier_orders (
    id bigserial primary key,
    product_id varchar(50) not null,
    buyer_ref varchar(40) not null unique,
    request_id varchar(80) not null,
    po_number varchar(50),
    cases integer not null check (cases > 0),
    units integer not null check (units > 0),
    status varchar(20) not null check (status in ('PENDING', 'SUBMITTED', 'PICKING', 'SHIPPED', 'DELIVERED', 'FAILED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Lab 4: Tiangge marketplace integration.

create table channel_feed_cursor (
    id smallint primary key,
    last_seq bigint not null default 0,
    updated_at timestamptz not null default now(),
    check (id = 1)
);

insert into channel_feed_cursor (id, last_seq) values (1, 0);

create table channel_orders (
    tiangge_order_id varchar(40) primary key,
    shop_order_id bigint,
    decision varchar(20) not null check (decision in ('PENDING', 'ACCEPTED', 'REJECTED', 'BACKORDERED', 'CANCELLED')),
    pending_action varchar(20) check (pending_action in ('DECISION', 'CANCELLATION', 'RESOLUTION')),
    synced boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table channel_order_items (
    channel_order_item_id bigserial primary key,
    tiangge_order_id varchar(40) not null references channel_orders (tiangge_order_id) on delete cascade,
    product_id varchar(50) not null,
    quantity integer not null check (quantity > 0)
);

insert into inventory (product_id, name, stock)
values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
set name = excluded.name,
    stock = excluded.stock;

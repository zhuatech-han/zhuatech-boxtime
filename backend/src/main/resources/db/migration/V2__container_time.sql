-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE customer(id bigint AUTO_INCREMENT PRIMARY KEY, name varchar(120) NOT NULL UNIQUE, department_id bigint NOT NULL, enabled boolean NOT NULL, FOREIGN KEY(department_id) REFERENCES department(id));
ALTER TABLE account ADD customer_id bigint NULL;
ALTER TABLE account ADD FOREIGN KEY(customer_id) REFERENCES customer(id);
CREATE TABLE agreement(
id bigint AUTO_INCREMENT PRIMARY KEY,
reference varchar(120) NOT NULL,
customer_id bigint NOT NULL,
department_id bigint NOT NULL,
carrier varchar(120) NOT NULL,
port varchar(120) NOT NULL,
container_type varchar(60) NOT NULL,
zone varchar(80) NOT NULL,
mode varchar(20) NOT NULL,
calendar varchar(20) NOT NULL,
include_start boolean NOT NULL,
include_end boolean NOT NULL,
holidays varchar(4000) NOT NULL,
valid_from date NOT NULL,
valid_to date NOT NULL,
free_demurrage int NOT NULL,
free_detention int NOT NULL,
free_combined int NOT NULL,
tier_days int NOT NULL,
demurrage_first decimal(14,2) NOT NULL,
demurrage_after decimal(14,2) NOT NULL,
detention_first decimal(14,2) NOT NULL,
detention_after decimal(14,2) NOT NULL,
combined_first decimal(14,2) NOT NULL,
combined_after decimal(14,2) NOT NULL,
status varchar(20) NOT NULL,
created_by bigint NOT NULL,
approved_by bigint NULL,
version bigint NOT NULL,
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(created_by) REFERENCES account(id),
FOREIGN KEY(approved_by) REFERENCES account(id),
UNIQUE(reference)
);
CREATE TABLE shipment(
id bigint AUTO_INCREMENT PRIMARY KEY,
reference varchar(120) NOT NULL,
bill_of_lading varchar(120) NOT NULL,
container_no varchar(11) NOT NULL,
customer_id bigint NOT NULL,
department_id bigint NOT NULL,
agreement_id bigint NOT NULL,
created_by bigint NOT NULL,
status varchar(20) NOT NULL,
version bigint NOT NULL,
arrival date NOT NULL,
gate_out date NULL,
empty_return date NULL,
agreement_snapshot longtext NOT NULL,
calculation longtext NOT NULL,
calculated_by bigint NULL,
reviewed_by bigint NULL,
bill_amount decimal(14,2) NULL,
bill_reference varchar(120) NULL,
bill_note varchar(1000) NULL,
bill_by bigint NULL,
acknowledgement varchar(1000) NULL,
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(agreement_id) REFERENCES agreement(id),
FOREIGN KEY(created_by) REFERENCES account(id),
FOREIGN KEY(calculated_by) REFERENCES account(id),
FOREIGN KEY(reviewed_by) REFERENCES account(id),
FOREIGN KEY(bill_by) REFERENCES account(id),
UNIQUE(reference)
);
CREATE TABLE free_extension(
id bigint AUTO_INCREMENT PRIMARY KEY,
shipment_id bigint NOT NULL,
phase varchar(20) NOT NULL,
days int NOT NULL,
proof varchar(1000) NOT NULL,
requested_by bigint NOT NULL,
reviewed_by bigint NULL,
status varchar(20) NOT NULL,
FOREIGN KEY(shipment_id) REFERENCES shipment(id),
FOREIGN KEY(reviewed_by) REFERENCES account(id),
FOREIGN KEY(requested_by) REFERENCES account(id)
);
CREATE TABLE payment(
id bigint AUTO_INCREMENT PRIMARY KEY,
shipment_id bigint NOT NULL,
amount decimal(14,2) NOT NULL,
reference varchar(120) NOT NULL,
paid_by bigint NOT NULL,
reversed_by bigint NULL,
reason varchar(1000) NULL,
status varchar(20) NOT NULL,
created_at timestamp(6) NOT NULL,
FOREIGN KEY(shipment_id) REFERENCES shipment(id),
FOREIGN KEY(paid_by) REFERENCES account(id),
FOREIGN KEY(reversed_by) REFERENCES account(id),
UNIQUE(reference),
CHECK(amount > 0)
);
CREATE TABLE command_record(id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(36) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint NOT NULL);
CREATE TABLE business_event(id bigint AUTO_INCREMENT PRIMARY KEY, object_type varchar(30) NOT NULL, object_id bigint NOT NULL, actor_id bigint NOT NULL, action varchar(60) NOT NULL, note varchar(1000) NOT NULL, snapshot longtext NOT NULL, created_at timestamp(6) NOT NULL, FOREIGN KEY(actor_id) REFERENCES account(id));
CREATE INDEX ix_shipment_scope ON shipment(department_id,customer_id,status);
CREATE INDEX ix_event_object ON business_event(object_type,object_id);
CREATE INDEX ix_payment_shipment ON payment(shipment_id);

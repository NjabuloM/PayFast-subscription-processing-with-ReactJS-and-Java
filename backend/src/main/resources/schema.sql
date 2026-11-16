DROP TABLE IF EXISTS MAGAZINE_STAND;
CREATE TABLE MAGAZINE_STAND (
    id INT AUTO_INCREMENT  PRIMARY KEY,
    name VARCHAR(250) NOT NULL,
    cover BLOB DEFAULT NULL,
    price DOUBLE NOT NULL,
    issue INT DEFAULT 0,
    sku VARCHAR(150) NOT NULL,
    month VARCHAR(50) NOT NULL,
    year INT NOT NULL
);

-- DROP TABLE IF EXISTS SUBSCRIPTION;
CREATE TABLE SUBSCRIPTION (
    id INT AUTO_INCREMENT  PRIMARY KEY,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    first_name VARCHAR(150),
    last_name VARCHAR(150),
    email_address VARCHAR(250) NOT NULL,
    reference VARCHAR(150),
    pf_token VARCHAR(150),
    payment_frequency VARCHAR(50),
    amount DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    magazine_id INT,
    FOREIGN KEY (magazine_id) REFERENCES MAGAZINE_STAND(id)
);

-- One row per genuine PayFast notification: the payment history of a subscription, and the way duplicates
-- (PayFast resending the same notification) are recognised.
CREATE TABLE PAYMENT_NOTIFICATION (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    pf_payment_id VARCHAR(50),
    payment_status VARCHAR(20) NOT NULL,
    amount_gross DECIMAL(10, 2),
    received_at TIMESTAMP NOT NULL,
    FOREIGN KEY (subscription_id) REFERENCES SUBSCRIPTION(id),
    CONSTRAINT uq_payment_notification UNIQUE (pf_payment_id, payment_status)
);
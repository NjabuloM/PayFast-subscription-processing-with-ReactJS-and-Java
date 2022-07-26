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
    magazine_id INT,
    FOREIGN KEY (magazine_id) REFERENCES MAGAZINE_STAND(id)
);
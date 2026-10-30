CREATE TABLE order_submissions (
    id UUID PRIMARY KEY,
    processor_code VARCHAR(20) NOT NULL CHECK (processor_code IN ('main', 'kz', 'az')),
    description VARCHAR(200) NOT NULL,
    customer_reference VARCHAR(100),
    internal_comment VARCHAR(500)
);

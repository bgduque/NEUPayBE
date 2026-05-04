INSERT INTO cash_in_locations (name, sublabel, kind) VALUES
    ('University Main Cashier', 'Ground Floor, Admin Building', 'MAIN_CASHIER'),
    ('Canteen Counter',         'Main Food Court Area',         'CANTEEN'),
    ('Library Kiosk',           '2nd Floor, Main Library',      'KIOSK')
ON CONFLICT DO NOTHING;

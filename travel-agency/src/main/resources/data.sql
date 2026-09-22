INSERT INTO users (id, username, password, role, email, phone_number, balance, active) VALUES
    -- ADMIN User
    ('f3e02ce0-365d-4c03-90a1-98f00cf6d3d1', 'admin',
     '$2b$12$kOeNIuRU5AxLDeugdgXkUeIFv.tMJVly0lTH.tiyEPY5AYNWqfLNS',
     'ADMIN', 'admin@gmail.com', null, 2000, true),

    -- USER User
    ('97e07604-ccda-4a87-afe5-260f0cd9f9fd', 'newuser',
     '$2b$12$h0i3pXRyg6cTGOA6.EWWhOhFEfX5pc49rjkwDncRyVtbl7Pp8OJ06',
     'USER', 'newuser@gmail.com', '123-456', 0.0, true),

    -- MANAGER User
    ('e1f3f5e0-6a3d-4d26-9b07-10ad848cfb2f', 'manager',
     '$2b$12$nyxCtZgAlq.s6hZFw.NkK.8gBPSPawFZPX2US.EiyNRZo6YWUppgK',
     'MANAGER', 'manager@gmail.com', '987-654-321', 1500.0, true);

INSERT INTO voucher (id, title, description, price, tour_type, transfer_type, hotel_type, status, arrival_date, eviction_date, hot, user_id) VALUES
    -- Health tour
    ('4ca55952-f6bd-4585-a386-57d8cfcb65d4', 'Hot Cruise',
    'A luxurious cruise to tropical islands. Includes all meals, entertainment, and excursions.',
    320.0, 'HEALTH', 'PLANE', 'FIVE_STARS', 'PAID', '2025-08-12', '2025-08-20', true, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Leisure tour
    ('9ac09156-5b02-4be2-bdbd-df7dbcd5d37a', 'City Break to Paris',
    'Visit the city of lights, Eiffel Tower, Louvre Museum, and other attractions in Paris.',
    220.0, 'LEISURE', 'SHIP', 'FOUR_STARS', 'REGISTERED', '2025-05-01', '2025-05-07', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Safari tour
    ('e0d4fe12-d8d9-4799-b2c9-42a2e4586b3b', 'Tropical Escape',
    'An all-inclusive vacation to a tropical beach resort, including water sports and excursions.',
    500.0, 'SAFARI', 'JEEPS', 'FIVE_STARS', 'PAID', '2025-06-10', '2025-06-17', true, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Wine tour
    ('cd5f9b79-b6cd-47b5-a1b1-736688660436', 'Shopping Tour in Milan',
    'Visit the top shopping destinations in Milan. Includes private shopping guide and exclusive discounts.',
    650.0, 'WINE', 'ELECTRICAL_CARS', 'FOUR_STARS', 'CANCELED', '2025-07-01', '2025-07-05', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Adventure tour
    ('a62f8507-e9d9-4738-b64d-daa2b2b8a416', 'Adventure Tour to Himalayas',
    'A thrilling adventure trek through the Himalayas with professional guides and full equipment.',
    750.0, 'ADVENTURE', 'PLANE', 'THREE_STARS', 'REGISTERED', '2025-10-01', '2025-10-14', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Health tour
    ('a1c1a4f1-12ad-4b19-9001-b0fc0aa12201', 'Wellness Retreat in Alps',
    'Relaxing 7-day retreat with spa treatments and yoga sessions.', 650.0,
    'HEALTH', 'PLANE', 'FIVE_STARS', 'REGISTERED', '2025-07-10', '2025-07-17', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Cultural tour
    ('a2b2b5f2-23bd-4c29-9111-c1fd1bb23302', 'Milan Fashion Shopping Tour',
    'Visit Milan and enjoy exclusive shopping deals with a personal stylist.', 850.0,
    'CULTURAL', 'BUS', 'THREE_STARS', 'REGISTERED', '2025-09-01', '2025-09-05', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Eco tour (hot)
    ('a3c3c6f3-34cd-4d39-9221-d2fe2cc34403', 'Beach Paradise in Maldives',
    'A luxury week in Maldives with oceanfront villas and private beach access.', 1200.0,
    'ECO', 'PLANE', 'FIVE_STARS', 'REGISTERED', '2025-06-15', '2025-06-22', true, '97e07604-ccda-4a87-afe5-260f0cd9f9fd'),

    -- Sports tour
    ('a4d4d7f4-45dd-4e49-9331-e3ff3dd45504', 'Historic Castles of Bavaria',
    'Explore the majestic castles of Germany on a guided bus tour.', 400.0,
    'SPORTS', 'BUS', 'FOUR_STARS', 'PAID', '2025-08-03', '2025-08-10', false, '97e07604-ccda-4a87-afe5-260f0cd9f9fd');

INSERT INTO author (first_name, last_name, bio)
VALUES
    ('Joshua',  'Bloch',   'Author of Effective Java, former Sun/Google engineer.'),
    ('Martin',  'Fowler',  'Chief Scientist at ThoughtWorks, author of Refactoring.'),
    ('Rod',     'Johnson', 'Creator of the Spring Framework.');

INSERT INTO book (title, isbn, author_id, published, price, description)
VALUES
    ('Effective Java',             '978-0134685991', 1, '2018-01-06', 49.99, 'Best practices for the Java programming language.'),
    ('Refactoring',                '978-0201485677', 2, '1999-07-08', 44.99, 'Improving the design of existing code.'),
    ('Patterns of Enterprise Application Architecture', '978-0321127426', 2, '2002-11-15', 54.99, 'Catalog of patterns for enterprise applications.'),
    ('J2EE Design and Development', '978-0764543852', 3, '2002-10-04', 39.99, 'Practical guide to J2EE design.');

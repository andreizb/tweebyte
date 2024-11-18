import csv
import uuid
from faker import Faker
import psycopg2
from datetime import datetime

# Initialize Faker
fake = Faker()

# Set up the number of entries
num_entries = 1000000

# Paths
uuid_file_path = 'uuids.csv'

# PostgreSQL connection details
db_config = {
    'dbname': 'user_service_db',
    'user': 'postgres',
    'password': 'postgres',
    'host': '192.168.1.219',
    'port': '54321'
}

# Establish database connection
conn = psycopg2.connect(**db_config)
cursor = conn.cursor()

# Open UUID CSV file for writing
with open(uuid_file_path, 'w', newline='') as uuidfile:
    uuid_writer = csv.writer(uuidfile)
    uuid_writer.writerow(['id'])  # Write header for UUID CSV

    for _ in range(num_entries):
        # Generate unique UUID, username, and email
        user_id = str(uuid.uuid4())  # Convert UUID to a string
        username = fake.unique.user_name()
        email = fake.unique.email()

        # Write UUID to CSV
        uuid_writer.writerow([user_id])

        # Insert data into the database
        cursor.execute('''
            INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ''', (
            user_id,
            username,
            email,
            fake.text(max_nb_chars=100).replace('\n', ' ').replace('\r', ' '),
            fake.password(length=10, special_chars=False, digits=True, upper_case=True, lower_case=True),
            False,  # is_private
            fake.date_of_birth(minimum_age=18, maximum_age=90),  # birth_date
            datetime.now()  # created_at
        ))

# Commit all inserts and close the connection
conn.commit()
cursor.close()
conn.close()

print(f"Database entries created, and UUIDs saved to {uuid_file_path}")
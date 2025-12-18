#!/usr/bin/env python3
import os
import sys
import uuid
import random
import time
from datetime import datetime, timezone

import psycopg2
from psycopg2.extras import execute_values

"""
Seed script for PostgreSQL to create only `follows` data.
- No `users` table, just generate UUIDs
- Insert N users, each following F others
"""

def parse_args():
    users = int(os.getenv("USERS", "10000"))
    fpu = int(os.getenv("FOLLOWS_PER_USER", "10"))
    args = sys.argv[1:]
    for i, a in enumerate(args):
        if a == "--users" and i + 1 < len(args):
            users = int(args[i + 1])
        if a == "--follows-per-user" and i + 1 < len(args):
            fpu = int(args[i + 1])
    return users, fpu

def connect():
    dsn = {
        "host": os.getenv("PGHOST", "localhost"),
        "port": int(os.getenv("PGPORT", "54323")),
        "dbname": os.getenv("PGDATABASE", "interaction_service_db"),
        "user": os.getenv("PGUSER", "postgres"),
        "password": os.getenv("PGPASSWORD", "postgres"),
    }
    return psycopg2.connect(**dsn)

def insert_follows(conn, user_ids, follows_per_user):
    print(f"Inserting follows for {len(user_ids)} users, {follows_per_user} each...")
    now = datetime.now(timezone.utc)
    seen = set()
    batch = []
    inserted_total = 0
    batch_size_rows = 10_000

    with conn.cursor() as cur:
        for follower in user_ids:
            choices = random.sample([u for u in user_ids if u != follower], follows_per_user)
            for followed in choices:
                pair = (follower, followed)
                if pair not in seen:
                    seen.add(pair)
                    batch.append((str(uuid.uuid4()), str(follower), str(followed), "ACCEPTED", now))

            if len(batch) >= batch_size_rows:
                execute_values(cur, """
                    insert into follows (id, follower_id, followed_id, status, created_at)
                    values %s
                """, batch, template="(%s, %s, %s, %s, %s)", page_size=5000)
                inserted_total += len(batch)
                batch.clear()

        if batch:
            execute_values(cur, """
                insert into follows (id, follower_id, followed_id, status, created_at)
                values %s
            """, batch, template="(%s, %s, %s, %s, %s)", page_size=5000)
            inserted_total += len(batch)

    conn.commit()
    print(f"Inserted {inserted_total:,} follow rows")

def main():
    users, fpu = parse_args()
    print(f"Generating {users} fake users with {fpu} follows each")
    t0 = time.time()
    conn = connect()
    try:
        user_ids = [uuid.uuid4() for _ in range(users)]
        insert_follows(conn, user_ids, fpu)
        print(f"Done in {time.time() - t0:.1f}s")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
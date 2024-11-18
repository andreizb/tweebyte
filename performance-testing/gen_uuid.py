import uuid
import csv

# Numele fișierului CSV
file_name = 'uuids.csv'

# Numărul de UUID-uri de generat
num_uuids = 1000000

# Funcție pentru a genera un UUID
def generate_uuid():
    return str(uuid.uuid4())

# Crearea fișierului CSV și scrierea UUID-urilor
with open(file_name, mode='w', newline='') as file:
    writer = csv.writer(file)
    writer.writerow(['UUID'])  # Scrie header-ul
    for _ in range(num_uuids):
        writer.writerow([generate_uuid()])

print(f"Fișierul {file_name} a fost creat cu succes și conține {num_uuids} UUID-uri.")

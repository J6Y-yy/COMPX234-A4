import socket
import base64
import os
import threading
import random
from typing import Tuple

DATA_PORT_MIN = 50000
DATA_PORT_MAX = 51000
MAX_PACKET_SIZE = 65535  # Maximum UDP packet size

def handle_file_transmission(filename: str, client_address: Tuple[str, int]):
    """Handle file transmission request for a single client"""
    # Select a random data port
    data_port = random.randint(DATA_PORT_MIN, DATA_PORT_MAX)
    print(f"Data thread started on port: {data_port}, File: {filename}")
    
    try:
        # Create data socket
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as data_sock:
            data_sock.bind(('0.0.0.0', data_port))
            data_sock.settimeout(1.0)  # Set timeout to allow thread exit
            
            # Open file
            try:
                file_size = os.path.getsize(filename)
                raf = open(filename, 'rb')
            except Exception as e:
                print(f"Error opening file: {e}")
                return
            
            while True:
                try:
                    # Receive client request
                    try:
                        data, _ = data_sock.recvfrom(MAX_PACKET_SIZE)
                    except socket.timeout:
                        continue  # Continue waiting for requests
                    
                    client_request = data.decode().strip()
                    print(f"Data thread received request: {client_request}")
                    
                    parts = client_request.split()
                    if len(parts) < 3 or parts[0] != "FILE" or parts[1] != filename:
                        print(f"Invalid file request: {client_request}")
                        continue
                    
                    # Handle close request
                    if parts[2] == "CLOSE":
                        close_response = f"FILE {filename} CLOSE_OK"
                        data_sock.sendto(close_response.encode(), client_address)
                        print("Sent close confirmation")
                        break
                    
                    # Handle data request
                    elif parts[2] == "GET":
                        try:
                            start = int(parts[4])
                            end = int(parts[6])
                            length = end - start + 1
                            
                            if start < 0 or end >= file_size or start > end:
                                print(f"Invalid byte range: {start} - {end}")
                                continue
                            
                            # Read file data
                            raf.seek(start)
                            file_data = raf.read(length)
                            if len(file_data) != length:
                                print(f"Error reading data, expected: {length}, actual: {len(file_data)}")
                                continue
                            
                            # Encode to Base64
                            base64_data = base64.b64encode(file_data).decode()
                            response = f"FILE {filename} OK START {start} END {end} DATA {base64_data}"
                            data_sock.sendto(response.encode(), client_address)
                            print(f"Sent data block: {start} - {end}")
                        
                        except (IndexError, ValueError) as e:
                            print(f"Error parsing request: {e}")
                            continue
                
                except Exception as e:
                    print(f"Error handling request: {e}")
                    break
            
            raf.close()
    
    except Exception as e:
        print(f"Data thread error: {e}")

def main():
    import sys
    if len(sys.argv) != 2:
        print("Usage: python3 udpserver.py <port>")
        sys.exit(1)
    
    server_port = int(sys.argv[1])
    
    # Create welcome socket
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as welcome_sock:
        welcome_sock.bind(('0.0.0.0', server_port))
        welcome_sock.settimeout(1.0)  # Set timeout to allow thread processing
        print(f"Server started, listening on port: {server_port}")
        
        while True:
            try:
                # Receive download request
                try:
                    data, client_address = welcome_sock.recvfrom(MAX_PACKET_SIZE)
                except socket.timeout:
                    continue  # Continue listening
                
                client_request = data.decode().strip()
                print(f"Received request: {client_request}")
                
                # Process download request
                parts = client_request.split()
                if len(parts) < 2 or parts[0] != "DOWNLOAD":
                    print(f"Invalid request: {client_request}")
                    continue
                
                filename = parts[1]
                file_path = filename
                
                # Check if file exists
                if not os.path.exists(file_path):
                    error_response = f"ERR {filename} NOT_FOUND"
                    welcome_sock.sendto(error_response.encode(), client_address)
                    print(f"File not found: {filename}")
                    continue
                
                # Create new thread to handle data transmission
                threading.Thread(
                    target=handle_file_transmission,
                    args=(filename, client_address),
                    daemon=True
                ).start()
                
                # Send OK response
                file_size = os.path.getsize(file_path)
                data_port = random.randint(DATA_PORT_MIN, DATA_PORT_MAX)
                ok_response = f"OK {filename} SIZE {file_size} PORT {data_port}"
                welcome_sock.sendto(ok_response.encode(), client_address)
                print(f"Sent OK response, File: {filename}, Size: {file_size}, Data Port: {data_port}")
            
            except Exception as e:
                print(f"Server error: {e}")

if __name__ == "__main__":
    main()    
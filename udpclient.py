import socket
import base64
import os
import threading
from typing import Optional

MAX_RETRIES = 5
INITIAL_TIMEOUT = 1.0  # Initial timeout in seconds
MAX_PACKET_SIZE = 65535  # Maximum UDP packet size

def send_and_receive(sock: socket.socket, server_address: tuple, message: str, timeout: float) -> Optional[str]:
    """Send a message and receive a response, handling timeout retries"""
    current_timeout = timeout
    for retries in range(MAX_RETRIES):
        try:
            sock.settimeout(current_timeout)
            sock.sendto(message.encode(), server_address)
            data, _ = sock.recvfrom(MAX_PACKET_SIZE)
            return data.decode().strip()
        except socket.timeout:
            print(f"Timeout, retrying {retries+1}/{MAX_RETRIES}, timeout: {current_timeout:.2f}s")
            current_timeout *= 2  # Double timeout
        except Exception as e:
            print(f"Send/receive error: {e}")
            return None
    print("Max retries reached, giving up")
    return None

def download_file(sock: socket.socket, server_address: tuple, filename: str):
    """Download a single file"""
    print(f"Starting download of file: {filename}")
    
    # Send DOWNLOAD request
    download_msg = f"DOWNLOAD {filename}"
    response = send_and_receive(sock, server_address, download_msg, INITIAL_TIMEOUT)
    
    if not response:
        print(f"Download failed: No response from server")
        return
    
    parts = response.split()
    if parts[0] == "ERR":
        print(f"File not found: {filename}")
        return
    elif parts[0] != "OK" or len(parts) < 6:
        print(f"Invalid response: {response}")
        return
    
    # Parse file size and data port
    file_size = int(parts[3])
    data_port = int(parts[5])
    data_server_address = (server_address[0], data_port)
    print(f"File size: {file_size} bytes, Data port: {data_port}")
    
    # Create file for writing
    with open(filename, 'wb') as f:
        total_received = 0
        progress = 0
        
        # Download file in chunks
        start = 0
        while total_received < file_size:
            end = min(start + 999, file_size - 1)
            file_request = f"FILE {filename} GET START {start} END {end}"
            response = send_and_receive(sock, data_server_address, file_request, INITIAL_TIMEOUT)
            
            if not response:
                print(f"Download failed: Could not retrieve data chunk")
                os.remove(filename)
                return
            
            parts = response.split()
            if parts[0] != "FILE" or parts[1] != filename or parts[2] != "OK":
                print(f"Invalid data response: {response}")
                continue
            
            # Parse data chunk
            data_index = parts.index("DATA") + 1
            base64_data = " ".join(parts[data_index:])
            try:
                file_data = base64.b64decode(base64_data)
                f.write(file_data)
                f.flush()
            except Exception as e:
                print(f"Error decoding data: {e}")
                continue
            
            total_received += len(file_data)
            start = end + 1
            
            # Show progress
            current_progress = int(total_received * 100.0 / file_size)
            if current_progress > progress:
                print(".", end="", flush=True)
                progress = current_progress
        
        print(f"\nFile download completed: {filename}")
        
        # Send close request
        close_msg = f"FILE {filename} CLOSE"
        response = send_and_receive(sock, data_server_address, close_msg, INITIAL_TIMEOUT)
        if response and response.startswith(f"FILE {filename} CLOSE_OK"):
            print("Close connection confirmation received")
        else:
            print("Close connection confirmation failed")

def main():
    import sys
    if len(sys.argv) != 4:
        print("Usage: python3 udpclient.py <hostname> <port> <file_list>")
        sys.exit(1)
    
    hostname = sys.argv[1]
    port = int(sys.argv[2])
    file_list_path = sys.argv[3]
    server_address = (hostname, port)
    
    # Create UDP socket
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        # Read file list
        try:
            with open(file_list_path, 'r') as f:
                filenames = [line.strip() for line in f if line.strip()]
        except Exception as e:
            print(f"Error reading file list: {e}")
            sys.exit(1)
        
        # Download each file
        for filename in filenames:
            download_file(sock, server_address, filename)

if __name__ == "__main__":
    main()    
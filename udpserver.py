import sys

def main():
    if len(sys.argv) != 2:
        print("Usage: python3 udpserver.py <port>")
        sys.exit(1)

    port = int(sys.argv[1])
    print(f"Server started on port: {port}")
 

if __name__ == "__main__":
    main()
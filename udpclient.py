import sys

def main():
    if len(sys.argv) != 4:
        print("Usage: python3 udpclient.py <hostname> <port> <file_list>")
        sys.exit(1)

    hostname = sys.argv[1]
    port = int(sys.argv[2])
    file_list_path = sys.argv[3]

    print(f"Client started with arguments: {hostname}, {port}, {file_list_path}")


if __name__ == "__main__":
    main()
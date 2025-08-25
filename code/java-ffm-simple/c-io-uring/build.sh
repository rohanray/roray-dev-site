#!/bin/bash
sudo apt-get update
sudo apt-get install liburing-dev
# gcc -O2 -Wall -Wextra -fPIC -shared -o libiouring_tcp.so io_uring_tcp_io.c -luring
gcc -shared -fPIC -o libiouring_tcp.so io_uring_tcp_sender.c -luring
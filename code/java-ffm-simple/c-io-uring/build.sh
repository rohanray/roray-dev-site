#!/bin/bash
sudo apt-get update
sudo apt-get install liburing-dev
gcc -shared -fPIC -o io_uring_tcp_sender.so io_uring_tcp_sender.c -luring
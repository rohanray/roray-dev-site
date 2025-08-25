// File: io_uring_tcp_sender.c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <liburing.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <errno.h>

#define PORT 12345
#define QUEUE_DEPTH 2
#define SEND_CHUNK (40 * 1024 * 1024) // 40 MB chunks

// Exposed function for FFM
int send_buffer_io_uring(const char* ip, int port, void* buffer, size_t length) {
    printf("Attempting to connect to %s:%d\n", ip, port);

    struct io_uring ring;
    int ret = io_uring_queue_init(QUEUE_DEPTH, &ring, 0);
    if (ret < 0) {
        printf("io_uring_queue_init failed: %s\n", strerror(-ret));
        return -1;
    }

    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        printf("socket creation failed: %s\n", strerror(errno));
        io_uring_queue_exit(&ring);
        return -1;
    }

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    if (inet_pton(AF_INET, ip, &serv_addr.sin_addr) <= 0) {
        printf("inet_pton failed for IP: %s\n", ip);
        close(sockfd);
        io_uring_queue_exit(&ring);
        return -1;
    }

    if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        printf("connect failed: %s (Make sure a server is listening on %s:%d)\n",
               strerror(errno), ip, port);
        close(sockfd);
        io_uring_queue_exit(&ring);
        return -1;
    }

    printf("Connected successfully, sending %zu bytes\n", length);

    size_t remaining = length;
    uint8_t *ptr = (uint8_t *)buffer;
    size_t total_sent = 0;

    while (remaining > 0) {
        size_t to_send = remaining > SEND_CHUNK ? SEND_CHUNK : remaining;

        struct io_uring_sqe* sqe = io_uring_get_sqe(&ring);
        if (!sqe) {
            // Queue full, submit and retry
            io_uring_submit(&ring);
            sqe = io_uring_get_sqe(&ring);
            if (!sqe) {
                fprintf(stderr, "Failed to get SQE\n");
                break;
            }
        }

        // MSG_NOSIGNAL to avoid SIGPIPE
        io_uring_prep_send(sqe, sockfd, ptr, to_send, MSG_NOSIGNAL);

        ret = io_uring_submit(&ring);
        if (ret < 0) {
            fprintf(stderr, "io_uring_submit failed: %s\n", strerror(-ret));
            break;
        }

        struct io_uring_cqe* cqe;
        ret = io_uring_wait_cqe(&ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "io_uring_wait_cqe failed: %s\n", strerror(-ret));
            break;
        }

        int sent_now = cqe->res;
        io_uring_cqe_seen(&ring, cqe);

        if (sent_now < 0) {
            fprintf(stderr, "send failed: %s\n", strerror(-sent_now));
            break;
        }

        if (sent_now == 0) {
            fprintf(stderr, "peer closed connection early\n");
            break;
        }

        ptr += sent_now;
        remaining -= sent_now;
        total_sent += sent_now;

        // Debug per-chunk
        printf("Sent %d bytes, %zu remaining\n", sent_now, remaining);
    }

    close(sockfd);
    io_uring_queue_exit(&ring);

    printf("Total bytes sent: %zu\n", total_sent);
    return (int)total_sent;
}


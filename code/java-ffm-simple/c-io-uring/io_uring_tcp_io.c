// File: io_uring_tcp_io.c
// Build example (Ubuntu):
//   gcc -O2 -Wall -Wextra -fPIC -shared -o libiouring_tcp.so io_uring_tcp_io.c -luring
//
// This module exposes a single, long-lived io_uring ring that is initialized once
// and reused for all network operations. Java (via FFM) should call
//   - io_uring_global_init(queueDepth)
//   - io_uring_connect(ip, port)  -> sockfd
//   - io_uring_send / io_uring_send_all / io_uring_recv / io_uring_recv_exact
//   - io_uring_close(sockfd)
//   - io_uring_global_shutdown()
// All functions are blocking and NOT thread-safe with each other.

#include <liburing.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

// -------- Global ring (single instance) --------
static struct io_uring g_ring;
static int g_ring_inited = 0;

// Internal helper: get an SQE, submitting if queue is full
static inline struct io_uring_sqe* get_sqe_retry() {
    struct io_uring_sqe* sqe = io_uring_get_sqe(&g_ring);
    if (!sqe) {
        // Submit outstanding SQEs to free space and try again
        int ret = io_uring_submit(&g_ring);
        if (ret < 0) {
            // submit error; caller will handle on next operations
            return NULL;
        }
        sqe = io_uring_get_sqe(&g_ring);
    }
    return sqe;
}

// -------- Lifecycle --------

int io_uring_global_init(unsigned queue_depth) {
    if (g_ring_inited) return 0;
    int ret = io_uring_queue_init(queue_depth, &g_ring, 0);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %s\n", strerror(-ret));
        return -1;
    }
    g_ring_inited = 1;
    return 0;
}

void io_uring_global_shutdown(void) {
    if (!g_ring_inited) return;
    io_uring_queue_exit(&g_ring);
    g_ring_inited = 0;
}

// -------- Connection management --------

int io_uring_connect(const char* ip, int port) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_connect: ring not initialized. Call io_uring_global_init first.\n");
        return -1;
    }

    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        fprintf(stderr, "socket() failed: %s\n", strerror(errno));
        return -1;
    }

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);

    if (inet_pton(AF_INET, ip, &serv_addr.sin_addr) <= 0) {
        fprintf(stderr, "inet_pton failed for %s\n", ip);
        close(sockfd);
        return -1;
    }

    if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        fprintf(stderr, "connect() failed: %s\n", strerror(errno));
        close(sockfd);
        return -1;
    }

    // Optionally: set TCP_NODELAY, keepalive, etc. here if desired.

    return sockfd;
}

void io_uring_close(int sockfd) {
    if (sockfd >= 0) close(sockfd);
}

// -------- I/O helpers --------

// Single-shot send via io_uring.
// Returns: bytes sent (>=0), or -errno on failure.
int io_uring_send(int sockfd, const void* buffer, size_t length) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_send: ring not initialized.\n");
        return -EINVAL;
    }
    if (length == 0) return 0;

    struct io_uring_sqe* sqe = get_sqe_retry();
    if (!sqe) return -EAGAIN;

    io_uring_prep_send(sqe, sockfd, buffer, length, MSG_NOSIGNAL);

    int ret = io_uring_submit(&g_ring);
    if (ret < 0) return ret;

    struct io_uring_cqe* cqe = NULL;
    ret = io_uring_wait_cqe(&g_ring, &cqe);
    if (ret < 0) return ret;

    int sent = cqe->res; // < 0 => -errno, 0 => peer closed
    io_uring_cqe_seen(&g_ring, cqe);
    return sent;
}

// Send-all helper: loops until 'length' bytes are sent or error occurs.
// Returns: total bytes sent (== length), or -errno on error.
int io_uring_send_all(int sockfd, const void* buffer, size_t length) {
    const unsigned char* p = (const unsigned char*)buffer;
    size_t remaining = length;
    size_t total = 0;

    while (remaining > 0) {
        int n = io_uring_send(sockfd, p, remaining);
        if (n < 0) return n;          // -errno
        if (n == 0) return -EPIPE;    // connection closed unexpectedly
        p += n;
        remaining -= (size_t)n;
        total += (size_t)n;
    }
    return (int)total;
}

// Single-shot recv via io_uring (like recv()).
// Returns: bytes received (>=0), 0 if peer closed, or -errno on failure.
int io_uring_recv(int sockfd, void* buffer, size_t length) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_recv: ring not initialized.\n");
        return -EINVAL;
    }
    if (length == 0) return 0;

    struct io_uring_sqe* sqe = get_sqe_retry();
    if (!sqe) return -EAGAIN;

    io_uring_prep_recv(sqe, sockfd, buffer, length, 0);

    int ret = io_uring_submit(&g_ring);
    if (ret < 0) return ret;

    struct io_uring_cqe* cqe = NULL;
    ret = io_uring_wait_cqe(&g_ring, &cqe);
    if (ret < 0) return ret;

    int recvd = cqe->res; // < 0 => -errno, 0 => peer closed
    io_uring_cqe_seen(&g_ring, cqe);
    return recvd;
}

// Receive exactly 'length' bytes (useful for fixed headers).
// Returns: total bytes received (== length), 0 if peer closed before complete, or -errno on error.
int io_uring_recv_exact(int sockfd, void* buffer, size_t length) {
    unsigned char* p = (unsigned char*)buffer;
    size_t remaining = length;
    size_t total = 0;

    while (remaining > 0) {
        int n = io_uring_recv(sockfd, p, remaining);
        if (n < 0) return n;   // -errno
        if (n == 0) return 0;  // peer closed before we got everything
        p += n;
        remaining -= (size_t)n;
        total += (size_t)n;
    }
    return (int)total;
}

// File: io_uring_tcp_io.c
// Build example (Ubuntu):
//   gcc -O2 -Wall -Wextra -fPIC -shared -o libiouring_tcp.so io_uring_tcp_io.c -luring

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

static inline struct io_uring_sqe* get_sqe_retry() {
    struct io_uring_sqe* sqe = io_uring_get_sqe(&g_ring);
    if (!sqe) {
        int ret = io_uring_submit(&g_ring);
        if (ret < 0) return NULL;
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

// -------- Connection management (client) --------
int io_uring_connect(const char* ip, int port) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_connect: ring not initialized\n");
        return -1;
    }

    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) return -1;

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    if (inet_pton(AF_INET, ip, &serv_addr.sin_addr) <= 0) {
        close(sockfd);
        return -1;
    }

    if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        close(sockfd);
        return -1;
    }
    return sockfd;
}

void io_uring_close(int sockfd) {
    if (sockfd >= 0) close(sockfd);
}

// -------- Connection management (server) --------
// Create a listening socket
int io_uring_listen(int port, int backlog) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_listen: ring not initialized\n");
        return -1;
    }

    int listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (listen_fd < 0) return -1;

    int opt = 1;
    setsockopt(listen_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);

    if (bind(listen_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        close(listen_fd);
        return -1;
    }

    if (listen(listen_fd, backlog) < 0) {
        close(listen_fd);
        return -1;
    }

    return listen_fd;
}

// Accept a client connection
int io_uring_accept(int listen_fd) {
    if (!g_ring_inited) {
        fprintf(stderr, "io_uring_accept: ring not initialized\n");
        return -1;
    }

    struct sockaddr_in client_addr;
    socklen_t addrlen = sizeof(client_addr);
    int client_fd = accept(listen_fd, (struct sockaddr*)&client_addr, &addrlen);
    if (client_fd < 0) return -1;
    return client_fd;
}

// -------- I/O helpers --------
int io_uring_send(int sockfd, const void* buffer, size_t length) {
    if (!g_ring_inited || length == 0) return -EINVAL;

    struct io_uring_sqe* sqe = get_sqe_retry();
    if (!sqe) return -EAGAIN;

    io_uring_prep_send(sqe, sockfd, buffer, length, MSG_NOSIGNAL);
    int ret = io_uring_submit(&g_ring);
    if (ret < 0) return ret;

    struct io_uring_cqe* cqe = NULL;
    ret = io_uring_wait_cqe(&g_ring, &cqe);
    if (ret < 0) return ret;

    int sent = cqe->res;
    io_uring_cqe_seen(&g_ring, cqe);
    return sent;
}

int io_uring_send_all(int sockfd, const void* buffer, size_t length) {
    const unsigned char* p = (const unsigned char*)buffer;
    size_t remaining = length;
    size_t total = 0;

    while (remaining > 0) {
        int n = io_uring_send(sockfd, p, remaining);
        if (n <= 0) return (n < 0) ? n : -EPIPE;
        p += n;
        remaining -= (size_t)n;
        total += (size_t)n;
    }
    return (int)total;
}

int io_uring_recv(int sockfd, void* buffer, size_t length) {
    if (!g_ring_inited || length == 0) return -EINVAL;

    struct io_uring_sqe* sqe = get_sqe_retry();
    if (!sqe) return -EAGAIN;

    io_uring_prep_recv(sqe, sockfd, buffer, length, 0);
    int ret = io_uring_submit(&g_ring);
    if (ret < 0) return ret;

    struct io_uring_cqe* cqe = NULL;
    ret = io_uring_wait_cqe(&g_ring, &cqe);
    if (ret < 0) return ret;

    int recvd = cqe->res;
    io_uring_cqe_seen(&g_ring, cqe);
    return recvd;
}

int io_uring_recv_exact(int sockfd, void* buffer, size_t length) {
    unsigned char* p = (unsigned char*)buffer;
    size_t remaining = length;
    size_t total = 0;

    while (remaining > 0) {
        int n = io_uring_recv(sockfd, p, remaining);
        if (n < 0) return n;
        if (n == 0) return 0;
        p += n;
        remaining -= (size_t)n;
        total += (size_t)n;
    }
    return (int)total;
}

// File: io_uring_tcp_io.c
#include <liburing.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

#define QUEUE_DEPTH 2

int io_uring_send(int sockfd, void* buffer, size_t length) {
    struct io_uring ring;
    int ret = io_uring_queue_init(QUEUE_DEPTH, &ring, 0);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %s\n", strerror(-ret));
        return -1;
    }

    struct io_uring_sqe* sqe = io_uring_get_sqe(&ring);
    if (!sqe) {
        fprintf(stderr, "Failed to get SQE\n");
        io_uring_queue_exit(&ring);
        return -1;
    }

    io_uring_prep_send(sqe, sockfd, buffer, length, MSG_NOSIGNAL);

    ret = io_uring_submit(&ring);
    if (ret < 0) {
        fprintf(stderr, "io_uring_submit failed: %s\n", strerror(-ret));
        io_uring_queue_exit(&ring);
        return -1;
    }

    struct io_uring_cqe* cqe;
    ret = io_uring_wait_cqe(&ring, &cqe);
    if (ret < 0) {
        fprintf(stderr, "io_uring_wait_cqe failed: %s\n", strerror(-ret));
        io_uring_queue_exit(&ring);
        return -1;
    }

    int sent = cqe->res;
    io_uring_cqe_seen(&ring, cqe);
    io_uring_queue_exit(&ring);

    return sent;
}

int io_uring_recv(int sockfd, void* buffer, size_t length) {
    struct io_uring ring;
    int ret = io_uring_queue_init(QUEUE_DEPTH, &ring, 0);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %s\n", strerror(-ret));
        return -1;
    }

    struct io_uring_sqe* sqe = io_uring_get_sqe(&ring);
    if (!sqe) {
        fprintf(stderr, "Failed to get SQE\n");
        io_uring_queue_exit(&ring);
        return -1;
    }

    io_uring_prep_recv(sqe, sockfd, buffer, length, 0);

    ret = io_uring_submit(&ring);
    if (ret < 0) {
        fprintf(stderr, "io_uring_submit failed: %s\n", strerror(-ret));
        io_uring_queue_exit(&ring);
        return -1;
    }

    struct io_uring_cqe* cqe;
    ret = io_uring_wait_cqe(&ring, &cqe);
    if (ret < 0) {
        fprintf(stderr, "io_uring_wait_cqe failed: %s\n", strerror(-ret));
        io_uring_queue_exit(&ring);
        return -1;
    }

    int recvd = cqe->res;
    io_uring_cqe_seen(&ring, cqe);
    io_uring_queue_exit(&ring);

    return recvd;
}

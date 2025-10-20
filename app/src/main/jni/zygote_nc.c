#define _POSIX_C_SOURCE 200112L
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/stat.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int connect_host(const char *host, const char *port, int *out_fd) {
    struct addrinfo hints, *res, *rp;
    int sfd = -1;
    int r;

    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;      // IPv4 or IPv6
    hints.ai_socktype = SOCK_STREAM;

    r = getaddrinfo(host, port, &hints, &res);
    if (r != 0) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(r));
        return -1;
    }

    for (rp = res; rp != NULL; rp = rp->ai_next) {
        sfd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (sfd < 0) continue;
        if (connect(sfd, rp->ai_addr, rp->ai_addrlen) == 0) {
            // connected
            *out_fd = sfd;
            freeaddrinfo(res);
            return 0;
        }
        close(sfd);
        sfd = -1;
    }

    freeaddrinfo(res);
    perror("connect");
    return -1;
}

static int set_nonblock(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags == -1) return -1;
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

static void one_shot_mode(int sockfd) {
    // Read all stdin and send to socket, then close and exit immediately.
    const size_t BUF_SZ = 4096;
    char buf[BUF_SZ];
    ssize_t n;
    // read until EOF
    while ((n = read(STDIN_FILENO, buf, BUF_SZ)) > 0) {
        ssize_t sent = 0;
        while (sent < n) {
            ssize_t w = write(sockfd, buf + sent, n - sent);
            if (w < 0) {
                if (errno == EINTR) continue;
                perror("write");
                close(sockfd);
                return;
            }
            sent += w;
        }
    }
    // do not wait for response — close socket and return
    shutdown(sockfd, SHUT_RDWR);
    close(sockfd);
}

static void interactive_mode(int sockfd) {
    struct pollfd fds[2];
    const int FD_STDIN = 0;
    const int FD_SOCK = 1;
    const size_t BUF_SZ = 4096;
    char buf[BUF_SZ];

    // make non-blocking to simplify shutdown handling
    set_nonblock(STDIN_FILENO);
    set_nonblock(sockfd);

    fds[FD_STDIN].fd = STDIN_FILENO;
    fds[FD_STDIN].events = POLLIN;
    fds[FD_SOCK].fd = sockfd;
    fds[FD_SOCK].events = POLLIN;

    int want_stdin = 1;
    int want_sock = 1;

    while (want_stdin || want_sock) {
        int timeout = -1; // wait indefinitely
        int ret = poll(fds, 2, timeout);
        if (ret < 0) {
            if (errno == EINTR) continue;
            perror("poll");
            break;
        }
        // from stdin -> socket
        if (want_stdin && (fds[FD_STDIN].revents & POLLIN)) {
            ssize_t n = read(STDIN_FILENO, buf, BUF_SZ);
            if (n < 0) {
                if (errno == EINTR) continue;
                perror("read stdin");
                want_stdin = 0;
            } else if (n == 0) {
                // EOF on stdin, signal to remote we've finished sending
                shutdown(sockfd, SHUT_WR);
                want_stdin = 0;
                // stop listening to stdin events
                fds[FD_STDIN].events = 0;
            } else {
                ssize_t sent = 0;
                while (sent < n) {
                    ssize_t w = write(sockfd, buf + sent, n - sent);
                    if (w < 0) {
                        if (errno == EINTR) continue;
                        if (errno == EWOULDBLOCK || errno == EAGAIN) {
                            // socket temporarily unavailable; break to poll again
                            break;
                        }
                        perror("write sock");
                        want_sock = 0;
                        break;
                    }
                    sent += w;
                }
            }
        }
        // from socket -> stdout
        if (want_sock && (fds[FD_SOCK].revents & (POLLIN | POLLHUP | POLLERR))) {
            ssize_t n = read(sockfd, buf, BUF_SZ);
            if (n < 0) {
                if (errno == EINTR) continue;
                if (errno == EWOULDBLOCK || errno == EAGAIN) {
                    // nothing now
                } else {
                    perror("read sock");
                    want_sock = 0;
                }
            } else if (n == 0) {
                // remote closed
                want_sock = 0;
            } else {
                ssize_t wrote = 0;
                while (wrote < n) {
                    ssize_t w = write(STDOUT_FILENO, buf + wrote, n - wrote);
                    if (w < 0) {
                        if (errno == EINTR) continue;
                        perror("write stdout");
                        want_sock = 0;
                        break;
                    }
                    wrote += w;
                }
            }
        }
        // handle remote hangups/errors
        if (fds[FD_SOCK].revents & (POLLHUP | POLLERR)) {
            continue;
        }
    }

    close(sockfd);
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "Usage: %s <host> <port>\n", argv[0]);
        return 1;
    }
    const char *host = argv[1];
    const char *port = argv[2];
    int sockfd;

    // disable stdout buffering so command substitution behaves predictably
    setvbuf(stdout, NULL, _IONBF, 0);

    if (connect_host(host, port, &sockfd) != 0) {
        return 2;
    }

    // Decide mode: if stdin is a terminal -> interactive; else -> one-shot send & exit
    if (!isatty(STDIN_FILENO)) {
        // piped/redirected input: read stdin, send everything, and exit immediately
        one_shot_mode(sockfd);
    } else {
        // interactive: relay between stdin and socket
        interactive_mode(sockfd);
    }

    return 0;
}
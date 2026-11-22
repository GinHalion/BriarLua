#include <jni.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <sys/ucontext.h>

#define BCRASH_MAX_FRAMES 48

static char g_crash_dir[512] = {0};
static char g_activity[256] = {0};
static char g_log_path[768] = {0};
static struct sigaction g_old_sa;
static volatile sig_atomic_t g_installed = 0;
static volatile sig_atomic_t g_in_handler = 0;

static void bcrash_write_str(int fd, const char *s) {
    if (s == NULL) return;
    size_t len = strlen(s);
    while (len > 0) {
        ssize_t n = write(fd, s, len);
        if (n <= 0) break;
        s += n;
        len -= (size_t) n;
    }
}

#if defined(__aarch64__)
static void bcrash_dump_context(int fd, ucontext_t *uc) {
    uint64_t pc = uc->uc_mcontext.pc;
    uint64_t sp = uc->uc_mcontext.sp;
    uint64_t lr = uc->uc_mcontext.regs[30];
    uint64_t fp = uc->uc_mcontext.regs[29];
    char buf[256];
    dprintf(fd, "arch: arm64\npc:  0x%llx\nlr:  0x%llx\nsp:  0x%llx\n",
            (unsigned long long) pc, (unsigned long long) lr,
            (unsigned long long) sp);
    dprintf(fd, "backtrace (frame-pointer chain):\n");
    int frames = 0;
    while (fp != 0 && (fp & 0x7) == 0 && frames < BCRASH_MAX_FRAMES) {
        uint64_t *frame = (uint64_t *) fp;
        uint64_t next_fp = frame[0];
        uint64_t ret_addr = frame[1];
        snprintf(buf, sizeof(buf), "  #%02d  pc 0x%016llx\n", frames++,
                 (unsigned long long) ret_addr);
        bcrash_write_str(fd, buf);
        if (next_fp <= fp) break;
        fp = next_fp;
    }
}
#elif defined(__arm__)
static void bcrash_dump_context(int fd, ucontext_t *uc) {
    uint32_t pc = uc->uc_mcontext.arm_pc;
    uint32_t sp = uc->uc_mcontext.arm_sp;
    uint32_t lr = uc->uc_mcontext.arm_lr;
    dprintf(fd, "arch: arm\npc:  0x%x\nlr:  0x%x\nsp:  0x%x\n", pc, lr, sp);
    dprintf(fd, "backtrace: arm32 frame pointer unavailable, pc/lr only\n");
}
#else
static void bcrash_dump_context(int fd, ucontext_t *uc) {
    (void) fd;
    (void) uc;
    bcrash_write_str(fd, "arch: unsupported\n");
}
#endif

static void bcrash_handler(int sig, siginfo_t *info, void *ctx) {
    if (g_in_handler) {
        return;
    }
    g_in_handler = 1;

    if (g_installed) {
        long ts = (long) time(NULL);
        snprintf(g_log_path, sizeof(g_log_path), "%s/native_%ld.log",
                 g_crash_dir, ts);
        int fd = open(g_log_path, O_CREAT | O_WRONLY | O_TRUNC, 0600);
        if (fd >= 0) {
            dprintf(fd, "BriarLua native crash report\n");
            dprintf(fd, "signal: %d (%s)\n", sig, strsignal(sig));
            dprintf(fd, "code:   %d\n", info != NULL ? info->si_code : 0);
            dprintf(fd, "addr:   0x%llx\n",
                    info != NULL ? (unsigned long long) (uintptr_t) info->si_addr : 0ULL);
            dprintf(fd, "pid:    %d\ntid:    %ld\n", (int) getpid(),
                    (long) syscall(SYS_gettid));
            dprintf(fd, "time:   %ld\n\n", ts);
            bcrash_dump_context(fd, (ucontext_t *) ctx);
            dprintf(fd, "\nnote: full symbolized stack trace in system tombstone and logcat.\n");
            close(fd);
        }

        pid_t pid = fork();
        if (pid == 0) {
            struct timespec ts_child;
            ts_child.tv_sec = 0;
            ts_child.tv_nsec = 400 * 1000 * 1000L;
            nanosleep(&ts_child, NULL);
            execl("/system/bin/am", "am", "start",
                  "-n", g_activity,
                  "-f", "0x10000000",
                  "--es", "EXTRA_NATIVE_CRASH_FILE", g_log_path,
                  "--es", "EXTRA_EXCEPTION_TYPE", "Native crash (SIGABRT)",
                  "--es", "EXTRA_THREAD_INFO", "native",
                  (char *) NULL);
            _exit(127);
        }

        sigaction(sig, &g_old_sa, NULL);
    }

    {
        sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, sig);
        sigprocmask(SIG_UNBLOCK, &set, NULL);
    }
    raise(sig);

    signal(sig, SIG_DFL);
    raise(sig);
    _exit(1);
}

JNIEXPORT void JNICALL
Java_com_briarlua_NativeCrashHandler_init(JNIEnv *env, jclass clz,
                                          jstring dir, jstring activity) {
    const char *cdir = NULL;
    const char *cact = NULL;
    struct sigaction sa;

    (void) clz;
    if (dir == NULL || activity == NULL) return;

    cdir = (*env)->GetStringUTFChars(env, dir, NULL);
    cact = (*env)->GetStringUTFChars(env, activity, NULL);
    if (cdir == NULL || cact == NULL) {
        if (cdir != NULL) (*env)->ReleaseStringUTFChars(env, dir, cdir);
        if (cact != NULL) (*env)->ReleaseStringUTFChars(env, activity, cact);
        return;
    }
    strncpy(g_crash_dir, cdir, sizeof(g_crash_dir) - 1);
    strncpy(g_activity, cact, sizeof(g_activity) - 1);
    (*env)->ReleaseStringUTFChars(env, dir, cdir);
    (*env)->ReleaseStringUTFChars(env, activity, cact);

    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = bcrash_handler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    sigemptyset(&sa.sa_mask);
    if (sigaction(SIGABRT, &sa, &g_old_sa) == 0) {
        g_installed = 1;
    }
}

#include <am.h>
#include <klib-macros.h>
#include <riscv/riscv.h>

#include "mmio.h"

#define npc_trap(code) asm volatile("mv a0, %0; ebreak" : : "r"(code))

int main(const char *args);

extern char _heap_start;
extern char _pmem_start;
#define PMEM_SIZE (16 * 1024 * 1024)
#define PMEM_END ((uintptr_t)&_pmem_start + PMEM_SIZE)

Area heap = RANGE(&_heap_start, PMEM_END);
static const char mainargs[MAINARGS_MAX_LEN] =
        TOSTRING(MAINARGS_PLACEHOLDER);

void putch(char ch)
{
        outb(UART_BASE, ch);
}

void halt(int code)
{
        npc_trap(code);

        // should not reach here
        while (1)
                ;
}

void _trm_init()
{
        int ret = main(mainargs);
        halt(ret);
}

/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 * PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 * KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <isa.h>

#ifdef CONFIG_ETRACE
// RISC-V异常代码定义
#define CAUSE_MISALIGNED_FETCH 0
#define CAUSE_FETCH_ACCESS 1
#define CAUSE_ILLEGAL_INSTRUCTION 2
#define CAUSE_BREAKPOINT 3
#define CAUSE_MISALIGNED_LOAD 4
#define CAUSE_LOAD_ACCESS 5
#define CAUSE_MISALIGNED_STORE 6
#define CAUSE_STORE_ACCESS 7
#define CAUSE_USER_ECALL 8
#define CAUSE_SUPERVISOR_ECALL 9
#define CAUSE_MACHINE_ECALL 11
#define CAUSE_FETCH_PAGE_FAULT 12
#define CAUSE_LOAD_PAGE_FAULT 13
#define CAUSE_STORE_PAGE_FAULT 15

static void etrace(word_t NO, vaddr_t epc)
{
        switch (NO) {
        case CAUSE_MISALIGNED_FETCH: {
                Log("[ETRACE] MISALIGNED_FETCH -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_FETCH_ACCESS: {
                Log("[ETRACE] FETCH_ACCESS -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_ILLEGAL_INSTRUCTION: {
                Log("[ETRACE] ILLEGAL_INSTRUCTION -> pc = 0x%08x mcause: "
                    "0x%08x, mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_BREAKPOINT: {
                Log("[ETRACE] BREAKPOINT -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_MISALIGNED_LOAD: {
                Log("[ETRACE] MISALIGNED_LOAD -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_LOAD_ACCESS: {
                Log("[ETRACE] LOAD_ACCESS -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_MISALIGNED_STORE: {
                Log("[ETRACE] MISALIGNED_STORE -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_STORE_ACCESS: {
                Log("[ETRACE] STORE_ACCESS -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_USER_ECALL: {
                Log("[ETRACE] USER_ECALL -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_SUPERVISOR_ECALL: {
                Log("[ETRACE] SUPERVISOR_ECALL -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_MACHINE_ECALL: {
                Log("[ETRACE] MACHINE_ECALL -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_FETCH_PAGE_FAULT: {
                Log("[ETRACE] FETCH_PAGE_FAULT -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_LOAD_PAGE_FAULT: {
                Log("[ETRACE] LOAD_PAGE_FAULT -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        case CAUSE_STORE_PAGE_FAULT: {
                Log("[ETRACE] STORE_PAGE_FAULT -> pc = 0x%08x mcause: 0x%08x, "
                    "mstatus: 0x%08x, mtvec: 0x%08x, mepc: 0x%08x",
                    epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        default: {
                Log("[ETRACE] UNSUPPORTED EXCEPTION NO: %d -> pc = 0x%08x "
                    "mcause: 0x%08x, mstatus: 0x%08x, mtvec: 0x%08x, mepc: "
                    "0x%08x",
                    NO, epc, csr_read(CSR_MCAUSE), csr_read(CSR_MSTATUS),
                    csr_read(CSR_MTVEC), csr_read(CSR_MEPC));
                break;
        }
        }
}
#endif

word_t isa_raise_intr(word_t NO, vaddr_t epc)
{
        cpu.csr.mcause = NO;
        cpu.csr.mepc = epc;
        IFDEF(CONFIG_ETRACE, etrace(NO, epc));
        word_t mstatus = cpu.csr.mstatus;
        word_t mie = (mstatus >> 3) & 1;
        mstatus = (mstatus & ~(1 << 7)) | (mie << 7);
        mstatus &= ~(1 << 3);
        cpu.csr.mstatus = mstatus;
        return cpu.csr.mtvec;
}

word_t isa_query_intr()
{
        return INTR_EMPTY;
}

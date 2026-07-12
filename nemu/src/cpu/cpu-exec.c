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

#include <cpu/cpu.h>
#include <cpu/decode.h>
#include <cpu/difftest.h>
#include <locale.h>
#include <stdio.h>

/* The assembly code of instructions executed is only output to the screen
 * when the number of instructions executed is less than this value.
 * This is useful when you use the `si' command.
 * You can modify this value as you want.
 */
#define MAX_INST_TO_PRINT 10

CPU_state cpu = {};
uint64_t g_nr_guest_inst = 0;
static uint64_t g_timer = 0; // unit: us
static bool g_print_step = false;

void device_update();

#ifdef CONFIG_ITRACE

char iringbuf[IRINGBUF_SIZE][MAX_IRINGLOG_LEN] = {};
int iringbuf_head = 0;
int iringbuf_count = 0;

static void iringbuf_write(Decode *s)
{
        if (s->logbuf[0] == '\0')
                return;
        snprintf(iringbuf[iringbuf_head], sizeof(iringbuf[0]), "%s", s->logbuf);
        iringbuf_head = (iringbuf_head + 1) % IRINGBUF_SIZE;
        if (iringbuf_count < IRINGBUF_SIZE)
                iringbuf_count++;
}

static void iringbuf_show()
{
        Log("%s\n", iringbuf[iringbuf_head - 1]);
        int start = (iringbuf_head - iringbuf_count + 64) % 64;
        for (int i = 0; i < iringbuf_count; i++) {
                int idx = (start + i) % 64;
                Log("%s\n", iringbuf[idx]);
        }
}
#endif

#ifdef CONFIG_FTRACE

#define CALL_FUNC_TIMES 20

typedef struct {
        vaddr_t pc;
        const char *name;
} CallFrame;

static CallFrame call_stack[CALL_FUNC_TIMES];
static int call_stack_depth = 0;

static inline bool ftrace_ready(void)
{
        return elf.ehdr && elf.shdr && elf.symtab && elf.strtab;
}

static const char *get_symbol_name(vaddr_t pc)
{
        for (int i = 0; i < elf.symtab_num; i++) {
                Elf32_Sym *sym = &elf.symtab[i];

                if (ELF32_ST_TYPE(sym->st_info) != STT_FUNC)
                        continue;

                if (pc >= sym->st_value && pc < sym->st_value + sym->st_size)
                        return elf.strtab + sym->st_name;
        }
        return NULL;
}

static void ftrace_elf_start(vaddr_t pc, vaddr_t dnpc, bool is_call,
                             bool is_ret)
{
        if (!ftrace_ready())
                return;

        if (is_call) {
                const char *target = get_symbol_name(dnpc);

                if (target == NULL)
                        return;

                if (call_stack_depth > 0 &&
                    strcmp(call_stack[call_stack_depth - 1].name, target) == 0)
                        return;

                for (int i = 0; i < call_stack_depth; i++)
                        printf(" ");

                printf("Call: %s at " FMT_WORD "\n", target, dnpc);

                if (call_stack_depth < CALL_FUNC_TIMES) {
                        call_stack[call_stack_depth].pc = dnpc;
                        call_stack[call_stack_depth].name = target;
                        call_stack_depth++;
                } else
                        printf("Warning: Call stack overflow.\n");

                return;
        }

        if (is_ret) {
                if (call_stack_depth == 0) {
                        return;
                }

                const char *current = get_symbol_name(pc);

                call_stack_depth--;

                for (int i = 0; i < call_stack_depth; i++)
                        printf(" ");

                if (current &&
                    strcmp(call_stack[call_stack_depth - 1].name, current) == 0)
                        printf("return: %s at " FMT_WORD "\n", current, pc);
                else
                        printf("return: %s at " FMT_WORD "\n",
                               call_stack[call_stack_depth].name, pc);
        }
}

static inline bool is_call_inst(uint32_t inst)
{
        uint32_t opcode = inst & 0x7f;

        if (opcode != 0x6f && opcode != 0x67)
                return false;

        return ((inst >> 7) & 0x1f) != 0;
}

static inline bool is_ret_inst(uint32_t inst)
{
        return ((inst & 0x7f) == 0x67) && (((inst >> 7) & 0x1f) == 0) &&
               (((inst >> 15) & 0x1f) == 1);
}

static void ftrace(vaddr_t pc, const Decode *s)
{
        bool is_call = is_call_inst(s->isa.inst);
        bool is_ret = is_ret_inst(s->isa.inst);
        ftrace_elf_start(pc, s->dnpc, is_call, is_ret);
}

#endif

#ifdef CONFIG_DTRACE
#define INRANGE(pc, start, end) ((pc) >= (start) && (pc) < (end))

static void dtrace(vaddr_t pc)
{
        if (nemu_state.state != NEMU_RUNNING)
                return;

        if (INRANGE(pc, CONFIG_SERIAL_MMIO, CONFIG_SERIAL_MMIO + 8)) {
                printf("dtrace: serial port access at " FMT_WORD, pc);
        } else if (INRANGE(pc, CONFIG_DISK_CTL_MMIO,
                           CONFIG_DISK_CTL_MMIO + 8)) {
                printf("dtrace: disk control port access at " FMT_WORD, pc);
        } else if (INRANGE(pc, CONFIG_VGA_CTL_MMIO, CONFIG_VGA_CTL_MMIO + 8)) {
                printf("dtrace: VGA control port access at " FMT_WORD, pc);
        } else if (INRANGE(pc, CONFIG_I8042_DATA_MMIO,
                           CONFIG_I8042_DATA_MMIO + 8)) {
                printf("dtrace: i8042 data port access at " FMT_WORD, pc);
        } else if (INRANGE(pc, CONFIG_AUDIO_CTL_MMIO,
                           CONFIG_AUDIO_CTL_MMIO + 8)) {
                printf("dtrace: audio control port access at " FMT_WORD, pc);
        } else if (INRANGE(pc, CONFIG_RTC_MMIO, CONFIG_RTC_MMIO + 8)) {
                printf("dtrace: RTC port access at " FMT_WORD, pc);
        } else {
        }
}

#endif

#ifdef CONFIG_WATCHPOINT
extern int update_wp();
static void check_wp_updated()
{
        if (update_wp() > 0)
                nemu_state.state = NEMU_STOP;
}
#endif

static void trace_and_difftest(Decode *_this, vaddr_t dnpc)
{
#ifdef CONFIG_ITRACE_COND
        if (ITRACE_COND) {
                log_write("%s\n", _this->logbuf);
                IFDEF(CONFIG_ITRACE, iringbuf_write(_this));
        }
#endif
        if (g_print_step) {
                IFDEF(CONFIG_ITRACE, puts(_this->logbuf));
        }
        IFDEF(CONFIG_DIFFTEST, difftest_step(_this->pc, dnpc));
        IFDEF(CONFIG_WATCHPOINT, check_wp_updated());
}

static void exec_once(Decode *s, vaddr_t pc)
{
        s->pc = pc;
        s->snpc = pc;
        isa_exec_once(s);
        IFDEF(CONFIG_DTRACE, dtrace(pc));
        IFDEF(CONFIG_FTRACE, ftrace(pc, s));
        cpu.pc = s->dnpc;
#ifdef CONFIG_ITRACE
        char *p = s->logbuf;
        p += snprintf(p, sizeof(s->logbuf), FMT_WORD ":", s->pc);
        int ilen = s->snpc - s->pc;
        int i;
        uint8_t *inst = (uint8_t *)&s->isa.inst;
#ifdef CONFIG_ISA_x86
        for (i = 0; i < ilen; i++) {
#else
        for (i = ilen - 1; i >= 0; i--) {
#endif
                p += snprintf(p, 4, " %02x", inst[i]);
        }
        int ilen_max = MUXDEF(CONFIG_ISA_x86, 8, 4);
        int space_len = ilen_max - ilen;
        if (space_len < 0)
                space_len = 0;
        space_len = space_len * 3 + 1;
        memset(p, ' ', space_len);
        p += space_len;

        void disassemble(char *str, int size, uint64_t pc, uint8_t *code,
                         int nbyte);
        disassemble(p, s->logbuf + sizeof(s->logbuf) - p,
                    MUXDEF(CONFIG_ISA_x86, s->snpc, s->pc),
                    (uint8_t *)&s->isa.inst, ilen);
#endif
}

static void execute(uint64_t n)
{
        Decode s;
        for (; n > 0; n--) {
                exec_once(&s, cpu.pc);
                g_nr_guest_inst++;
                trace_and_difftest(&s, cpu.pc);
                if (nemu_state.state != NEMU_RUNNING) {
                        if (nemu_state.state == NEMU_STOP)
                                printf("Touched watchpoint\n");
                        break;
                }
                IFDEF(CONFIG_DEVICE, device_update());
        }
}

static void statistic()
{
        IFNDEF(CONFIG_TARGET_AM, setlocale(LC_NUMERIC, ""));
#define NUMBERIC_FMT MUXDEF(CONFIG_TARGET_AM, "%", "%'") PRIu64
        Log("host time spent = " NUMBERIC_FMT " us", g_timer);
        Log("total guest instructions = " NUMBERIC_FMT, g_nr_guest_inst);
        if (g_timer > 0)
                Log("simulation frequency = " NUMBERIC_FMT " inst/s",
                    g_nr_guest_inst * 1000000 / g_timer);
        else
                Log("Finish running in less than 1 us and can not calculate "
                    "the simulation frequency");
}

void assert_fail_msg()
{
        IFDEF(CONFIG_ITRACE, iringbuf_show());
        isa_reg_display();
        statistic();
}

/* Simulate how the CPU works. */
void cpu_exec(uint64_t n)
{
        g_print_step = (n < MAX_INST_TO_PRINT);
        switch (nemu_state.state) {
        case NEMU_END:
        case NEMU_ABORT:
        case NEMU_QUIT:
                printf("Program execution has ended. To restart the program, "
                       "exit NEMU and run again.\n");
                return;
        default:
                nemu_state.state = NEMU_RUNNING;
        }

        uint64_t timer_start = get_time();

        execute(n);

        uint64_t timer_end = get_time();
        g_timer += timer_end - timer_start;

        switch (nemu_state.state) {
        case NEMU_RUNNING:
                nemu_state.state = NEMU_STOP;
                break;

        case NEMU_END:
        case NEMU_ABORT:
                Log("nemu: %s at pc = " FMT_WORD,
                    (nemu_state.state == NEMU_ABORT
                             ? ANSI_FMT("ABORT", ANSI_FG_RED)
                             : (nemu_state.halt_ret == 0
                                        ? ANSI_FMT("HIT GOOD TRAP",
                                                   ANSI_FG_GREEN)
                                        : ANSI_FMT("HIT BAD TRAP",
                                                   ANSI_FG_RED))),
                    nemu_state.halt_pc);
                // fall through
        case NEMU_QUIT:
                statistic();
        }
}

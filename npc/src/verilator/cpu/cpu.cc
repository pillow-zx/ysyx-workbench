#include <array>
#include <stdexcept>

#include "VCore.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

#include <cpu.hh>

class Cpu::Impl {
public:
    Impl(const int argc, char *argv[]) : top(&context) {
        context.commandArgs(argc, argv);
        Verilated::traceEverOn(true);
        top.trace(&trace, 99);
        trace.open("build/npc.vcd");
    }

    ~Impl() {
        top.final();
        trace.close();
    }

    auto execOnce() -> void {
        top.clock = 0;
        top.eval();
        trace.dump(context.time());
        context.timeInc(1);

        top.clock = 1;
        top.eval();
        trace.dump(context.time());
        context.timeInc(1);
    }

    VerilatedContext context;
    VCore top;
    VerilatedVcdC trace;
};

Cpu::Cpu(const int argc, char *argv[]) : impl(std::make_unique<Impl>(argc, argv)) {}

Cpu::~Cpu() = default;

auto Cpu::reset() const -> void {
    impl->top.reset = 1;
    impl->execOnce();
    impl->execOnce();

    impl->top.reset = 0;
    impl->execOnce();
}

auto Cpu::exec(const std::optional<std::size_t> cycles) const -> bool {
    std::size_t executed = 0;
    while (!cycles.has_value() || executed < *cycles) {
        impl->execOnce();
        ++executed;
        if (impl->top.io_commit_isEbreak) {
            return true;
        }
    }
    return false;
}

auto Cpu::getReg(const std::size_t index) const -> std::uint32_t {
    const std::array<std::uint32_t, 32> registers = {
        impl->top.io_debug_gpr_0,  impl->top.io_debug_gpr_1,  impl->top.io_debug_gpr_2,
        impl->top.io_debug_gpr_3,  impl->top.io_debug_gpr_4,  impl->top.io_debug_gpr_5,
        impl->top.io_debug_gpr_6,  impl->top.io_debug_gpr_7,  impl->top.io_debug_gpr_8,
        impl->top.io_debug_gpr_9,  impl->top.io_debug_gpr_10, impl->top.io_debug_gpr_11,
        impl->top.io_debug_gpr_12, impl->top.io_debug_gpr_13, impl->top.io_debug_gpr_14,
        impl->top.io_debug_gpr_15, impl->top.io_debug_gpr_16, impl->top.io_debug_gpr_17,
        impl->top.io_debug_gpr_18, impl->top.io_debug_gpr_19, impl->top.io_debug_gpr_20,
        impl->top.io_debug_gpr_21, impl->top.io_debug_gpr_22, impl->top.io_debug_gpr_23,
        impl->top.io_debug_gpr_24, impl->top.io_debug_gpr_25, impl->top.io_debug_gpr_26,
        impl->top.io_debug_gpr_27, impl->top.io_debug_gpr_28, impl->top.io_debug_gpr_29,
        impl->top.io_debug_gpr_30, impl->top.io_debug_gpr_31,
    };

    if (index >= registers.size()) {
        throw std::out_of_range("register index out of range");
    }
    return registers[index];
}

auto Cpu::getPc() const -> std::uint32_t {
    return impl->top.io_debug_pc;
}

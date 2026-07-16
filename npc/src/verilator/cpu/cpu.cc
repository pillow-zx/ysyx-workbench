#include "VCore.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

#include <algorithm>
#include <cstring>
#include <elf.h>
#include <fstream>
#include <iterator>
#include <optional>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>
#include <iomanip>
#include <iostream>

#include <cpu.hh>
#include <disassembler.hh>

template<typename T>
[[nodiscard]] static auto readElfObject(
    const std::vector<char> &data,
    const std::size_t offset
) -> T {
    if (offset > data.size() || sizeof(T) > data.size() - offset) {
        throw std::runtime_error("ELF object is outside file bounds");
    }

    T object{};
    std::memcpy(&object, data.data() + offset, sizeof(T));
    return object;
}

class Cpu::Impl {
public:
    struct Commit {
        bool valid;
        bool trap;
        bool isEbreak;
        std::uint32_t pc;
        std::uint32_t nextPc;
        std::uint32_t inst;
    };

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

    auto execOnce() -> Commit {
        top.clock = 0;
        top.eval();
        trace.dump(context.time());
        context.timeInc(1);

        const Commit commit{
            static_cast<bool>(top.io_commit_valid),
            static_cast<bool>(top.io_commit_trap),
            static_cast<bool>(top.io_commit_isEbreak),
            top.io_commit_pc,
            top.io_commit_nextPc,
            top.io_commit_inst,
        };

        top.clock = 1;
        top.eval();
        trace.dump(context.time());
        context.timeInc(1);

        return commit;
    }

    VerilatedContext context;
    VCore top;
    VerilatedVcdC trace;
};

class Cpu::Trace {
public:
    explicit Trace(const std::optional<std::string> &itracePath, const std::optional<std::string> &elfPath) {
        if constexpr (config::trace::itrace) {
            initializeItrace(itracePath);
        }
        if constexpr (config::trace::ftrace) {
            initializeFtrace(elfPath);
        }
    }

    ~Trace() = default;

    Trace(const Trace &) = delete;

    Trace &operator=(const Trace &) = delete;

    Trace(Trace &&) = delete;

    Trace &operator=(Trace &&) = delete;

    auto itrace(const std::uint32_t pc, const std::uint32_t inst) -> void {
        if (!itraceFile_.is_open()) {
            return;
        }

        const size_t len = ((inst & 0x3) == 0x3) ? 4 : 2;
        const std::string disassemble = disassembler_.disassemble(pc, reinterpret_cast<const uint8_t *>(&inst), len);
        itraceFile_ << std::hex << std::setw(8) << std::setfill('0') << pc << ": " << std::setw(8) << inst << "    " <<
                disassemble << '\n';
    }

    auto ftrace(const std::uint32_t pc, const std::uint32_t nextPc, const std::uint32_t inst) -> void {
        if (isRetCall(inst)) {
            if (callStack_.empty()) {
                std::cout << "Warning: unmatched return at 0x" << std::hex << pc << '\n';
                return;
            }
            const CallFrame frame = std::move(callStack_.back());
            callStack_.pop_back();

            std::cout << std::string(callStack_.size() * 2, ' ') << "return: " << frame.functionName << " at 0x" <<
                    std::hex << pc << '\n';
            return;
        }

        if (!isCallInst(inst)) {
            return;
        }

        const FunctionSymbol *symbol = findFunction(nextPc);
        std::string functionName = symbol == nullptr ? "<unknown>" : symbol->name;

        std::cout << std::string(callStack_.size() * 2, ' ')
                << "call: " << functionName
                << " at 0x" << std::hex << nextPc << '\n';

        callStack_.push_back({pc, nextPc, std::move(functionName)});
    }

private:
    // itrace
    std::ofstream itraceFile_;
    Disassembler disassembler_;

    auto initializeItrace(const std::optional<std::string> &itracePath) -> void {
        if (!itracePath.has_value()) {
            return;
        }
        itraceFile_.open(itracePath.value());
        if (not itraceFile_.is_open()) {
            throw std::runtime_error("Failed to initialize Itrace, cannot open the file path");
        }
        std::cout << "Itrace initialize success." << std::endl;
    }

    // ftrace
    struct FunctionSymbol {
        std::uint64_t begin;
        std::uint64_t end;
        std::string name;
    };

    struct CallFrame {
        std::uint32_t callPc;
        std::uint32_t targetPc;
        std::string functionName;
    };

    std::vector<FunctionSymbol> functionSymbols_;
    std::vector<CallFrame> callStack_;

    [[nodiscard]] static auto opcode(const std::uint32_t inst) -> std::uint32_t {
        return inst & 0x7f;
    }

    [[nodiscard]] static auto rd(const std::uint32_t inst) -> std::uint32_t {
        return inst >> 7 & 0x1f;
    }

    [[nodiscard]] static auto funct3(const std::uint32_t inst) -> std::uint32_t {
        return inst >> 12 & 0x7;
    }

    [[nodiscard]] static auto rs1(const std::uint32_t inst) -> std::uint32_t {
        return inst >> 15 & 0x1f;
    }

    [[nodiscard]] static auto immI(const std::uint32_t inst) -> std::int32_t {
        return static_cast<std::int32_t>(inst) >> 20;
    }

    [[nodiscard]] static auto isCallInst(const std::uint32_t inst) -> bool {
        const auto instOpcode = opcode(inst);
        const auto dest = rd(inst);
        const bool writesLinkRegister = dest == 1 || dest == 5;

        if (instOpcode == 0x6f) {
            return writesLinkRegister;
        }

        if (instOpcode == 0x67) {
            return funct3(inst) == 0 && writesLinkRegister;
        }

        return false;
    }

    [[nodiscard]] static auto isRetCall(const std::uint32_t inst) -> bool {
        return opcode(inst) == 0x67 && funct3(inst) == 0 && rd(inst) == 0 && (rs1(inst) == 1 || rs1(inst) == 5) &&
               immI(inst) == 0;
    }

    [[nodiscard]] auto findFunction(const std::uint32_t nextPc) -> const FunctionSymbol * {
        const auto iterator = std::upper_bound(
            functionSymbols_.begin(),
            functionSymbols_.end(),
            nextPc,
            [](const std::uint32_t addr, const FunctionSymbol &symbol) {
                return addr < symbol.begin;
            }
        );

        if (iterator == functionSymbols_.begin()) {
            return nullptr;
        }

        const FunctionSymbol &symbol = *std::prev(iterator);

        if (nextPc < symbol.begin || nextPc >= symbol.end) {
            return nullptr;
        }

        return &symbol;
    }

    [[nodiscard]] static auto validRange(
        const std::size_t fileSize,
        const std::uint64_t offset,
        const std::uint64_t size
    ) -> bool {
        return offset <= fileSize && size <= fileSize - offset;
    }

    [[nodiscard]] static auto readElfString(
        const std::vector<char> &data,
        const Elf32_Shdr &stringTable,
        const std::uint32_t stringOffset
    ) -> std::string {
        if (stringOffset >= stringTable.sh_size) {
            throw std::runtime_error("ELF symbol name is outside string table");
        }

        const std::uint64_t beginOffset =
                static_cast<std::uint64_t>(stringTable.sh_offset) + stringOffset;

        const std::uint64_t remaining =
                static_cast<std::uint64_t>(stringTable.sh_size) - stringOffset;

        if (!validRange(data.size(), beginOffset, remaining)) {
            throw std::runtime_error("ELF string table is outside file bounds");
        }

        const char *begin = data.data() + beginOffset;
        const void *terminator = std::memchr(begin, '\0', remaining);

        if (terminator == nullptr) {
            throw std::runtime_error("ELF symbol name is not null-terminated");
        }

        const auto *end = static_cast<const char *>(terminator);
        return std::string(begin, end);
    }

    auto normalizeFunctionSymbols() -> void {
        std::sort(
            functionSymbols_.begin(),
            functionSymbols_.end(),
            [](const FunctionSymbol &lhs, const FunctionSymbol &rhs) {
                if (lhs.begin != rhs.begin) {
                    return lhs.begin < rhs.begin;
                }

                return lhs.end > rhs.end;
            }
        );

        for (
            std::size_t index = 0;
            index < functionSymbols_.size();
            ++index
        ) {
            FunctionSymbol &symbol = functionSymbols_[index];

            if (symbol.end > symbol.begin) {
                continue;
            }

            auto nextIndex = index + 1;

            while (
                nextIndex < functionSymbols_.size() &&
                functionSymbols_[nextIndex].begin == symbol.begin
            ) {
                ++nextIndex;
            }

            if (nextIndex < functionSymbols_.size()) {
                symbol.end = functionSymbols_[nextIndex].begin;
            } else {
                symbol.end = symbol.begin + 1;
            }
        }
    }

    auto initializeFtrace(const std::optional<std::string> &elfPath) -> void {
        if (!elfPath.has_value()) {
            throw std::invalid_argument("Ftrace is enabled, but no ELF file was specified with --elf");
        }

        std::ifstream elfFile(*elfPath, std::ios::binary | std::ios::ate);

        if (!elfFile.is_open()) {
            throw std::runtime_error("Failed to initialize ftrace: cannot open " + *elfPath);
        }

        const std::streampos fileEnd = elfFile.tellg();

        if (fileEnd < 0) {
            throw std::runtime_error("Failed to determine ELF file size: " + *elfPath);
        }

        std::vector<char> elfData(fileEnd);

        elfFile.seekg(0, std::ios::beg);

        if (!elfData.empty() && !elfFile.
            read(elfData.data(), static_cast<std::streamsize>(elfData.size()))) {
            throw std::runtime_error("Failed to read ELF file: " + *elfPath);
        }

        const Elf32_Ehdr elfHeader = readElfObject<Elf32_Ehdr>(elfData, 0);

        if (elfHeader.e_ident[EI_MAG0] != ELFMAG0 || elfHeader.e_ident[EI_MAG1] != ELFMAG1 || elfHeader.e_ident[EI_MAG2]
            != ELFMAG2 || elfHeader.e_ident[EI_MAG3] != ELFMAG3) {
            throw std::runtime_error("Invalid ELF maigc: " + *elfPath);
        }

        if (elfHeader.e_ident[EI_CLASS] != ELFCLASS32) {
            throw std::runtime_error("Ftrace requires a 32-bit ELF file");
        }

        if (elfHeader.e_ident[EI_DATA] != ELFDATA2LSB) {
            throw std::runtime_error("Ftrace requires a little-endian ELF file");
        }

        if (elfHeader.e_machine != EM_RISCV) {
            throw std::runtime_error("Ftrace requires a RISC-V ELF file");
        }

        if (elfHeader.e_shentsize < sizeof(Elf32_Shdr)) {
            throw std::runtime_error("Invalid ELF section header size");
        }

        if (const std::uint64_t sectionTableSize = static_cast<std::uint64_t>(elfHeader.e_shnum) * elfHeader.e_shentsize
            ; !validRange(elfData.size(), elfHeader.e_shoff, sectionTableSize)) {
            throw std::runtime_error("ELF section header table is outside file bounds");
        }

        const auto readSection = [&](const std::size_t index) {
            if (index >= elfHeader.e_shnum) {
                throw std::runtime_error("Invalid ELF section index");
            }

            const std::uint64_t offset =
                    static_cast<std::uint64_t>(elfHeader.e_shoff) +
                    index * elfHeader.e_shentsize;

            return readElfObject<Elf32_Shdr>(
                elfData,
                offset
            );
        };

        functionSymbols_.clear();
        callStack_.clear();

        for (std::size_t sectionIndex = 0; sectionIndex < elfHeader.e_shnum; ++sectionIndex) {
            const Elf32_Shdr symbolTable = readSection(sectionIndex);

            if (symbolTable.sh_type != SHT_SYMTAB) {
                continue;
            }

            if (
                symbolTable.sh_entsize < sizeof(Elf32_Sym) ||
                symbolTable.sh_entsize == 0
            ) {
                throw std::runtime_error("Invalid ELF symbol table entry size");
            }

            if (symbolTable.sh_link >= elfHeader.e_shnum) {
                throw std::runtime_error(
                    "ELF symbol table has invalid string table index"
                );
            }

            const Elf32_Shdr stringTable = readSection(symbolTable.sh_link);

            if (stringTable.sh_type != SHT_STRTAB) {
                throw std::runtime_error(
                    "ELF symbol table does not reference a string table"
                );
            }

            if (
                !validRange(
                    elfData.size(),
                    symbolTable.sh_offset,
                    symbolTable.sh_size
                ) ||
                !validRange(
                    elfData.size(),
                    stringTable.sh_offset,
                    stringTable.sh_size
                )
            ) {
                throw std::runtime_error(
                    "ELF symbol or string table is outside file bounds"
                );
            }

            const std::size_t symbolCount = symbolTable.sh_size / symbolTable.sh_entsize;


            for (
                std::size_t symbolIndex = 0;
                symbolIndex < symbolCount;
                ++symbolIndex
            ) {
                const std::uint64_t symbolOffset =
                        static_cast<std::uint64_t>(symbolTable.sh_offset) +
                        symbolIndex * symbolTable.sh_entsize;

                const Elf32_Sym symbol = readElfObject<Elf32_Sym>(
                    elfData,
                    symbolOffset
                );

                if (ELF32_ST_TYPE(symbol.st_info) != STT_FUNC) {
                    continue;
                }

                if (
                    symbol.st_shndx == SHN_UNDEF ||
                    symbol.st_name == 0
                ) {
                    continue;
                }

                const std::string name =
                        readElfString(elfData, stringTable, symbol.st_name);

                if (name.empty()) {
                    continue;
                }

                const std::uint64_t begin = symbol.st_value;
                const std::uint64_t end =
                        begin + static_cast<std::uint64_t>(symbol.st_size);

                functionSymbols_.push_back({
                    begin,
                    end,
                    name,
                });
            }

            break;
        }

        if (functionSymbols_.empty()) {
            throw std::runtime_error(
                "No function symbols found in ELF file: " + *elfPath
            );
        }

        normalizeFunctionSymbols();

        std::cout
                << "Ftrace initialized: "
                << functionSymbols_.size()
                << " function symbols loaded from "
                << *elfPath
                << '\n';
    }
};

Cpu::Cpu(const int argc, char *argv[], const std::optional<std::string> &itracePath,
         const std::optional<std::string> &elfPath)
    : impl(std::make_unique<Impl>(argc, argv)), trace(std::make_unique<Trace>(itracePath, elfPath)) {
}

Cpu::~Cpu() = default;

auto Cpu::reset() const -> void {
    impl->top.reset = 1;
    impl->execOnce();
    impl->execOnce();

    impl->top.reset = 0;
}

auto Cpu::exec(const std::optional<std::size_t> cycles) const -> bool {
    std::size_t executed = 0;
    while (!cycles.has_value() || executed < *cycles) {
        const auto [valid, trap, isEbreak, pc, nextPc, inst] = impl->execOnce();
        ++executed;
        if (valid) {
            if constexpr (config::trace::itrace) {
                trace->itrace(pc, inst);
            }
            if constexpr (config::trace::ftrace) {
                if (!trap) {
                    trace->ftrace(pc, nextPc, inst);
                }
            }
        }
        if (isEbreak) {
            return true;
        }
    }
    return false;
}

auto Cpu::getReg(const std::size_t index) const -> std::uint32_t {
    const std::array<std::uint32_t, 32> registers = {
        impl->top.io_debug_gpr_0, impl->top.io_debug_gpr_1, impl->top.io_debug_gpr_2,
        impl->top.io_debug_gpr_3, impl->top.io_debug_gpr_4, impl->top.io_debug_gpr_5,
        impl->top.io_debug_gpr_6, impl->top.io_debug_gpr_7, impl->top.io_debug_gpr_8,
        impl->top.io_debug_gpr_9, impl->top.io_debug_gpr_10, impl->top.io_debug_gpr_11,
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

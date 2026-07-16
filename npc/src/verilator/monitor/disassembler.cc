#include <disassembler.hh>

#include <stdexcept>

Disassembler::Disassembler() {
    if (const cs_err err = cs_open(CS_ARCH_RISCV, static_cast<cs_mode>(CS_MODE_RISCV32 | CS_MODE_RISCVC), &handle_);
        err != CS_ERR_OK) {
        throw std::runtime_error("Failed to initialize capstone");
    }
}

Disassembler::~Disassembler() {
    cs_close(&handle_);
}

auto Disassembler::disassemble(const std::uint64_t pc, const std::uint8_t *code,
                               const std::size_t len) const -> std::string {
    cs_insn *insn = nullptr;

    const std::size_t count = cs_disasm(handle_, code, len, pc, 0, &insn);
    if (count != 1) {
        return std::string{"<invalid>"};
    }

    std::string result = insn[0].mnemonic;
    if (insn[0].op_str[0] != '\0') {
        result += '\t';
        result += insn[0].op_str;
    }

    cs_free(insn, count);
    return result;
}

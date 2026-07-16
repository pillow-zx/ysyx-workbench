#ifndef NPC_DISASSEMBLER_HH
#define NPC_DISASSEMBLER_HH

#include <capstone/capstone.h>

#include <cstdint>
#include <string>

class Disassembler {
public:
    Disassembler();
    ~Disassembler();

    Disassembler(const Disassembler&) = delete;
    Disassembler& operator=(const Disassembler&) = delete;

    [[nodiscard]] auto disassemble(std::uint64_t pc, const std::uint8_t *code, std::size_t len) const -> std::string;

private:
    csh handle_ = {};
};

#endif //NPC_TRACE_HH

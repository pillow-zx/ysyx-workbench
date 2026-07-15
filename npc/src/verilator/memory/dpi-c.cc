#include "VCore__Dpi.h"

#include <mm.hh>

extern "C" auto fetchInst(const int addr, int *inst) -> void {
    *inst = static_cast<int>(Memory::fetchInst(static_cast<std::uint32_t>(addr)));
}

extern "C" auto readData(const int addr, int *data) -> void {
    *data = static_cast<int>(Memory::readData(static_cast<std::uint32_t>(addr), sizeof(std::uint32_t)));
}

extern "C" auto writeData(const int addr, const int data, const char wmask) -> void {
    Memory::writeData(static_cast<std::uint32_t>(addr), static_cast<std::uint32_t>(data),
                      static_cast<std::uint8_t>(wmask));
}
